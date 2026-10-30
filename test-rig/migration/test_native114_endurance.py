"""Native transport guards; historical material/energy oracles stay inherited."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import nbtlib

import endurance
from matrix import WORLD, digest, write_json
from native114_endurance import (FRESH_WORLD_SEED, NativeEnduranceRig, audit,
                                 configure_fresh_world, prepare_endurance, verify_fresh_world)


class NativeEnduranceTests(unittest.TestCase):
    def native_world(self, runtime, **changes):
        values = {"MapFeatures": nbtlib.Byte(0), "RandomSeed": nbtlib.Long(FRESH_WORLD_SEED),
                  "generatorName": nbtlib.String("flat"), "DataVersion": nbtlib.Int(1976)}
        values.update(changes)
        folder = runtime / WORLD
        folder.mkdir()
        path = folder / "level.dat"
        nbtlib.File({"Data": nbtlib.Compound(values)}, gzipped=True).save(path)
        return path

    def test_fresh_setup_disables_structures_and_sets_one_fixed_seed(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            properties = runtime / "server.properties"
            properties.write_text("level-name=AdvantageWorksMigration\nserver-port=25590\ngenerate-structures=true\nlevel-seed=\nlevel-seed=old\n")
            configure_fresh_world(runtime)
            lines = properties.read_text().splitlines()
            values = dict(line.split("=", 1) for line in lines)
            self.assertEqual("false", values["generate-structures"])
            self.assertEqual(str(FRESH_WORLD_SEED), values["level-seed"])
            self.assertEqual("FLAT", values["level-type"])
            self.assertEqual("", values["generator-settings"])
            self.assertEqual("25590", values["server-port"])
            self.assertEqual("AdvantageWorksMigration", values["level-name"])
            self.assertEqual(1, sum(line.startswith("level-seed=") for line in lines))

    def test_existing_world_cannot_be_reconfigured(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            path = self.native_world(runtime, MapFeatures=nbtlib.Byte(1), RandomSeed=nbtlib.Long(7))
            properties = runtime / "server.properties"
            properties.write_text("generate-structures=true\nlevel-seed=7\n")
            before = digest(path), properties.read_bytes()
            with self.assertRaisesRegex(AssertionError, "existing endurance world"):
                configure_fresh_world(runtime)
            self.assertEqual(before, (digest(path), properties.read_bytes()))

    def test_prepare_configures_only_fresh_saves_and_leaves_copy_settings_alone(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            with patch("native114_endurance.prepare", return_value="native-launcher") as prepare, \
                    patch("native114_endurance.configure_fresh_world") as configure:
                self.assertEqual("native-launcher", prepare_endurance(runtime, "profile", [], 25590))
                prepare.assert_called_once_with(runtime, "profile", [], 25590, None)
                configure.assert_called_once_with(runtime)
            with patch("native114_endurance.prepare", return_value="native-launcher") as prepare, \
                    patch("native114_endurance.configure_fresh_world") as configure:
                source = runtime / "retained-source"
                self.assertEqual("native-launcher", prepare_endurance(runtime, "profile", [], 25591, source))
                prepare.assert_called_once_with(runtime, "profile", [], 25591, source)
                configure.assert_not_called()

    def test_saved_native_settings_are_checked_and_not_rewritten(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            path = self.native_world(runtime)
            before = digest(path)
            receipt = verify_fresh_world(runtime, "endurance")
            self.assertEqual("PASS", receipt["status"])
            self.assertEqual(before, digest(path))
            self.assertTrue((runtime / "native-world-settings-endurance.json").is_file())
            with self.assertRaisesRegex(AssertionError, "overwrite"):
                verify_fresh_world(runtime, "endurance")

    def test_wrong_native_settings_fail_and_preserve_failed_receipts(self):
        for key, value in (("MapFeatures", nbtlib.Byte(1)), ("RandomSeed", nbtlib.Long(7)),
                           ("generatorName", nbtlib.String("default")), ("DataVersion", nbtlib.Int(1631))):
            with self.subTest(key=key), tempfile.TemporaryDirectory() as directory:
                runtime = Path(directory)
                path = self.native_world(runtime, **{key: value})
                before = digest(path)
                with self.assertRaisesRegex(AssertionError, "world settings differ"):
                    verify_fresh_world(runtime, "endurance")
                receipt = __import__("json").loads((runtime / "native-world-settings-endurance.json").read_text())
                self.assertEqual("FAIL", receipt["status"])
                self.assertTrue(receipt["failures"])
                self.assertEqual(before, digest(path))

    def test_actual_settings_gate_runs_after_fresh_boot_and_before_rig_construction(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            rig = NativeEnduranceRig("fresh114", runtime, None, None, {})
            calls = []
            rig.command = lambda *args: calls.append(args)
            with patch.object(endurance.Rig, "boot", side_effect=lambda phase: calls.append(("boot", phase))), \
                    patch("native114_endurance.verify_code"), \
                    patch("native114_endurance.verify_fresh_world", side_effect=lambda *args: calls.append(("verify", *args))):
                rig.boot("endurance")
            self.assertEqual([("boot", "endurance"), ("save-all flush", "Saved the game"),
                              ("verify", runtime, "endurance")], calls)

    def test_batch_and_conservation_oracles_are_inherited(self):
        self.assertIsNot(NativeEnduranceRig.batch, endurance.Rig.batch)
        self.assertIs(NativeEnduranceRig.invariants, endurance.Rig.invariants)
        self.assertIs(NativeEnduranceRig.inventory, endurance.Rig.inventory)
        self.assertIs(NativeEnduranceRig.seed, endurance.Rig.seed)
        self.assertIs(NativeEnduranceRig.structure, endurance.Rig.structure)

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
            with patch.object(endurance.Rig, "boot"), patch("native114_endurance.verify_code"), patch("native114_endurance.time.sleep"):
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
            with patch.object(endurance.Rig, "boot"), patch("native114_endurance.verify_code"), patch("native114_endurance.time.sleep"):
                with self.assertRaisesRegex(TimeoutError, "No actual native tick"):
                    rig.boot("endurance-restart")

    def test_initial_boot_does_not_restore_or_rebuild_anything(self):
        rig = NativeEnduranceRig("test", Path("unused"), None, None, {})
        rig.command = lambda *args: self.fail("Initial boot issued a command")
        with patch.object(endurance.Rig, "boot"), patch("native114_endurance.verify_code"):
            rig.boot("endurance")

    def test_missing_code_seal_prevents_any_server_boot(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = NativeEnduranceRig("test", Path(directory), None, None, {})
            with patch.object(endurance.Rig, "boot") as boot, self.assertRaises(FileNotFoundError):
                rig.boot("endurance")
            boot.assert_not_called()

    def test_independent_failure_is_retained_and_blocks_the_historical_ledger(self):
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

    def test_smoke_result_never_qualifies_as_endurance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_json(root / "native114-endurance-results.json", dict(status="SMOKE_PASS_NOT_ENDURANCE"))
            with self.assertRaisesRegex(AssertionError, "No completed native endurance"):
                audit(root)

    def test_missing_native_profile_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_json(root / "native114-endurance-results.json", dict(
                status="PASS", duration_seconds=1800, interval_seconds=60,
                source_fixture_unchanged=True, helpers_unchanged=True, profiles={}))
            with self.assertRaisesRegex(AssertionError, "Missing native environment"):
                audit(root)


if __name__ == "__main__":
    unittest.main()
