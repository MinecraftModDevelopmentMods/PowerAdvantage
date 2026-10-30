"""Native transport guards; historical material/energy oracles stay inherited."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import nbtlib
import endurance
from matrix import WORLD, fingerprint, write_json
from native116_endurance import (FRESH_FIXTURE_SEED, NativeEnduranceRig, audit,
                                 configure_new_fresh_fixture, prove_fresh_fixture_before_build,
                                 verify_fresh_fixture_proof, world_info)


class NativeEnduranceTests(unittest.TestCase):
    def test_batch_and_conservation_oracles_are_inherited(self):
        self.assertIsNot(NativeEnduranceRig.batch, endurance.Rig.batch)
        self.assertIs(NativeEnduranceRig.invariants, endurance.Rig.invariants)
        self.assertIs(NativeEnduranceRig.inventory, endurance.Rig.inventory)
        self.assertIs(NativeEnduranceRig.seed, endurance.Rig.seed)
        self.assertIs(NativeEnduranceRig.structure, endurance.Rig.structure)

    def test_independent_failure_is_retained_and_cannot_reach_a_passing_ledger(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = NativeEnduranceRig("test", Path(directory), None, None, {})
            rig.documents = {station: {} for station in ("S-04", "E-03", "W-04", "W-03")}
            with patch("semantic_machine_audit.endurance_batch", return_value={"status": "FAIL", "failures": ["lost filter"]}), \
                    patch.object(endurance.Rig, "batch") as historical:
                with self.assertRaisesRegex(AssertionError, "Independent batch"):
                    rig.batch("cycle-001")
                historical.assert_not_called()
            self.assertEqual("FAIL", __import__("json").loads((Path(directory) / "semantic-batch-cycle-001.json").read_text())["status"])

    def test_independent_pass_keeps_the_historical_material_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = NativeEnduranceRig("test", Path(directory), None, None, {})
            rig.documents = {station: {} for station in ("S-04", "E-03", "W-04", "W-03")}
            with patch("semantic_machine_audit.endurance_batch", return_value={"status": "PASS", "failures": []}), \
                    patch.object(endurance.Rig, "batch", return_value={"retained": True}) as historical:
                self.assertEqual({"retained": True}, rig.batch("cycle-001"))
                historical.assert_called_once_with("cycle-001")

    def test_restart_selects_first_tick_without_output_or_inventory_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            definitions = {s: dict(origin=[0, 0, 0], boundsMin=[0, 0, 0], boundsMax=[31, 20, 31])
                           for s in endurance.CHECKED}
            rig = NativeEnduranceRig("test", Path(directory), None, None, definitions)
            ticks = iter([200, 200, 201])
            observations, commands = [], []
            def checkpoint(label):
                observations.append(label)
                rig.documents = {"W-01": {"worldTime": next(ticks)}}
            rig.checkpoint = checkpoint
            rig.command = lambda command, response=None: commands.append(command)
            with patch.object(endurance.Rig, "boot"), patch("native116_endurance.time.sleep"):
                rig.boot("endurance-restart")
            self.assertEqual(len(observations), 3)
            self.assertEqual(len(commands), len(endurance.CHECKED))
            self.assertTrue(all(c == "forceload add 0 0 31 31" for c in commands))
            self.assertTrue((Path(directory) / "native-restart-readiness.json").is_file())

    def test_restart_without_measured_tick_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            definitions = {s: dict(origin=[0, 0, 0], boundsMin=[0, 0, 0], boundsMax=[31, 20, 31])
                           for s in endurance.CHECKED}
            rig = NativeEnduranceRig("test", Path(directory), None, None, definitions)
            rig.command = lambda *args: None
            rig.checkpoint = lambda label: rig.documents.update({"W-01": {"worldTime": 200}})
            with patch.object(endurance.Rig, "boot"), patch("native116_endurance.time.sleep"):
                with self.assertRaisesRegex(TimeoutError, "No actual native tick"):
                    rig.boot("endurance-restart")

    def test_initial_boot_does_not_restore_or_rebuild_anything(self):
        rig = NativeEnduranceRig("test", Path("unused"), None, None, {})
        rig.command = lambda *args: self.fail("Initial boot issued a command")
        with patch.object(endurance.Rig, "boot"):
            rig.boot("endurance")

    def test_smoke_result_never_qualifies_as_endurance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_json(root / "native116-endurance-results.json", dict(status="SMOKE_PASS_NOT_ENDURANCE"))
            with self.assertRaisesRegex(AssertionError, "No completed native endurance"):
                audit(root)

    def test_missing_native_profile_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_json(root / "native116-endurance-results.json", dict(
                status="PASS", duration_seconds=1800, interval_seconds=60,
                source_fixture_unchanged=True, helpers_unchanged=True, profiles={}))
            with self.assertRaisesRegex(AssertionError, "Missing native environment"):
                audit(root)


class FreshFixtureIsolationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.runtime = Path(self.temporary.name)
        (self.runtime / "server.properties").write_text(
            "max-tick-time=60000\nserver-port=25590\nlevel-seed=old\n"
            "generate-structures=true\ngenerate-structures=true\n", encoding="utf-8")

    def level(self, features=0, seed=FRESH_FIXTURE_SEED):
        path = self.runtime / WORLD / "level.dat"
        path.parent.mkdir(exist_ok=True)
        dimensions = nbtlib.Compound({key: nbtlib.Compound({"type": nbtlib.String(key),
            "generator": nbtlib.Compound({"type": nbtlib.String("minecraft:flat"),
                                          "settings": nbtlib.Compound()})})
            for key in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")})
        nbtlib.File({"Data": nbtlib.Compound({"WorldGenSettings": nbtlib.Compound({
                    "seed": nbtlib.Long(seed), "generate_features": nbtlib.Byte(features),
                    "bonus_chest": nbtlib.Byte(0), "dimensions": dimensions})})},
                    gzipped=True).save(path)
        return path

    def rig(self, block="minecraft:air"):
        rig = NativeEnduranceRig("test", self.runtime, None, None, {})
        def snapshot(label):
            write_json(self.runtime / f"migration-{label}.json", [dict(
                id="poi-target", x=-126, y=5, z=27, actualName=block, properties={})])
        rig.server = type("FakeServer", (), {"snapshot": staticmethod(snapshot)})()
        rig.command = lambda command, response=None: self.assertEqual((command, response),
                                                                    ("save-all flush", "Saved the game"))
        return rig

    def test_new_world_only_changes_two_creation_settings_and_keeps_watchdog(self):
        proof = configure_new_fresh_fixture(self.runtime)
        text = (self.runtime / "server.properties").read_text()
        self.assertTrue(proof["existing_world_absent"])
        self.assertEqual(text.count("generate-structures="), 1)
        self.assertEqual(text.count("level-seed="), 1)
        self.assertIn("generate-structures=false\n", text)
        self.assertIn(f"level-seed={FRESH_FIXTURE_SEED}\n", text)
        self.assertIn("max-tick-time=60000\nserver-port=25590\n", text)

    def test_existing_imported_world_is_refused_before_any_write(self):
        self.level(features=1, seed=42)
        before = fingerprint(self.runtime)
        with self.assertRaisesRegex(AssertionError, "existing/imported world"):
            configure_new_fresh_fixture(self.runtime)
        self.assertEqual(fingerprint(self.runtime), before)
        self.assertEqual(world_info(self.runtime / WORLD / "level.dat")["MapFeatures"], 1)

    def test_actual_seed_mapfeatures_and_air_proof_pass_and_are_replayable(self):
        configuration = configure_new_fresh_fixture(self.runtime)
        self.level()
        proof = prove_fresh_fixture_before_build(self.rig(), configuration)
        self.assertEqual(proof["status"], "PASS")
        self.assertEqual(verify_fresh_fixture_proof(self.runtime, proof),
                         dict(MapFeatures=0, RandomSeed=FRESH_FIXTURE_SEED, generatorName="flat"))
        self.assertEqual(proof["critical_block"]["actualName"], "minecraft:air")

    def test_property_false_does_not_substitute_for_actual_worldinfo_zero(self):
        configuration = configure_new_fresh_fixture(self.runtime)
        self.level(features=1)
        with self.assertRaisesRegex(AssertionError, "MapFeatures is not zero"):
            prove_fresh_fixture_before_build(self.rig(), configuration)

    def test_a_different_seed_is_rejected_even_with_structures_disabled(self):
        configuration = configure_new_fresh_fixture(self.runtime)
        self.level(seed=123)
        with self.assertRaisesRegex(AssertionError, "seed changed"):
            prove_fresh_fixture_before_build(self.rig(), configuration)

    def test_composter_is_rejected_even_with_correct_seed_and_worldinfo(self):
        configuration = configure_new_fresh_fixture(self.runtime)
        self.level()
        with self.assertRaisesRegex(AssertionError, "not AIR"):
            prove_fresh_fixture_before_build(self.rig("minecraft:composter"), configuration)

    def test_missing_guard_and_tampered_raw_evidence_fail(self):
        configuration = configure_new_fresh_fixture(self.runtime)
        self.level()
        proof = prove_fresh_fixture_before_build(self.rig(), configuration)
        with self.assertRaisesRegex(AssertionError, "new-world isolation guard"):
            verify_fresh_fixture_proof(self.runtime, dict(proof, existing_world_absent=False))
        (self.runtime / "migration-fresh-isolation-before-build.json").write_text("[]")
        with self.assertRaisesRegex(AssertionError, "evidence changed"):
            verify_fresh_fixture_proof(self.runtime, proof)

    def test_no_new_run_can_qualify_without_fresh_isolation_proof(self):
        write_json(self.runtime / "native116-endurance-results.json", dict(
            status="PASS", duration_seconds=1800, interval_seconds=60,
            source_fixture_unchanged=True, helpers_unchanged=True,
            profiles={"fresh116": {}, "original110-chained113-native116": {}}))
        with self.assertRaisesRegex(AssertionError, "Missing fresh isolation proof"):
            audit(self.runtime)


if __name__ == "__main__":
    unittest.main()
