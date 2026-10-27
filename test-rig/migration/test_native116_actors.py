import unittest
from pathlib import Path
import tempfile
from unittest.mock import Mock, patch
import json

import dimensions
from native116_actors import baseline, expected_state, native_dimension_state, release
from native116_audit_actors import inherited_sources, persistence_controls, replay


class NativeActorOracleTest(unittest.TestCase):
    def test_frozen_comparison_retains_positions_and_nested_items(self):
        old = {"file": {"Inventory": [{"Count": 17, "tag": {"nested": {"keep": 3}}}]},
               "entities": {"cart": {"Pos": [1, 2, 3], "Items": [{"Count": 4}]}}}
        result = expected_state(old, "actors")
        self.assertEqual(old, result)
        result["entities"]["cart"]["Items"][0]["Count"] = 0
        self.assertEqual(4, old["entities"]["cart"]["Items"][0]["Count"])

    def test_survival_only_removes_historical_movement_keys(self):
        old = {"file": {}, "entities": {"wolf": {"Pos": [1, 2, 3], "Motion": [0, 0, 0],
               "Rotation": [0, 0], "OwnerUUID": "owner", "ForgeData": {"keep": 8}}}}
        self.assertEqual({"OwnerUUID": "owner", "ForgeData": {"keep": 8}},
                         expected_state(old, "actors", survival=True)["entities"]["wolf"])

    def test_dimension_oracle_keeps_live_relationships_and_quantities(self):
        old = {"file": {"Inventory": [{"id": "steamadvantage:musket", "Count": 3}]},
               "entities": {"horse": {"Pos": [1, 2, 3], "Health": 20}},
               "relationships": {"horse": {"passengerUuids": ["wolf"]}}, "adapter": {"Dimension": -1}}
        result = expected_state(old, "dimensions", survival=True)
        self.assertEqual(old["relationships"], result["relationships"])
        self.assertEqual(old["file"], result["file"])

    def test_native_leash_byte_is_derived_only_from_actual_saved_compound(self):
        state = {"entities": {"horse": {"id": "minecraft:horse", "Leash": {"X": 1, "Y": 2, "Z": 3}},
                              "wolf": {"id": "minecraft:wolf"}}, "relationships": {"horse": {"leashHolderType": "minecraft:leash_knot"}}}
        result = native_dimension_state(state)
        self.assertEqual(1, result["entities"]["horse"]["Leashed"])
        self.assertEqual(0, result["entities"]["wolf"]["Leashed"])
        self.assertNotIn("Leashed", state["entities"]["horse"])
        del state["entities"]["horse"]["Leash"]
        self.assertEqual(0, native_dimension_state(state)["entities"]["horse"]["Leashed"])

    def test_native_adapter_does_not_hide_vanilla_wolf_health_changes(self):
        old = {"entities": {"wolf": {"id": "minecraft:wolf", "Health": 8.0}},
               "relationships": {}}
        actual = {"entities": {"wolf": {"id": "minecraft:wolf", "Health": 20.0}},
                  "relationships": {}}
        expected = expected_state(old, "dimensions", survival=True)
        result = dimensions.verdict(expected, native_dimension_state(actual))
        self.assertEqual("FAIL", result["status"])
        self.assertIn({"path": ".entities.wolf.Health", "expected": 8.0, "actual": 20.0},
                      result["differences"])
        self.assertEqual(8.0, old["entities"]["wolf"]["Health"])

    def test_dimension_release_never_saves_before_native_initialization(self):
        server = Mock()
        release(server, Path("unused"), "dimensions", "released")
        server.send.assert_called_once_with("advdimensions operate released")
        server.wait.assert_called_once_with("ADVDIMENSIONS COMPLETE released", 120)

    def test_actor_release_requests_real_tickets_not_synthetic_ticks(self):
        server = Mock()
        with patch("native116_actors.command", return_value="captured") as command:
            self.assertEqual("captured", release(server, Path("unused"), "actors", "released"))
            self.assertEqual("forceload add 96 -32 223 47", server.send.call_args_list[0].args[0])
            command.assert_called_once_with(server, Path("unused"), "actors", "operate", "released")

    def test_retained_baseline_selection_includes_player_layout_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for selected in ("source", "restarted", "runtime-restart"):
                (root / ("semantic-" + selected + ".json")).write_text("{}")
                with patch("native116_actors.capture_actors", return_value={"retain": True}) as capture:
                    self.assertEqual(({"retain": True}, selected), baseline(root, "actors"))
                    capture.assert_called_once_with(root, selected)

    def test_inherited_proof_reuses_old_fingerprint_and_rejects_new_data(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            proof = root / "old-proof.json"
            source = root / "original-source"
            expected = {"level.dat": "old-approved-hash"}
            proof.write_text(json.dumps({"source": str(source), "sourceFingerprint": expected}))
            with patch("native116_audit_actors.fingerprint", return_value=expected):
                result = inherited_sources([proof])
                self.assertEqual(expected, result["receipts"][0]["sources"][0]["fingerprint"])
            with patch("native116_audit_actors.fingerprint", return_value={"level.dat": "fresh-but-not-approved"}):
                with self.assertRaisesRegex(AssertionError, "inherited proof"):
                    inherited_sources([proof])

    def test_aborted_route_reports_failure_without_reconstructing_unsaved_state(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            runtime = root / "broken"
            runtime.mkdir()
            source = root / "protected-source"
            manifest = root / "archived-inputs.json"
            manifest.write_text("{}")
            error = {"status": "FAIL", "error": "Unclean shutdown"}
            (root / "results.json").write_text(json.dumps({"broken": {"campaign-error": error}}))
            (runtime / "native116-source-provenance.json").write_text(json.dumps({
                "source": str(source), "sourceFingerprint": {"approved": "hash"},
                "targetManifest": str(manifest), "targetManifestSha256": "manifest-hash"}))
            (runtime / "input-jars.json").write_text("[]")
            (runtime / "console-conversion.log").write_text("[Server thread/ERROR] Exception stopping the server")
            with patch("native116_audit_actors.fingerprint", return_value={"approved": "hash"}), \
                 patch("native116_audit_actors.digest", return_value="manifest-hash"), \
                 patch("native116_audit_actors.pinned_profile"), \
                 patch("native116_audit_actors.dimensions.capture") as capture:
                result = replay(root, "dimensions")
            capture.assert_not_called()
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(error, result["routes"]["broken"]["recordedCampaignError"])
            self.assertEqual(2, len(result["findings"]))

    def test_persistence_controls_require_every_bounded_save_receipt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for kind, counts in (("actors", (2, 3, 2)), ("dimensions", (2, 2, 1))):
                for label, count in zip(("conversion", "restart", "runtime-restart"), counts):
                    (root / ("console-" + label + ".log")).write_text(
                        "Done (1.0s)!\n" + "Saved the game\n" * count + "Stopping server\n")
                result = persistence_controls(root, kind)
                self.assertEqual("PASS", result["status"])
                self.assertEqual(45, result["liveRunnerBudgetsSeconds"]["saveReceipt"])
                self.assertIn("not inferred", result["nativeHookListInspection"])
            (root / "console-conversion.log").write_text("Done (1.0s)!\nSaved the game\nStopping server\n")
            self.assertEqual("FAIL", persistence_controls(root, "dimensions")["status"])

    def test_persistence_controls_reject_load_save_npe_even_with_receipts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for label, count in zip(("conversion", "restart", "runtime-restart"), (2, 2, 1)):
                (root / ("console-" + label + ".log")).write_text(
                    "Done (1.0s)!\n" + "Saved the game\n" * count + "Stopping server\n")
            with (root / "console-conversion.log").open("a") as stream:
                stream.write("WorldPersistenceHooks.lambda$handleWorldDataLoad$1\nNullPointerException\n"
                             "WorldPersistenceHooks.lambda$handleWorldDataSave$0\n")
            result = persistence_controls(root, "dimensions")
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(3, len(result["boots"]["conversion"]["errors"]))

    def test_persistence_controls_reject_missing_boots(self):
        with tempfile.TemporaryDirectory() as temp:
            result = persistence_controls(Path(temp), "dimensions")
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(3, len(result["boots"]))


if __name__ == "__main__":
    unittest.main()
