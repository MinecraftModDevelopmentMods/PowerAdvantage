"""Native lifecycle adapter regressions; no Minecraft process or retained world writes."""

import argparse
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from unittest.mock import Mock

import lifecycle
import native114_lifecycle as native
from matrix import WORLD, digest, fingerprint, write_json
from native114 import RIG


class NativeLifecycleTests(unittest.TestCase):
    def setUp(self):
        root = RIG / "build/port-evidence"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def source_args(self):
        source = self.root / "source"
        world = source / WORLD
        world.mkdir(parents=True)
        (world / "level.dat").write_bytes(b"unit-test-world-hash-only")
        mods = source / "mods"
        mods.mkdir()
        jar = mods / "archived-1.13.2.jar"
        jar.write_bytes(b"unit-test-pin-not-a-runtime")
        write_json(source / "input-jars.json", [{"file": jar.name, "sha256": digest(jar)}])
        history = {key: {"status": "PASS"} for key in ("conversion", "restart", "idempotence")}
        write_json(source / "state-comparison.json", history)
        write_json(source / "migration-fixture.json", [])
        write_json(source / "migration-restarted.json", [])
        manifest = self.root / "archived.json"
        write_json(manifest, {"target113": {"seed": str(source), "launcher": "forge-1.13.2-25.0.223.jar",
                                           "jars": [{"path": str(jar), "sha256": digest(jar)}]}})
        hashes = self.root / "source-world-hashes.json"
        write_json(hashes, fingerprint(world))
        java = self.root / "java.exe"
        java.write_bytes(b"not-launched")
        target = self.root / "target.json"
        write_json(target, {"target114": {"jars": [{"id": key} for key in
                                                   ("steamadvantage", "electricadvantage", "basemetals")]}})
        return argparse.Namespace(source=source, source_manifest=manifest, source_key="target113",
                                  source_hashes=hashes, target_manifest=target, java8=java,
                                  output=self.root / "output", port=25580)

    def test_native_subclass_inherits_original_operations_fixtures_and_oracles(self):
        self.assertIs(native.lifecycle.LifecycleRun, lifecycle.LifecycleRun)
        self.assertIs(native.lifecycle.fixtures, lifecycle.fixtures)
        for method in ("build", "midblock", "save_read_guards", "mining_results"):
            self.assertIs(getattr(native.NativeLifecycleRun, method), getattr(lifecycle.LifecycleRun, method))
        definition = native.lifecycle.fixtures()
        self.assertEqual(len(definition), 15)
        drills = [entry for entry in definition if "from" in entry]
        self.assertEqual({entry["id"] for entry in drills},
                         {prefix + facing for prefix in ("S-MOVE-", "E-MOVE-") for facing in lifecycle.FACINGS})
        for entry in drills:
            self.assertNotEqual((entry["from"][0] >> 4, entry["from"][2] >> 4),
                                (entry["to"][0] >> 4, entry["to"][2] >> 4))

    def test_full_seven_groups_cannot_pass_missing_or_failed_drills(self):
        definition = lifecycle.fixtures()
        group = {entry["id"]: {"status": "PASS"} for entry in definition if "from" in entry}
        groups = [copy.deepcopy(group) for _ in range(7)]
        self.assertTrue(native.require_groups(definition, groups))
        self.assertFalse(native.require_groups(definition, []))
        groups[0].pop("S-MOVE-down")
        self.assertFalse(native.require_groups(definition, groups))
        groups[0] = copy.deepcopy(group)
        groups[6]["E-MOVE-up"]["status"] = "FAIL"
        self.assertFalse(native.require_groups(definition, groups))

    def test_optional_load_tracing_precedes_build_and_does_not_replace_runner(self):
        runner = Mock(runtime=self.root, definition=lifecycle.fixtures())
        runner.build.side_effect = RuntimeError("stop before any real fixture")
        with patch.object(native, "require_native_boot"):
            with self.assertRaisesRegex(RuntimeError, "stop before"):
                native.exercise(runner, "unit", lambda result: None, trace_loads=True)
        runner.boot.assert_called_once_with("lifecycle")
        runner.probe.assert_called_once_with("trace-loads", "tracing-loads")
        self.assertEqual([call[0] for call in runner.mock_calls], ["boot", "probe", "build"])

    def test_source_preflight_is_read_only(self):
        args = self.source_args()
        before = fingerprint(args.source)
        observed, history = native.verify_source(args)
        self.assertEqual(before, observed)
        self.assertEqual(before, fingerprint(args.source))
        self.assertEqual(history["restart"]["status"], "PASS")

    def test_changed_source_world_is_rejected(self):
        args = self.source_args()
        (args.source / WORLD / "level.dat").write_bytes(b"tampered")
        with self.assertRaisesRegex(AssertionError, "fingerprint mismatch"):
            native.verify_source(args)

    def test_source_mods_must_match_archived_manifest_and_recorded_inputs(self):
        args = self.source_args()
        (args.source / "mods" / "unexpected.jar").write_bytes(b"extra")
        with self.assertRaisesRegex(AssertionError, "source jars differ"):
            native.verify_source(args)

    def test_failed_source_state_is_not_reclassified(self):
        args = self.source_args()
        history = native.read_json(args.source / "state-comparison.json")
        history["restart"]["status"] = "FAIL"
        write_json(args.source / "state-comparison.json", history)
        with self.assertRaisesRegex(AssertionError, "state gate did not pass"):
            native.verify_source(args)

    def test_world_hash_keys_are_portable_without_ignoring_files(self):
        native.require_equal_hashes({"region/r.0.0.mca": "abc"}, {"region\\r.0.0.mca": "ABC"}, "mismatch")
        for actual in ({}, {"region/r.0.0.mca": "DEF"}, {"region/r.0.0.mca": "ABC", "extra": "ABC"}):
            with self.assertRaisesRegex(AssertionError, "mismatch"):
                native.require_equal_hashes(actual, {"region/r.0.0.mca": "ABC"}, "mismatch")

    def test_wrong_runtime_and_errors_fail_native_boot_check(self):
        log = self.root / "console.log"
        good = ("Starting minecraft server version 1.14.4\n"
                "Forge mod loading, version 28.2.26, for MC 1.14.4 with MCP 20190829.143755\nDone\n")
        log.write_text(good)
        native.require_native_boot(log)
        notice = "[Thread-1/FATAL] [ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!\n"
        log.write_text(good + notice)
        native.require_native_boot(log)
        for text in (good.replace("1.14.4", "1.13.2"), good + "[Server thread/ERROR] failure\n",
                     good + "[Server thread/FATAL] failure\n", good + notice.replace("ForgeConfig/CORE", "Other/CORE")):
            log.write_text(text)
            with self.assertRaises(AssertionError):
                native.require_native_boot(log)

    def test_staged_extra_or_changed_jars_fail(self):
        runtime = self.root / "runtime"
        (runtime / "mods").mkdir(parents=True)
        seed = self.root / "seed"
        seed.mkdir()
        (seed / native.LAUNCHER).write_bytes(b"launcher")
        (runtime / native.LAUNCHER).write_bytes(b"launcher")
        jar = self.root / "candidate.jar"
        jar.write_bytes(b"candidate")
        (runtime / "mods" / jar.name).write_bytes(jar.read_bytes())
        write_json(runtime / "input-jars.json", [{"file": jar.name, "sha256": digest(jar)}])
        profile = (seed, native.LAUNCHER, [jar])
        native.verify_staged(runtime, profile)
        (runtime / "mods" / "extra.jar").write_bytes(b"unexpected")
        with self.assertRaisesRegex(AssertionError, "jar set changed"):
            native.verify_staged(runtime, profile)

    def test_failures_are_retained_both_profiles_attempted_and_source_rechecked(self):
        args = self.source_args()
        source_profile = native.pinned_profile(args.source_manifest, args.source_key)
        native_profile = (self.root / "native-seed", native.LAUNCHER, [])
        native_profile[0].mkdir()
        before = fingerprint(args.source)

        def profile(path, key):
            return native_profile if key == "target114" else source_profile

        with patch.object(native, "pinned_profile", side_effect=profile), \
                patch.object(native, "prepare", side_effect=RuntimeError("deliberate unit-test failure")) as prepare:
            self.assertEqual(native.run(args), 1)
        result = native.read_json(args.output / "native114-lifecycle-results.json")
        self.assertEqual(prepare.call_count, 2)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(result["source_fixture_unchanged"])
        self.assertTrue(result["helpers_unchanged"])
        self.assertEqual(result["sourceRuntimeBefore"], result["sourceRuntimeAfter"])
        self.assertEqual(fingerprint(args.source), before)
        for row in result["profiles"].values():
            self.assertIn("deliberate unit-test failure", row["failure"])


class NativeReadinessTests(unittest.TestCase):
    def setUp(self):
        root = RIG / "build/port-evidence"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.root = Path(self.temp.name)
        self.definition = [dict(id="drill", min=[0, 0, 0], max=[15, 100, 15],
                                witnesses=[[1, 80, 1]], to=[1, 80, 1], block="steamadvantage:steam_drill")]

    def tearDown(self):
        self.temp.cleanup()

    def snapshot(self, tick=100, loaded=True, loads=1, nbt="{}"):
        return dict(worldTime=tick, chunks=[dict(pos=[0, 0], loaded=loaded, loads=loads, unloads=0)],
                    chunkEvents=[dict(operation="load", pos=[0, 0], worldTime=99)] if loads else [],
                    cases={"drill": [dict(pos=[1, 80, 1], loaded=loaded, nbt=nbt)]})

    def runner(self, snapshots):
        runner = native.NativeLifecycleRun(self.root, None, None, self.definition)
        pending = iter(snapshots)

        def probe(operation, label):
            value = copy.deepcopy(next(pending))
            path = self.root / "advantage-works-results" / f"lifecycle-{label}.json"
            self.assertFalse(path.exists())
            write_json(path, value)
            return value

        runner._raw_probe = Mock(side_effect=probe)
        return runner

    def test_readiness_does_not_inspect_machine_nbt_block_or_progress(self):
        first, ready = self.snapshot(), self.snapshot(101, nbt="not even parsed")
        runner = self.runner([ready])
        with patch.object(native.time, "sleep"):
            selected = runner._wait_ready("test", first)
        self.assertEqual(selected, ready)
        self.assertEqual(runner.observations[-1]["first_ready"]["world_time"], 101)
        self.assertEqual(native.loaded_issues(ready, self.definition), [])

    def unloaded(self, tick=101, callbacks=0, events=False):
        value = self.snapshot(tick, loaded=False)
        value["chunks"][0]["unloads"] = callbacks
        if events:
            value["chunkEvents"].append(dict(operation="unload", pos=[0, 0], worldTime=tick))
        return value

    def unload_runner(self, snapshots, deadline=45):
        runner = self.runner(snapshots)
        runner._unload_context = dict(name="cycle", before=self.snapshot(), deadline=deadline)
        return runner

    def test_absent_holder_waits_for_queued_real_unload_callback(self):
        pending, complete = self.unloaded(), self.unloaded(102, callbacks=1, events=True)
        runner = self.unload_runner([pending, complete])
        with patch.object(native.time, "sleep"), patch.object(native.time, "monotonic", return_value=0):
            selected = runner.probe("snapshot", "cycle-absent-00")
        self.assertEqual(selected, complete)
        self.assertEqual(runner._raw_probe.call_count, 2)
        lifecycle.require_unloaded(self.snapshot(), selected)
        self.assertEqual(native.read_json(self.root / "advantage-works-results/lifecycle-cycle-absent-00.json"), complete)
        self.assertTrue((self.root / "advantage-works-results/lifecycle-cycle-absent-00-native-initial.json").exists())

    def test_queued_callback_wait_never_extends_original_unload_budget(self):
        pending = self.unloaded()
        runner = self.unload_runner([pending], deadline=1)
        with patch.object(native.time, "sleep") as sleep, patch.object(native.time, "monotonic", return_value=1):
            selected = runner.probe("snapshot", "cycle-absent-44")
        self.assertEqual(selected, pending)
        sleep.assert_not_called()
        with self.assertRaisesRegex(AssertionError, "No real unload event"):
            lifecycle.require_unloaded(self.snapshot(), selected)

    def test_still_loaded_snapshot_returns_to_inherited_polling(self):
        loaded = self.snapshot(101)
        runner = self.unload_runner([loaded])
        with patch.object(native.time, "sleep") as sleep:
            selected = runner.probe("snapshot", "cycle-absent-00")
        self.assertEqual(selected, loaded)
        sleep.assert_not_called()

    def test_unload_counter_without_an_actual_event_is_not_ready(self):
        pending = self.unloaded(callbacks=1)
        runner = self.unload_runner([pending], deadline=0)
        with patch.object(native.time, "monotonic", return_value=0):
            runner.probe("snapshot", "cycle-absent-00")
        self.assertEqual(runner.observations[-1]["status"], "PENDING")
        self.assertTrue(native.unload_event_issues(self.snapshot(), pending))

    def test_unload_context_cannot_leak_to_another_cycle_after_failure(self):
        runner = self.runner([])
        with patch.object(lifecycle.LifecycleRun, "unload_reload", side_effect=RuntimeError("probe failure")):
            with self.assertRaisesRegex(RuntimeError, "probe failure"):
                runner.unload_reload("cycle")
        self.assertIsNone(runner._unload_context)

    def test_unload_wrapper_delegates_the_original_protocol_exactly_once(self):
        runner = self.runner([])
        result = object()

        def original(instance, name):
            self.assertIs(instance, runner)
            self.assertEqual(name, "cycle")
            self.assertEqual(instance._unload_context, dict(name="cycle", before=None, deadline=None))
            return result

        with patch.object(lifecycle.LifecycleRun, "unload_reload", autospec=True, side_effect=original) as call:
            self.assertIs(runner.unload_reload("cycle"), result)
        call.assert_called_once_with(runner, "cycle")
        self.assertIsNone(runner._unload_context)

    def test_unload_budget_is_anchored_to_release_and_not_extended_by_samples(self):
        runner = self.unload_runner([self.unloaded(), self.unloaded()])
        with patch.object(native.time, "monotonic", return_value=10):
            runner.probe("release", "cycle-released")
        self.assertEqual(runner._unload_context["deadline"], 55)
        with patch.object(native.time, "monotonic", return_value=55):
            runner.probe("snapshot", "cycle-absent-44")
        self.assertEqual(runner._unload_context["deadline"], 55)

    def test_reload_waits_for_genuine_load_then_first_tick_without_reading_values(self):
        absent = self.unloaded(callbacks=1, events=True)
        initial, ready = self.snapshot(102, loads=2), self.snapshot(103, loads=2, nbt="{progress:0s}")
        runner = self.unload_runner([initial, ready])
        runner._unload_context["absent"] = absent
        with patch.object(native.time, "sleep"):
            selected = runner.probe("load", "cycle-loaded")
        self.assertEqual(selected, ready)
        self.assertEqual(runner._raw_probe.call_args_list[0].args[0], "load")
        self.assertEqual(native.read_json(self.root / "advantage-works-results/lifecycle-cycle-loaded.json"), ready)

    def test_wait_uses_first_real_tick_even_when_desired_values_arrive_later(self):
        before = self.snapshot(nbt="{progress:12s}")
        immediate = self.snapshot(nbt="{progress:12s}")
        same_tick = self.snapshot(nbt="{progress:12s}")
        first_tick = self.snapshot(101, nbt="{progress:0s}")
        later_pass = self.snapshot(102, nbt="{progress:13s}")
        runner = self.runner([before, immediate, same_tick, first_tick, later_pass])
        with patch.object(native.time, "sleep"), \
                patch.object(lifecycle, "progress_results", side_effect=AssertionError("oracle inside wait")), \
                patch.object(lifecycle, "changed_target_results", side_effect=AssertionError("oracle inside wait")):
            selected = runner.probe("legacy-read", "legacy")
        self.assertEqual(selected, first_tick)
        self.assertEqual(runner._raw_probe.call_count, 4)
        canonical = native.read_json(self.root / "advantage-works-results/lifecycle-legacy.json")
        self.assertEqual(canonical, first_tick)
        self.assertNotEqual(canonical, later_pass)
        record = runner.observations[-1]
        source = self.root / "advantage-works-results" / ("lifecycle-" + record["selected_label"] + ".json")
        self.assertEqual(source.read_bytes(), (self.root / "advantage-works-results/lifecycle-legacy.json").read_bytes())
        self.assertEqual([sample["world_time"] for sample in record["samples"]], [100, 100, 101])

    def test_changed_read_is_not_reissued_or_retried_until_target_clears(self):
        before, immediate = self.snapshot(nbt="{progress:12s}"), self.snapshot(nbt="{progress:12s}")
        retargeted = self.snapshot(101, nbt="{progress:1s,targetX:2,targetY:80,targetZ:1}")
        runner = self.runner([before, immediate, retargeted, self.snapshot(102, nbt="{progress:0s}")])
        with patch.object(native.time, "sleep"):
            self.assertEqual(runner.probe("changed-target-read", "changed"), retargeted)
        self.assertEqual([call.args[0] for call in runner._raw_probe.call_args_list],
                         ["snapshot", "changed-target-read", "snapshot"])

    def guard_samples(self, changed_items="[]"):
        old = "{progress:12s,targetX:2,targetY:80,targetZ:1,Items:[]}"
        advanced = old.replace("12s", "13s")
        cleared = "{progress:0s,Items:" + changed_items + "}"
        return [self.snapshot(nbt=old), self.snapshot(nbt=old), self.snapshot(nbt=old),
                self.snapshot(101, nbt=advanced), self.snapshot(101, nbt=advanced),
                self.snapshot(101, nbt=cleared), self.snapshot(102, nbt=cleared)]

    def test_inherited_guard_calls_each_unchanged_oracle_once_after_selection(self):
        runner = self.runner(self.guard_samples())
        with patch.object(native.time, "sleep"), \
                patch.object(lifecycle, "progress_results", wraps=lifecycle.progress_results) as progress, \
                patch.object(lifecycle, "changed_target_results", wraps=lifecycle.changed_target_results) as changed:
            results = runner.save_read_guards()
        self.assertEqual(progress.call_count, 1)
        self.assertEqual(changed.call_count, 1)
        self.assertEqual(results["legacy_read"]["drill"]["status"], "PASS")
        self.assertEqual(results["changed_target_read"]["drill"]["status"], "PASS")

    def test_inherited_unchanged_inventory_guard_still_fails(self):
        runner = self.runner(self.guard_samples('[{id:"minecraft:dirt",Count:1b}]'))
        with patch.object(native.time, "sleep"):
            with self.assertRaisesRegex(AssertionError, "changed drill inventory"):
                runner.save_read_guards()

    def test_loaded_chunks_need_actual_load_events_and_counter_increase(self):
        before = self.snapshot(loaded=False, loads=1)
        loaded = self.snapshot(101, loads=1)
        self.assertTrue(native.load_event_issues(before, loaded))
        loaded["chunks"][0]["loads"] = 2
        self.assertEqual(native.load_event_issues(before, loaded), [])
        loaded["chunkEvents"] = []
        self.assertTrue(native.load_event_issues(before, loaded))

    def test_tick_gate_starts_only_once_all_witnesses_are_loaded(self):
        first = self.snapshot(loaded=False, loads=0)
        loaded, ready = self.snapshot(101), self.snapshot(102)
        runner = self.runner([loaded, ready])
        with patch.object(native.time, "sleep"):
            self.assertEqual(runner._wait_ready("test", first, reload_before=first), ready)
        samples = runner.observations[-1]["samples"]
        self.assertIsNone(samples[0]["loaded_since_world_time"])
        self.assertEqual(samples[1]["loaded_since_world_time"], 101)

    def test_missing_witness_and_missing_chunk_are_never_ready(self):
        for key in ("cases", "chunks"):
            value = self.snapshot()
            value[key] = {} if key == "cases" else []
            self.assertTrue(native.loaded_issues(value, self.definition))

    def test_stalled_native_time_fails_bounded_without_synthetic_advancement(self):
        runner = self.runner([self.snapshot()] * 4)
        with patch.object(native, "READINESS_SAMPLES", 3), patch.object(native.time, "sleep"):
            with self.assertRaisesRegex(TimeoutError, "actual ticks"):
                runner._wait_ready("test", self.snapshot())
        self.assertEqual(runner.observations[-1]["status"], "FAIL")
        self.assertTrue(all(row["world_time"] == 100 for row in runner.observations[-1]["samples"]))

    def test_wall_guard_cannot_be_passed_by_late_tick(self):
        runner = self.runner([self.snapshot(101)])
        with patch.object(native.time, "monotonic", side_effect=[0, 0, 21]), patch.object(native.time, "sleep"):
            with self.assertRaises(TimeoutError):
                runner._wait_ready("test", self.snapshot())

    def test_native_restart_only_restores_tickets_not_fixtures(self):
        absent, loaded, ready = self.snapshot(loaded=False, loads=0), self.snapshot(), self.snapshot(101)
        runner = self.runner([absent, loaded, ready])
        with patch.object(lifecycle.LifecycleRun, "boot") as boot, patch.object(native, "require_native_boot"), \
                patch.object(native.time, "sleep"):
            runner.boot("midblock-restart")
        boot.assert_called_once_with("midblock-restart")
        self.assertEqual([call.args[0] for call in runner._raw_probe.call_args_list], ["snapshot", "load", "snapshot"])
        self.assertEqual(runner.observations[-1]["reload_before_label"], "midblock-restart-native-unrestored")

    def test_initial_and_frozen_boot_never_rebuild_or_load_lifecycle_fixture(self):
        runner = self.runner([])
        with patch.object(lifecycle.LifecycleRun, "boot"):
            runner.boot("lifecycle")
            runner.boot("retained13-import")
        runner._raw_probe.assert_not_called()

    def test_raw_snapshot_overwrite_rejected(self):
        runner = native.NativeLifecycleRun(self.root, None, None, self.definition)
        write_json(self.root / "advantage-works-results/lifecycle-original.json", self.snapshot())
        with patch.object(lifecycle.LifecycleRun, "probe") as original:
            with self.assertRaises(FileExistsError):
                runner._raw_probe("snapshot", "original")
        original.assert_not_called()


if __name__ == "__main__":
    unittest.main()
