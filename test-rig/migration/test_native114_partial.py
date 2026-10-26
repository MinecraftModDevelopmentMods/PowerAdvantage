"""Native transport controls do not change historical fixture/oracle semantics."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import extended_lifecycle
from native114_partial import (NativeExtendedRun, NativePartialRun, selected_chunks,
                               subset_load_issues, subset_unload_pending)
from native114_lifecycle import NativeLifecycleRun


def snapshot(tick, loaded=True, loads=1, unloads=0, events=None):
    return dict(worldTime=tick, chunks=[dict(pos=[1, 2], loaded=loaded, loads=loads, unloads=unloads)],
                chunkEvents=events if events is not None else [dict(pos=[1, 2], operation="load")],
                cases={"case": [dict(pos=[16, 80, 32], loaded=loaded)]})


class NativePartialTest(unittest.TestCase):
    def test_extended_keeps_historical_operations(self):
        self.assertIs(NativeExtendedRun.build, extended_lifecycle.ExtendedRun.build)
        self.assertIs(NativeExtendedRun.partial, extended_lifecycle.ExtendedRun.partial)
        self.assertIs(NativeExtendedRun.wait_step, extended_lifecycle.ExtendedRun.wait_step)
        self.assertIs(NativeExtendedRun.save_read_guards, NativeLifecycleRun.save_read_guards)

    def test_group_selection_does_not_acquire_unrequested_group(self):
        definition = [dict(groups=dict(machine=[[1, 2]], supply=[[2, 2]]))]
        self.assertEqual(selected_chunks(definition, "machine"), {(1, 2)})

    def test_load_requires_actual_event_not_loaded_flag_alone(self):
        self.assertTrue(subset_load_issues(snapshot(1, False), snapshot(2, events=[]), {(1, 2)}))

    def test_load_requires_increment_after_absence(self):
        self.assertTrue(subset_load_issues(snapshot(1, False), snapshot(2), {(1, 2)}))
        self.assertFalse(subset_load_issues(snapshot(1, False), snapshot(2, loads=2), {(1, 2)}))

    def test_load_rejects_unloaded_witness(self):
        current = snapshot(2, loads=2)
        current["cases"]["case"][0]["loaded"] = False
        self.assertTrue(subset_load_issues(snapshot(1, False), current, {(1, 2)}))

    def test_unload_requires_counter_and_callback(self):
        before = snapshot(1)
        current = snapshot(2, loaded=False, unloads=1)
        self.assertEqual(subset_unload_pending(before, current, {(1, 2)}), (True, ["(1, 2)"]))
        current["chunkEvents"].append(dict(pos=[1, 2], operation="unload"))
        self.assertEqual(subset_unload_pending(before, current, {(1, 2)}), (True, []))

    def test_unload_does_not_accept_callback_without_absence(self):
        current = snapshot(2, unloads=1, events=[dict(pos=[1, 2], operation="unload")])
        self.assertEqual(subset_unload_pending(snapshot(1), current, {(1, 2)}), (False, []))

    def test_unknown_load_and_release_groups_fail_before_commands(self):
        runner = NativePartialRun(Path("unused"), None, None, [])
        with patch.object(runner, "_raw_probe") as raw:
            for operation in ("load-foreign", "release-foreign"):
                with self.assertRaises(ValueError):
                    runner.probe(operation, "unknown")
            raw.assert_not_called()

    def test_unload_callback_wait_preserves_actual_raw_event_and_tick(self):
        with tempfile.TemporaryDirectory() as temp:
            runner = NativePartialRun(Path(temp), None, None, [])
            runner._released_groups["machine"] = dict(before=snapshot(1), selected={(1, 2)}, deadline=1000)
            pending = snapshot(2, False, unloads=0)
            actual = snapshot(3, False, unloads=1, events=[dict(pos=[1, 2], operation="unload")])
            with patch.object(runner, "_raw_probe", side_effect=[pending, actual]) as raw, \
                    patch.object(runner, "_canonical") as canonical, patch.object(runner, "_record"), \
                    patch("native114_partial.time.monotonic", return_value=100), patch("native114_partial.time.sleep"):
                selected = runner._snapshot_ready("snapshot", "absent")
            self.assertIs(selected, actual)
            self.assertEqual(raw.call_count, 2)
            self.assertEqual(selected["worldTime"], 3)
            canonical.assert_called_once_with("absent-native-pending-01", "absent")

    def test_load_selects_first_real_tick_even_if_gameplay_wrong(self):
        definition = [dict(min=[16, 0, 32], max=[31, 100, 47])]
        with tempfile.TemporaryDirectory() as temp:
            runner = NativePartialRun(Path(temp), None, None, definition)
            rows = [snapshot(1, False, loads=0), snapshot(2, loads=1), snapshot(2, loads=1), snapshot(3, loads=1)]
            rows[-1]["cases"]["case"][0]["nbt"] = '{progress:0,WrongGameplay:1}'
            with patch.object(runner, "_raw_probe", side_effect=rows) as raw, \
                    patch.object(runner, "_canonical") as canonical, patch.object(runner, "_record"), \
                    patch("native114_partial.time.sleep"):
                selected = runner._load_ready("load", "reload")
            self.assertIs(selected, rows[-1])
            self.assertEqual(raw.call_count, 4)
            canonical.assert_called_once_with("reload-native-pending-002", "reload")

    def test_canonical_is_exact_raw_bytes_and_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "advantage-works-results"
            root.mkdir()
            raw = root / "lifecycle-selected.json"
            raw.write_bytes(b'{"worldTime":5,"raw":true}\n')
            runner = NativePartialRun(Path(temp), None, None, [])
            runner._canonical("selected", "canonical")
            self.assertEqual(raw.read_bytes(), (root / "lifecycle-canonical.json").read_bytes())
            with self.assertRaises(FileExistsError):
                runner._canonical("selected", "canonical")


if __name__ == "__main__":
    unittest.main()
