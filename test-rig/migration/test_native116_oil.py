"""Focused native oil safety, protocol and unchanged-oracle negative controls."""

from copy import deepcopy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

import lifecycle
from matrix import WORLD, digest, fingerprint, write_json
from native116 import LAUNCHER, RIG
from native116_code import INVENTORY, code_files
import native116_oil as native
import oil_transfer


BOOT_PROOF = ("Starting minecraft server version 1.16.5\n"
              "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550\n")


def snapshot(definition, amount, identity=0):
    cases = {}
    for index, lane in enumerate(definition):
        cases[lane["id"]] = [
            dict(pos=lane["source"], block="minecraft:air"),
            dict(pos=lane["drain"], block="poweradvantage:fluid_drain", tileIdentity=identity + index * 2 + 1,
                 nbt="{Tank:{Empty:1b}}"),
            *[dict(pos=pos, block="poweradvantage:fluid_pipe") for pos in lane["pipes"]],
            dict(pos=lane["output"], block=lane["tank"], tileIdentity=identity + index * 2 + 2,
                 nbt=('{Tank:{Amount:' + str(amount) + ',FluidName:"' + lane["fluid"] + '"}}')
                 if amount else "{Tank:{Empty:1b}}")]
    return dict(cases=cases)


class NativeOilTests(unittest.TestCase):
    def test_mineralogy_pin_matches_every_native116_profile(self):
        seen = 0
        for path in (RIG / "profiles/native116").glob("*.json"):
            for mod in json.loads(path.read_text(encoding="utf-8"))["mods"]:
                if mod["id"] == "mineralogy":
                    self.assertEqual(native.MINERALOGY_VERSION, mod["version"], path.name)
                    self.assertEqual(native.MINERALOGY_SOURCE_COMMIT, mod["sourceRef"], path.name)
                    if mod["sha256"] is not None:
                        self.assertRegex(mod["sha256"], r"^[A-Fa-f0-9]{64}$")
                    seen += 1
        self.assertGreater(seen, 0)

    def setUp(self):
        root = RIG / "build/port-evidence"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def mineralogy_pin(self):
        artifact = self.root / "Mineralogy-6.1.4.116051.jar"
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="mineralogy"\nversion="6.1.4.116051"\n')
        return dict(id="mineralogy", version=native.MINERALOGY_VERSION, sha256=digest(artifact),
                    path=str(artifact), sourceRef=native.MINERALOGY_SOURCE_COMMIT)

    def profile(self):
        seed = self.root / "installed-seed"
        (seed / "libraries/subdir").mkdir(parents=True)
        (seed / "libraries/subdir/runtime.jar").write_bytes(b"unit-test-library-not-executed")
        (seed / LAUNCHER).write_bytes(b"unit-test-launcher-not-executed")
        (seed / "minecraft_server.1.16.5.jar").write_bytes(b"unit-test-vanilla-not-executed")
        (seed / WORLD).mkdir()
        (seed / WORLD / "level.dat").write_bytes(b"protected-unit-test-world-never-copied")
        (seed / "mods").mkdir()
        (seed / "mods/unpinned.jar").write_bytes(b"never-copy-seed-mods")
        (seed / "server.properties").write_text("protected-seed-settings\n")
        jars = []
        for mod in ("poweradvantage", "advantageworkstest", "orespawn"):
            jar = self.root / (mod + "-1.0.0.116051.jar")
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="' + mod + '"\nversion="1.0.0.116051"\n')
            jars.append(dict(path=str(jar), sha256=digest(jar), id=mod, version="1.0.0.116051"))
        server_code = {"files": code_files(seed)}
        write_json(seed / INVENTORY, server_code)
        pins = dict(seed=str(seed), launcher=LAUNCHER, launcher_sha256=digest(seed / LAUNCHER), jars=jars,
                    serverCode=server_code)
        manifest = self.root / "target.json"
        write_json(manifest, dict(target116=pins))
        java = self.root / "unit-java-not-executed.exe"
        java.write_bytes(b"unit test executable placeholder, process factory is mocked")
        args = SimpleNamespace(target_manifest=manifest, output=self.root / "native116-oil-unit",
                               java8=java, port=25576, seconds=2)
        return args, (seed, LAUNCHER, [Path(jar["path"]) for jar in jars]), pins

    def server_factory(self, mutate=None, transform=None, failure=None, shutdown=False):
        owner = self
        self.processes = []
        self.commands = []

        class ProtocolServer:
            """Unit protocol double; no Java or Minecraft process is started."""
            def __init__(self, runtime, java, launcher, label):
                self.runtime, self.label = runtime, label
                self.closed = False
                self.identity = len(owner.processes) * 100
                owner.processes.append(self)
                self.definition = native.read_json(runtime / "lifecycle-fixture.json")
                (runtime / f"console-{label}.log").write_text(BOOT_PROOF)

            def send(self, command):
                owner.commands.append((self.label, command))
                if mutate:
                    mutate(self, command)
                if not command.startswith("advlifecycle "):
                    return
                _, operation, label = command.split()
                if label == failure:
                    raise AssertionError("unit-injected probe failure")
                amount = {"oil-empty": 0, "oil-first-fill": 1000, "oil-restored": 1000,
                          "oil-refilled": 2000, "oil-retained": 2000}.get(label, 0)
                value = snapshot(self.definition, amount, self.identity)
                if transform:
                    transform(label, value)
                write_json(self.runtime / "advantage-works-results" / f"lifecycle-{label}.json", value)

            def wait(self, token, timeout):
                pass

            def close(self):
                self.closed = True
                if shutdown:
                    raise AssertionError("unit-injected shutdown failure")

        return ProtocolServer

    def report(self, args):
        return native.read_json(args.output / "native116-oil-results.json")

    def test_power_definitions_are_exact_unchanged_copies(self):
        original = oil_transfer.fixtures()
        lanes = native.fixtures(dict(jars=[]))
        self.assertEqual(lanes, original)
        lanes[0]["source"][0] += 1
        self.assertEqual(oil_transfer.fixtures(), original)

    def test_optional_mineralogy_has_two_distinct_native_lanes_without_mutating_power(self):
        pins = dict(jars=[self.mineralogy_pin()])
        lanes = native.fixtures(pins)
        self.assertEqual(lanes[:4], oil_transfer.fixtures())
        self.assertEqual(len(lanes), 6)
        for lane, template in zip(lanes[4:], lanes[:2]):
            self.assertEqual(lane["fluid"], "mineralogy:crude_oil")
            self.assertEqual(lane["bucket"], "mineralogy:crude_oil_bucket")
            self.assertEqual(lane["tank"], template["tank"])
            for point, old in zip(lane["witnesses"], template["witnesses"]):
                self.assertEqual(point, [old[0] + 256, *old[1:]])
        native.require_document(snapshot(lanes, 1000), lanes)
        self.assertEqual(len(oil_transfer.inspect(snapshot(lanes, 1000), lanes, 1000)), 6)

    def test_mineralogy_wrong_version_hash_or_duplicate_is_not_silently_skipped(self):
        good = self.mineralogy_pin()
        for jars in ([dict(good, version="6.0.0.113021")], [dict(good, sha256="0" * 64)], [good, good]):
            with self.subTest(jars=jars), self.assertRaises(ValueError):
                native.fixtures(dict(jars=jars))

    def test_bounds_headroom_connectivity_overlap_and_duplicate_ids_are_strict(self):
        def moved(lanes):
            lanes[0]["source"][0] += 1
        mutations = [lambda lanes: lanes[0]["min"].__setitem__(0, 0),
                     lambda lanes: lanes[0]["max"].__setitem__(0, lanes[0]["min"][0] + 32),
                     lambda lanes: lanes[0]["source"].__setitem__(1, lanes[0]["max"][1]),
                     moved, lambda lanes: lanes.append(deepcopy(lanes[0]))]
        for mutate in mutations:
            lanes = oil_transfer.fixtures()
            mutate(lanes)
            with self.assertRaises(AssertionError):
                native.require_bounds(lanes)

    def test_missing_extra_duplicate_and_moved_snapshot_witnesses_fail(self):
        lanes = oil_transfer.fixtures()
        for mutate in (lambda s: s["cases"].pop(lanes[0]["id"]),
                       lambda s: s["cases"].update(extra=[]),
                       lambda s: s["cases"][lanes[0]["id"]].pop(),
                       lambda s: s["cases"][lanes[0]["id"]].append(s["cases"][lanes[0]["id"]][0]),
                       lambda s: s["cases"][lanes[0]["id"]][0].update(pos=[0, 80, 0])):
            document = snapshot(lanes, 1000)
            mutate(document)
            with self.assertRaises(AssertionError):
                native.require_document(document, lanes)

    def test_existing_quantity_fluid_and_tile_identity_oracles_remain_strict(self):
        lanes = native.fixtures(dict(jars=[]))
        for amount in (0, 999, 1001, 2000):
            with self.assertRaises(AssertionError):
                oil_transfer.inspect(snapshot(lanes, amount), lanes, 1000)
        wrong = snapshot(lanes, 1000)
        wrong["cases"][lanes[0]["id"]][-1]["nbt"] = '{Tank:{Amount:1000,FluidName:"minecraft:water"}}'
        with self.assertRaises(AssertionError):
            oil_transfer.inspect(wrong, lanes, 1000)
        with self.assertRaises(AssertionError):
            oil_transfer.require_same_machines(snapshot(lanes, 1000), snapshot(lanes, 2000, 100), lanes)

    def test_native_log_requires_both_versions_and_rejects_runtime_command_and_probe_errors(self):
        log = self.root / "console.log"
        log.write_text(BOOT_PROOF + "[watcher/DEBUG] " + native.CONFIG_NOTICE + "\n")
        native.require_native_boot(log)
        for text in (BOOT_PROOF.replace("1.16.5", "1.13.2"), BOOT_PROOF.replace("36.2.34", "25.0.223"),
                     *[BOOT_PROOF + failure + "\n" for failure in (
                         "[main/ERROR] bad", "[main/FATAL] actual fatal", "[watcher/FATAL] " + native.CONFIG_NOTICE,
                         "NoSuchFieldError: bad",
                         "NoSuchMethodError: bad", "Failed to reload data packs", "Lifecycle probe failed",
                         "ADVLIFECYCLE FAIL", "Unknown or incomplete command",
                         "Incorrect argument for command", "An unexpected error occurred trying to execute that command")]):
            log.write_text(text)
            with self.subTest(text=text), self.assertRaises(AssertionError):
                native.require_native_boot(log)

    def test_prepare_copies_pinned_libraries_only_never_seed_world_or_settings(self):
        args, profile, pins = self.profile()
        before = fingerprint(profile[0])
        runtime = self.root / "target"
        definition = native.fixtures(pins)
        self.assertEqual(native.prepare(runtime, profile, definition, args.port, pins, before), LAUNCHER)
        self.assertFalse((runtime / WORLD).exists())
        self.assertFalse((runtime / "mods/unpinned.jar").exists())
        self.assertIn("server-port=25576", (runtime / "server.properties").read_text())
        self.assertNotIn("3;minecraft:bedrock", (runtime / "server.properties").read_text())
        self.assertEqual(fingerprint(profile[0]), before)
        self.assertEqual(native.read_json(runtime / "lifecycle-fixture.json"), definition)

    def test_staged_jars_cannot_follow_a_changed_original_instead_of_captured_pins(self):
        args, profile, pins = self.profile()
        before = fingerprint(profile[0])
        runtime = self.root / "target"
        native.prepare(runtime, profile, native.fixtures(pins), args.port, pins, before)
        jar = profile[2][0]
        jar.write_bytes(b"changed after pin")
        (runtime / "mods" / jar.name).write_bytes(jar.read_bytes())
        write_json(runtime / "input-jars.json", [dict(file=p.name, sha256=digest(p)) for p in profile[2]])
        with self.assertRaises(AssertionError):
            native.verify_staged(runtime, pins, before)

    def test_staged_launcher_and_library_tampering_fail(self):
        args, profile, pins = self.profile()
        before = fingerprint(profile[0])
        runtime = self.root / "target"
        native.prepare(runtime, profile, native.fixtures(pins), args.port, pins, before)
        for path in (runtime / LAUNCHER, runtime / "libraries/subdir/runtime.jar"):
            old = path.read_bytes()
            path.write_bytes(b"changed")
            with self.assertRaises(AssertionError):
                native.verify_staged(runtime, pins, before)
            path.write_bytes(old)

    def test_only_ordered_initial_construction_is_allowed_and_never_after_baseline(self):
        args, profile, pins = self.profile()
        lanes = native.fixtures(pins)
        runner = native.NativeOilRun(self.root, args.java8, LAUNCHER, lanes, pins, {})
        runner.boots = [native.BOOT_LABELS[0]]
        runner.server = SimpleNamespace(send=lambda command: None)
        for command in ("advlifecycle offer illicit", "data merge block 11272 80 2056 {}",
                        "setblock 11272 81 2056 poweradvantage:crude_oil",
                        "advworks reset all", "advlifecycle snapshot ok\nstop"):
            with self.subTest(command=command), self.assertRaises(AssertionError):
                runner.command(command)
        with self.assertRaises(AssertionError):
            runner.seal_construction()
        initial = native.construction_commands(lanes)
        for command in initial:
            runner.command(command)
        runner.seal_construction()
        for command in initial:
            with self.assertRaises(AssertionError):
                runner.command(command)

    def test_buckets_require_empty_or_restored_gate_and_at_most_two_placements(self):
        args, profile, pins = self.profile()
        runner = native.NativeOilRun(self.root, args.java8, LAUNCHER, native.fixtures(pins), pins, {})
        runner.server = SimpleNamespace(send=lambda command: None)
        runner.boots = [native.BOOT_LABELS[0]]
        with self.assertRaises(AssertionError):
            runner.command("advlifecycle place-buckets too-soon")
        with self.assertRaises(AssertionError):
            runner.permit_buckets()
        runner.pending_construction = []
        runner.seal_construction()
        for index in range(2):
            runner.boots = list(native.BOOT_LABELS[:index + 1])
            runner.permit_buckets()
            runner.command("advlifecycle place-buckets permitted")
            with self.assertRaises(AssertionError):
                runner.command("advlifecycle place-buckets duplicate")
        runner.boots = list(native.BOOT_LABELS)
        with self.assertRaises(AssertionError):
            runner.permit_buckets()

    def test_complete_protocol_three_boots_two_source_only_placements_and_all_evidence(self):
        args, profile, pins = self.profile()
        before = fingerprint(profile[0])
        with patch.object(lifecycle, "Server", self.server_factory()), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 0)
        report = self.report(args)
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["full_process_restarts"], 2)
        self.assertEqual(report["bucket_placements"], 2)
        self.assertEqual([phase["phase"] for phase in report["phases"]], ["first-fill", "restored", "refilled", "retained"])
        self.assertEqual([p["cases"][0]["total_mB"] for p in report["phases"]], [1000, 1000, 2000, 2000])
        self.assertEqual(report["laneCount"], 4)
        self.assertEqual(report["seedFingerprintBefore"], before)
        self.assertEqual(report["seedFingerprintAfter"], before)
        self.assertTrue(report["helpers_unchanged"] and report["seed_unchanged"] and report["manifest_unchanged"])
        self.assertEqual([p.label for p in self.processes], list(native.BOOT_LABELS))
        self.assertTrue(all(p.closed for p in self.processes))
        later = [command for label, command in self.commands if label != native.BOOT_LABELS[0]]
        self.assertFalse(any(command.startswith(("fill ", "setblock ", "data ")) for command in later))
        self.assertEqual(sum("place-buckets" in command for command in later), 1)
        self.assertEqual((args.output / "target-manifest.json").read_bytes(), args.target_manifest.read_bytes())

    def test_probe_failure_is_retained_with_seed_fingerprints_and_process_closed(self):
        args, profile, _ = self.profile()
        before = fingerprint(profile[0])
        with patch.object(lifecycle, "Server", self.server_factory(failure="oil-refilled")), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        report = self.report(args)
        self.assertEqual(report["status"], "FAIL")
        self.assertIn("probe failure", report["failure"])
        self.assertEqual(report["seedFingerprintAfter"], before)
        self.assertTrue(all(p.closed for p in self.processes))

    def test_shutdown_failure_cannot_be_success(self):
        args, _, _ = self.profile()
        with patch.object(lifecycle, "Server", self.server_factory(shutdown=True)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertIn("shutdown failure", self.report(args)["failure"])

    def test_restart_quantity_loss_blocks_refill_not_repaired_by_new_sources(self):
        args, _, _ = self.profile()
        def transform(label, value):
            if label == "oil-restored":
                lane = next(iter(value["cases"].values()))
                lane[-1]["nbt"] = lane[-1]["nbt"].replace("Amount:1000", "Amount:999")
        with patch.object(lifecycle, "Server", self.server_factory(transform=transform)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertIn("Lost/duplicated oil", self.report(args)["failure"])
        self.assertEqual(sum("place-buckets" in command for _, command in self.commands), 1)
        self.assertEqual(len(self.processes), 2)

    def test_nonempty_baseline_cannot_place_any_bucket(self):
        args, _, _ = self.profile()
        def transform(label, value):
            if label == "oil-empty":
                next(iter(value["cases"].values()))[-1]["nbt"] = '{Tank:{Amount:1,FluidName:"poweradvantage:crude_oil"}}'
        with patch.object(lifecycle, "Server", self.server_factory(transform=transform)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertIn("Fixture was not empty", self.report(args)["failure"])
        self.assertFalse(any("place-buckets" in command for _, command in self.commands))

    def test_refill_cannot_replace_a_machine_even_with_exact_amount(self):
        args, _, _ = self.profile()
        def transform(label, value):
            if label == "oil-refilled":
                next(iter(value["cases"].values()))[-1]["tileIdentity"] += 1
        with patch.object(lifecycle, "Server", self.server_factory(transform=transform)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertIn("Refill replaced a machine", self.report(args)["failure"])
        self.assertEqual(len(self.processes), 2)

    def test_missing_initial_identity_cannot_qualify(self):
        args, _, _ = self.profile()
        def transform(label, value):
            if label == "oil-empty":
                next(iter(value["cases"].values()))[1].pop("tileIdentity")
        with patch.object(lifecycle, "Server", self.server_factory(transform=transform)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertFalse(any("place-buckets" in command for _, command in self.commands))

    def test_staging_failure_still_retains_full_seed_fingerprints(self):
        args, profile, _ = self.profile()
        before = fingerprint(profile[0])
        with patch.object(native, "prepare", side_effect=AssertionError("unit-injected staging failure")):
            self.assertEqual(native.run(args), 1)
        report = self.report(args)
        self.assertIn("staging failure", report["failure"])
        self.assertEqual(report["seedFingerprintBefore"], before)
        self.assertEqual(report["seedFingerprintAfter"], before)

    def test_wrong_launcher_and_out_of_order_boots_are_rejected_before_process(self):
        args, _, pins = self.profile()
        runner = native.NativeOilRun(self.root, args.java8, "forge-1.13.2-25.0.223.jar", native.fixtures(pins), pins, {})
        with self.assertRaises(AssertionError):
            runner.boot(native.BOOT_LABELS[0])
        runner.launcher = LAUNCHER
        for label in (native.BOOT_LABELS[1], "invented-boot"):
            with self.assertRaises(AssertionError):
                runner.boot(label)

    def test_late_log_errors_are_audited_after_stop(self):
        args, _, _ = self.profile()
        def mutate(server, command):
            if command == "save-all flush":
                with (server.runtime / f"console-{server.label}.log").open("a") as stream:
                    stream.write("[main/ERROR] unit-injected late failure\n")
        with patch.object(lifecycle, "Server", self.server_factory(mutate=mutate)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertIn("runtime_audit_failure", self.report(args))

    def test_full_seed_fingerprint_catches_changes_outside_world(self):
        args, profile, _ = self.profile()
        def mutate(server, command):
            (profile[0] / "server.properties").write_text("unit-injected concurrent seed change")
        with patch.object(lifecycle, "Server", self.server_factory(mutate=mutate)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        report = self.report(args)
        self.assertFalse(report["seed_unchanged"])
        self.assertIn("server.properties", report["seedFingerprintAfter"])

    def test_mutable_manifest_is_detected_and_captured_manifest_retained(self):
        args, _, _ = self.profile()
        original = args.target_manifest.read_bytes()
        def mutate(server, command):
            args.target_manifest.write_bytes(original + b"\n")
        with patch.object(lifecycle, "Server", self.server_factory(mutate=mutate)), patch.object(native.time, "sleep"):
            self.assertEqual(native.run(args), 1)
        self.assertFalse(self.report(args)["manifest_unchanged"])
        self.assertEqual((args.output / "target-manifest.json").read_bytes(), original)

    def test_stale_input_manifest_fails_before_staging_or_process(self):
        args, profile, _ = self.profile()
        profile[2][0].write_bytes(b"not the pinned jar")
        with patch.object(native, "prepare") as prepare, self.assertRaises(AssertionError):
            native.run(args)
        prepare.assert_not_called()
        self.assertFalse(args.output.exists())

    def test_output_cannot_overlap_seed_manifest_or_leave_scoped_build(self):
        args, profile, _ = self.profile()
        for output in (profile[0], profile[0] / "native116-oil-unsafe", args.target_manifest,
                       RIG / "migration/native116-oil-unsafe", self.root / "wrong-prefix"):
            args.output = output
            with self.subTest(output=output), self.assertRaises((ValueError, FileExistsError)):
                native.run(args)

    def test_invalid_port_or_interval_fails_before_staging(self):
        args, _, _ = self.profile()
        for port, seconds in ((0, 8), (65536, 8), (25576, 1), (25576, 121)):
            args.port, args.seconds = port, seconds
            with self.assertRaises(ValueError):
                native.run(args)
        self.assertFalse(args.output.exists())


if __name__ == "__main__":
    unittest.main()
