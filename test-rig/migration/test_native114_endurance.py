"""Native transport guards; historical material/energy oracles stay inherited."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import endurance
from matrix import write_json
from native114_endurance import NativeEnduranceRig, audit


class NativeEnduranceTests(unittest.TestCase):
    def test_batch_and_conservation_oracles_are_inherited(self):
        self.assertIs(NativeEnduranceRig.batch, endurance.Rig.batch)
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
            with patch.object(endurance.Rig, "boot"), patch("native114_endurance.time.sleep"):
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
            with patch.object(endurance.Rig, "boot"), patch("native114_endurance.time.sleep"):
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
