"""Run unchanged lifecycle fixtures/oracles in fresh and retained-copy native116 worlds.

Requires Python 3.11+, migration/requirements.txt, an explicit archived source
manifest and a hash-pinned native target manifest. Never launches a source.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import shutil
import time
import traceback

import lifecycle
from native116_code import verify_if_sealed
from matrix import WORLD, compare, copy_fixture, digest, fingerprint, stage, write_json
from native116 import stage
from native116 import (LAUNCHER, native_normalized, output_path, pinned_profile,
                       require_clean_log, require_disjoint)
from native116_geometry import require_geometry


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def portable_hashes(values):
    return {key.replace("\\", "/"): value.upper() for key, value in values.items()}


def require_equal_hashes(actual, expected, detail):
    if not expected or portable_hashes(actual) != portable_hashes(expected):
        raise AssertionError(detail)


def verify_source(args):
    source = args.source.resolve()
    before = fingerprint(source)
    require_equal_hashes(fingerprint(source / WORLD), read_json(args.source_hashes),
                         "Protected retained13 source world fingerprint mismatch")
    profile = pinned_profile(args.source_manifest.resolve(), args.source_key)
    if not profile[1].startswith(("forge-1.13.2-25.0.223", "forge-1.14.4-28.2.26")):
        raise ValueError("The retained source must use its archived native 1.13.2 or accepted 1.14.4 profile")
    expected = sorted((jar.name, digest(jar)) for jar in profile[2])
    staged = sorted((entry["file"], entry["sha256"].upper())
                    for entry in read_json(source / "input-jars.json"))
    actual = sorted((jar.name, digest(jar)) for jar in (source / "mods").glob("*.jar"))
    if staged != expected or actual != expected:
        raise AssertionError("Retained source jars differ from the explicit archived profile")
    history = read_json(source / "state-comparison.json")
    gates = ("converted", "restarted", "idempotence") if "converted" in history else ("conversion", "restart", "idempotence")
    for gate in gates:
        if history.get(gate, {}).get("status") != "PASS":
            raise AssertionError("Retained source state gate did not pass: " + gate)
    for name in ("migration-fixture.json", "migration-restarted.json"):
        if not (source / name).is_file():
            raise FileNotFoundError(source / name)
    if fingerprint(source) != before:
        raise AssertionError("Retained source changed during preflight")
    return before, history


def require_native_boot(path):
    require_clean_log(path)
    text = path.read_text(encoding="utf-8", errors="replace")
    for marker in ("Starting minecraft server version 1.16.5",
                   "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550"):
        if marker not in text:
            raise AssertionError("Missing genuine native116 boot proof: " + marker)
    # Forge 36.2.34 ForgeConfig.onFileChange logs this exact notice at FATAL
    # without throwing. No other fatal message is exempted from this adapter gate.
    reload_notice = "[ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!"
    if any("/FATAL]" in line and not line.endswith(reload_notice) for line in text.splitlines()):
        raise AssertionError("Fatal native runtime error in " + str(path))


def verify_staged(runtime, profile):
    verify_if_sealed(runtime)
    expected = sorted((jar.name, digest(jar)) for jar in profile[2])
    actual = sorted((jar.name, digest(jar)) for jar in (runtime / "mods").glob("*.jar"))
    recorded = sorted((entry["file"], entry["sha256"].upper())
                      for entry in read_json(runtime / "input-jars.json"))
    if actual != expected or recorded != expected:
        raise AssertionError("Staged native jar set changed: " + str(runtime))
    if digest(runtime / LAUNCHER) != digest(profile[0] / LAUNCHER):
        raise AssertionError("Staged native launcher changed")


def prepare(runtime, profile, definition, port, source=None):
    if profile[1] != LAUNCHER:
        raise ValueError("Refusing a non-native116 lifecycle launcher")
    launcher = stage(runtime, profile)
    if source is not None:
        copy_fixture(source, runtime)
    properties = runtime / "server.properties"
    lines = [line for line in properties.read_text().splitlines()
             if not line.startswith(("server-port=", "generator-settings="))]
    properties.write_text("\n".join(lines) + f"\nserver-port={port}\ngenerator-settings=\n", encoding="utf-8")
    write_json(runtime / "lifecycle-fixture.json", definition)
    verify_staged(runtime, profile)
    return launcher


def authorize_lifecycle(runtime):
    for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
        (runtime / marker).touch()


def require_groups(definition, groups):
    expected = {fixture["id"] for fixture in definition if "from" in fixture}
    if len(expected) != 12:
        raise AssertionError("Lifecycle requires the original twelve drill cases")
    return len(groups) == 7 and all(set(group) == expected and all(row.get("status") == "PASS" for row in group.values())
               for group in groups)


READINESS_SECONDS = 20
READINESS_TICKS = 160
READINESS_SAMPLES = 200
DEFERRED_TICKS = 1


def required_chunks(definition):
    return {(x, z) for fixture in definition
            for x in range(fixture["min"][0] >> 4, (fixture["max"][0] >> 4) + 1)
            for z in range(fixture["min"][2] >> 4, (fixture["max"][2] >> 4) + 1)}


def loaded_issues(snapshot, definition):
    issues = []
    chunks = snapshot.get("chunks", [])
    if len(chunks) != len(required_chunks(definition)) or {tuple(c["pos"]) for c in chunks} != required_chunks(definition):
        issues.append("fixture chunk witness set differs")
    if any(not chunk.get("loaded") for chunk in chunks):
        issues.append("fixture chunks unloaded")
    for fixture in definition:
        rows = snapshot.get("cases", {}).get(fixture["id"], [])
        if len(rows) != len(fixture["witnesses"]) or {tuple(row["pos"]) for row in rows} != {tuple(p) for p in fixture["witnesses"]}:
            issues.append(fixture["id"] + ": witness set differs")
        if any(not row.get("loaded") for row in rows):
            issues.append(fixture["id"] + ": witnesses unloaded")
    return issues


def load_event_issues(before, after):
    old = {tuple(chunk["pos"]): chunk for chunk in before["chunks"]}
    events = {tuple(event["pos"]) for event in after.get("chunkEvents", []) if event["operation"] == "load"}
    issues = []
    for chunk in after["chunks"]:
        pos = tuple(chunk["pos"])
        previous = old.get(pos)
        if (previous is None or pos not in events or chunk["loads"] <= 0
                or (not previous["loaded"] and chunk["loads"] <= previous["loads"])):
            issues.append("missing actual Chunk.Load: " + str(pos))
    return issues


def unload_event_issues(before, after):
    old = {tuple(chunk["pos"]): chunk for chunk in before["chunks"]}
    current = {tuple(chunk["pos"]): chunk for chunk in after["chunks"]}
    if not old or old.keys() != current.keys() or len(current) != len(after["chunks"]):
        return ["unload chunk witness set differs"]
    events = {tuple(event["pos"]) for event in after.get("chunkEvents", []) if event["operation"] == "unload"}
    return ["missing actual Chunk.Unload: " + str(pos) for pos, chunk in current.items()
            if pos not in events or chunk["unloads"] <= old[pos]["unloads"]]


class NativeLifecycleRun(lifecycle.LifecycleRun):
    """Native transport readiness only; inherited fixtures, operations and oracles."""

    def __init__(self, *args):
        super().__init__(*args)
        self.observations = []
        self._unload_context = None

    def _raw_probe(self, operation, label):
        path = self.runtime / "advantage-works-results" / f"lifecycle-{label}.json"
        if path.exists():
            raise FileExistsError("Refusing to overwrite native observation: " + str(path))
        result = super().probe(operation, label)
        require_geometry(result, forced=operation == "load")
        return result

    def _record(self):
        write_json(self.runtime / "native116-readiness.json", self.observations)

    def _wait_ready(self, label, initial, reload_before=None):
        started = time.monotonic()
        record = dict(label=label, status="PENDING", min_real_ticks=DEFERRED_TICKS,
                      max_seconds=READINESS_SECONDS, max_ticks=READINESS_TICKS,
                      initial_world_time=initial["worldTime"], samples=[])
        self.observations.append(record)
        loaded_at = None
        snapshot = initial
        try:
            for attempt in range(READINESS_SAMPLES):
                issues = loaded_issues(snapshot, self.definition)
                issues += load_event_issues(reload_before if reload_before is not None else initial, snapshot)
                now = snapshot["worldTime"]
                if now < initial["worldTime"]:
                    raise AssertionError("Native worldTime moved backwards")
                loaded_at = None if issues else (now if loaded_at is None else loaded_at)
                sample = dict(label=label + ("-initial" if attempt == 0 else f"-pending-{attempt:03}"),
                              world_time=now, elapsed_seconds=time.monotonic() - started, issues=issues,
                              loaded_since_world_time=loaded_at)
                record["samples"].append(sample)
                if sample["elapsed_seconds"] >= READINESS_SECONDS or now - initial["worldTime"] >= READINESS_TICKS:
                    break
                ready = loaded_at is not None and now - loaded_at >= DEFERRED_TICKS
                if ready:
                    record.update(status="PASS", first_ready=sample, selected_label=sample["label"])
                    self._record()
                    return snapshot
                self._record()
                if attempt + 1 == READINESS_SAMPLES:
                    break
                # Yield between observations; elapsed sleep is never evidence of a native tick.
                time.sleep(0.01)
                snapshot = self._raw_probe("snapshot", label + f"-pending-{attempt + 1:03}")
            raise TimeoutError("Native readiness did not observe loaded witnesses and actual ticks: " + label)
        except Exception as error:
            record.update(status="FAIL", failure=str(error))
            self._record()
            raise

    def boot(self, label):
        verify_if_sealed(self.runtime)
        super().boot(label)
        if label not in ("lifecycle-restart", "midblock-restart"):
            return
        require_native_boot(self.runtime / f"console-{label}.log")
        before = self._raw_probe("snapshot", label + "-native-unrestored")
        loaded = self._raw_probe("load", label + "-native-initial")
        self._wait_ready(label + "-native", loaded, reload_before=before)
        self.observations[-1]["reload_before_label"] = label + "-native-unrestored"
        self._record()

    def unload_reload(self, name):
        self._unload_context = dict(name=name, before=None, deadline=None)
        try:
            return super().unload_reload(name)
        finally:
            self._unload_context = None

    def _unload_observation(self, label):
        context = self._unload_context
        selected_label = label + "-native-initial"
        snapshot = self._raw_probe("snapshot", selected_label)
        record = dict(label=label, phase="native-unload-callback-readiness", samples=[])
        self.observations.append(record)
        # Verified Forge 1.15 behavior queues Unload after holder removal. Keep the
        # inherited 45-second budget; never fabricate flags or callback counters.
        for attempt in range(45):
            pending = unload_event_issues(context["before"], snapshot)
            absent = all(not chunk["loaded"] for chunk in snapshot["chunks"])
            record["samples"].append(dict(label=selected_label, world_time=snapshot["worldTime"],
                                           all_absent=absent, pending_events=pending))
            if not absent or not pending:
                break
            remaining = context["deadline"] - time.monotonic()
            if remaining <= 0 or attempt == 44:
                break
            self._record()
            time.sleep(min(1, remaining))
            selected_label = label + f"-native-pending-{attempt + 1:02}"
            snapshot = self._raw_probe("snapshot", selected_label)
        source = self.runtime / "advantage-works-results" / f"lifecycle-{selected_label}.json"
        canonical = self.runtime / "advantage-works-results" / f"lifecycle-{label}.json"
        if canonical.exists():
            raise FileExistsError(canonical)
        shutil.copy2(source, canonical)
        record.update(selected_label=selected_label, canonical_label=label,
                      status="READY" if absent and not pending else "PENDING")
        self._record()
        return snapshot

    def probe(self, operation, label):
        context = self._unload_context
        if context is not None:
            prefix = context["name"] + "-absent-"
            if operation == "snapshot" and label.startswith(prefix) and label[len(prefix):].isdigit():
                return self._unload_observation(label)
            if operation == "snapshot" and label == context["name"] + "-before":
                result = self._raw_probe(operation, label)
                context["before"] = result
                return result
            if operation == "release" and label == context["name"] + "-released":
                result = self._raw_probe(operation, label)
                context["deadline"] = time.monotonic() + 45
                return result
            if operation == "snapshot" and label == context["name"] + "-absent-confirmed":
                result = self._raw_probe(operation, label)
                context["absent"] = result
                return result
            if operation == "load" and label == context["name"] + "-loaded":
                initial = self._raw_probe(operation, label + "-native-initial")
                selected = self._wait_ready(label + "-native", initial, reload_before=context["absent"])
                source = self.runtime / "advantage-works-results" / (
                    "lifecycle-" + self.observations[-1]["selected_label"] + ".json")
                canonical = self.runtime / "advantage-works-results" / f"lifecycle-{label}.json"
                if canonical.exists():
                    raise FileExistsError(canonical)
                shutil.copy2(source, canonical)
                return selected
        if operation not in ("legacy-read", "changed-target-read"):
            return self._raw_probe(operation, label)
        before = self._raw_probe("snapshot", label + "-native-before")
        issues = loaded_issues(before, self.definition) + load_event_issues(before, before)
        if issues:
            raise AssertionError("Saved-read prerequisite is not loaded: " + "; ".join(issues))
        immediate = self._raw_probe(operation, label + "-native-initial")
        selected = self._wait_ready(label + "-native", immediate)
        record = self.observations[-1]
        # Select by loaded states and real ticks, never by the expected oracle verdict.
        selected_path = self.runtime / "advantage-works-results" / ("lifecycle-" + record["selected_label"] + ".json")
        canonical = self.runtime / "advantage-works-results" / ("lifecycle-" + label + ".json")
        if canonical.exists():
            raise FileExistsError(canonical)
        shutil.copy2(selected_path, canonical)
        record.update(operation=operation, canonical_label=label)
        self._record()
        return selected


def exercise(runner, label, progress, trace_loads=False):
    """Original sequence/fixtures/oracles, with native observation readiness gates."""
    definition = runner.definition
    runner.boot("lifecycle")
    require_native_boot(runner.runtime / "console-lifecycle.log")
    if trace_loads:
        runner.probe("trace-loads", "tracing-loads")
    runner.build()
    moved = runner.wait_moves()
    movements = {movement["id"]: movement for movement in moved["movements"]}
    if len(movements) != len(moved["movements"]):
        raise AssertionError("Duplicate movement witnesses")
    movement_results = {fixture["id"]: lifecycle.move_result(fixture, movements[fixture["id"]])
                        for fixture in definition if "from" in fixture}
    result = dict(status="RUNNING", move_cases=movement_results, chunk_cycles=0)
    progress(result)
    print(f"{label}: twelve finite-energy moves observed", flush=True)
    time.sleep(60)
    runner.processing("commissioned", 1000)
    for cycle in range(1, 4):
        runner.unload_reload(f"chunk-cycle-{cycle}")
        runner.replenish()
        runner.probe("offer", f"fluid-offer-{cycle}")
        time.sleep(60)
        runner.processing(f"chunk-resumed-{cycle}", (cycle + 1) * 1000)
        result["chunk_cycles"] = cycle
        progress(result)
        print(f"{label}: real chunk unload/reload {cycle} and resumed processing PASS", flush=True)
    before = runner.probe("snapshot", "before-restart")
    runner.command("save-all flush")
    runner.close()
    runner.boot("lifecycle-restart")
    require_native_boot(runner.runtime / "console-lifecycle-restart.log")
    time.sleep(1)
    after = runner.probe("snapshot", "after-restart")
    for fixture in definition:
        if "to" not in fixture:
            continue
        old = lifecycle.at(before, fixture["id"], fixture["to"])
        new = lifecycle.at(after, fixture["id"], fixture["to"])
        if not (lifecycle.contents(old) == lifecycle.contents(new) and old["block"] == new["block"]
                and "facing=" + fixture["facing"] in new["state"]):
            raise AssertionError("Moved drill changed across full restart: " + fixture["id"])
    runner.processing("restart-retained", 4000)
    runner.replenish()
    runner.probe("offer", "restart-fluid-offer")
    runner.mine()
    time.sleep(60)
    mined = runner.processing("restart-resumed", 5000)
    mining_results = runner.mining_results(mined)
    runner.unload_reload("mining-chunk-reload")
    time.sleep(5)
    mining_after = runner.mining_results(runner.probe("snapshot", "mining-retained"))
    result.update(mining=mining_results, mining_after_chunk_reload=mining_after)
    progress(result)
    midblock_results = runner.midblock()
    result["mid_block_progress"] = midblock_results
    progress(result)
    save_read_results = runner.save_read_guards()
    failed_changed = [name for name, row in save_read_results["changed_target_read"].items() if row["status"] != "PASS"]
    if failed_changed:
        result["changed_target_observation_gap"] = dict(cases=failed_changed, detail=
            "Strict oracle failed on the first loaded snapshot after an actual tick. Immediate/pending phases retained for retarget-race diagnosis; no verdict-based resampling.")
    runner.command("save-all flush")
    runner.close()
    groups = (movement_results, mining_results, mining_after, midblock_results["chunk_reload"],
              midblock_results["full_restart"], save_read_results["legacy_read"],
              save_read_results["changed_target_read"])
    result.update(status="PASS" if require_groups(definition, groups) else "FAIL", chunk_cycles=5,
                  full_restart="PASS", network_processing="PASS", fluid_mB=5000,
                  mining=mining_results, mining_after_chunk_reload=mining_after,
                  mid_block_progress=midblock_results, save_read_guards=save_read_results)
    progress(result)
    print(f"{label}: {result['status']}; movement, mining, chunk and restart oracles retained", flush=True)
    return result


def frozen_import(runner, source):
    # This is a separate frozen boot, before construction authority or live ticks.
    runner.boot("retained13-import")
    try:
        require_native_boot(runner.runtime / "console-retained13-import.log")
        runner.server.snapshot("retained13-import")
        runner.command("save-all flush")
        time.sleep(2)
    finally:
        runner.close()
    result = compare(source / "migration-restarted.json", runner.runtime / "migration-retained13-import.json",
                     native_normalized)
    write_json(runner.runtime / "native116-import-comparison.json", result)
    if result["status"] != "PASS":
        raise AssertionError("Frozen retained13-to-native116 preservation failed")
    return result


def run(args):
    if not 1 <= args.port <= 65535 or not args.java8.is_file():
        raise ValueError("Supply an available TCP port and an existing Java 8 executable")
    output = output_path(args.output)
    for protected in (args.source, args.source_manifest, args.source_hashes, args.target_manifest):
        require_disjoint(output, protected)
    source_before, history = verify_source(args)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    identities = {entry["id"] for entry in read_json(args.target_manifest)["target116"]["jars"]}
    if not {"steamadvantage", "electricadvantage", "basemetals"}.issubset(identities):
        raise ValueError("Full lifecycle requires native Steam, Electric and Base Metals")
    for protected in (target[0], *target[2]):
        require_disjoint(output, protected)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    definition = lifecycle.fixtures()
    helper_before = {path.name: digest(path) for path in (Path(lifecycle.__file__), Path(__file__))}
    output.mkdir(parents=True, exist_ok=False)
    report_path = output / "native116-lifecycle-results.json"
    report = dict(status="RUNNING", started_at_utc=datetime.now(timezone.utc).isoformat(),
                  scope="independent-native116-movement-aware-lifecycle",
                  protocol="Inherited LifecycleRun operations, unchanged fixtures/oracles; bounded native loaded/tick readiness; genuine chunk events; no source process",
                  source=str(args.source.resolve()), sourceKey=args.source_key,
                  sourceManifest=str(args.source_manifest.resolve()), sourceManifestSha256=digest(args.source_manifest),
                  sourceHashes=str(args.source_hashes.resolve()), sourceHashesSha256=digest(args.source_hashes),
                  sourceRuntimeBefore=source_before, sourceWorldExpected=read_json(args.source_hashes),
                  sourceStateGates=history, targetManifest=str(args.target_manifest.resolve()),
                  targetManifestSha256=digest(args.target_manifest), targetProfile=read_json(args.target_manifest)["target116"],
                  helperSha256=helper_before, port=args.port, profiles={})
    report["load_stack_tracing"] = getattr(args, "trace_loads", False)
    for path in (Path(lifecycle.__file__), Path(__file__)):
        archive = output / "protocol-sources" / path.name
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, archive)
    write_json(output / "lifecycle-fixture.json", definition)
    write_json(report_path, report)
    runner = None
    try:
        for name, source in (("fresh116", None), ("original110-chained113-native116", args.source)):
            runner = None
            result = dict(status="RUNNING")
            report["profiles"][name] = result
            write_json(report_path, report)
            try:
                runtime = output / name
                launcher = prepare(runtime, target, definition, args.port, source)
                runner = NativeLifecycleRun(runtime, args.java8, launcher, definition)
                if source is not None:
                    result["frozen_import"] = frozen_import(runner, source)
                authorize_lifecycle(runtime)

                def progress(value):
                    result.update(value)
                    write_json(report_path, report)

                exercise(runner, name, progress, trace_loads=getattr(args, "trace_loads", False))
            except Exception as error:
                result.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
                print(f"{name}: FAIL: {error}", flush=True)
            finally:
                if runner is not None:
                    result["native_readiness"] = dict(file="native116-readiness.json", stages=len(runner.observations))
                    try:
                        runner.close()
                    except Exception as error:
                        result.update(status="FAIL", shutdown_error=str(error))
                runtime = output / name
                if (runtime / "input-jars.json").is_file():
                    try:
                        verify_staged(runtime, target)
                        logs = sorted(runtime.glob("console-*.log"))
                        for log in logs:
                            require_native_boot(log)
                        result["native_boots"] = [log.name for log in logs]
                        result["forge_config_reload_notices"] = {
                            log.name: log.read_text(encoding="utf-8", errors="replace").count(
                                "[ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!")
                            for log in logs}
                    except Exception as error:
                        result.update(status="FAIL", runtime_audit_error=str(error))
                write_json(report_path, report)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["profiles"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        raise
    finally:
        if runner is not None:
            try:
                runner.close()
            except Exception as error:
                report.update(status="FAIL", shutdown_error=str(error))
        after = fingerprint(args.source.resolve())
        report["sourceRuntimeAfter"] = after
        report["source_fixture_unchanged"] = after == source_before
        report["sourceWorldAfter"] = fingerprint(args.source.resolve() / WORLD)
        if after != source_before:
            report.update(status="FAIL", source_error="Protected retained13 source changed")
        helper_after = {path.name: digest(path) for path in (Path(lifecycle.__file__), Path(__file__))}
        report["helpers_unchanged"] = helper_after == helper_before
        if helper_after != helper_before:
            report.update(status="FAIL", helper_error="Lifecycle code changed during execution")
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(report_path, report)
    print(report["status"] + ": " + str(report_path), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--source-key", default="target113")
    parser.add_argument("--source-hashes", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25580)
    parser.add_argument("--trace-loads", action="store_true",
                        help="Record native chunk-load caller stacks; does not change any lifecycle oracle or timeout")
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
