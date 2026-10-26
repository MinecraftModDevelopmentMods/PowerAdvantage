"""Native 1.15 transport for unchanged partial-network and pipe-bridge oracles."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import socket
import time
import traceback

from audit_pipe_only import bridge_result
import extended_lifecycle as extended
import pipe_only_lifecycle as bridge
from lifecycle import at, counts, parse
from matrix import digest, fingerprint, write_json
from native115 import output_path, pinned_profile, require_disjoint
from native115_lifecycle import (NativeLifecycleRun, authorize_lifecycle, frozen_import,
                                 prepare, read_json, require_native_boot, verify_source,
                                 verify_staged)


def selected_chunks(definition, group=None):
    if group is None:
        return {(x, z) for f in definition
                for x in range(f["min"][0] >> 4, (f["max"][0] >> 4) + 1)
                for z in range(f["min"][2] >> 4, (f["max"][2] >> 4) + 1)}
    return {tuple(p) for f in definition for p in f.get("groups", {}).get(group, [])}


def subset_load_issues(before, current, selected):
    old = {tuple(c["pos"]): c for c in before["chunks"]}
    chunks = {tuple(c["pos"]): c for c in current["chunks"]}
    events = {tuple(e["pos"]) for e in current.get("chunkEvents", []) if e["operation"] == "load"}
    errors = []
    for pos in selected:
        a, b = old.get(pos), chunks.get(pos)
        if a is None or b is None or not b["loaded"]:
            errors.append("selected chunk not loaded: " + str(pos))
        elif pos not in events or b["loads"] <= 0 or (not a["loaded"] and b["loads"] <= a["loads"]):
            errors.append("missing actual Chunk.Load: " + str(pos))
    for rows in current["cases"].values():
        for row in rows:
            if (row["pos"][0] >> 4, row["pos"][2] >> 4) in selected and not row.get("loaded"):
                errors.append("selected witness not loaded: " + str(row["pos"]))
    return errors


def subset_unload_pending(before, current, selected):
    old = {tuple(c["pos"]): c for c in before["chunks"]}
    chunks = {tuple(c["pos"]): c for c in current["chunks"]}
    events = {tuple(e["pos"]) for e in current.get("chunkEvents", []) if e["operation"] == "unload"}
    absent = all(pos in chunks and not chunks[pos]["loaded"] for pos in selected)
    pending = [str(pos) for pos in selected if pos not in old or pos not in chunks
               or pos not in events or chunks[pos]["unloads"] <= old[pos]["unloads"]]
    return absent, pending


class NativePartialRun(NativeLifecycleRun):
    """Select actual snapshots by chunk callbacks/ticks, never by machine results."""

    def __init__(self, *args):
        super().__init__(*args)
        self._released_groups = {}

    def _canonical(self, selected, label):
        root = self.runtime / "advantage-works-results"
        destination = root / ("lifecycle-" + label + ".json")
        if destination.exists():
            raise FileExistsError(destination)
        shutil.copy2(root / ("lifecycle-" + selected + ".json"), destination)

    def _load_ready(self, operation, label):
        selected = selected_chunks(self.definition, None if operation == "load" else operation[5:])
        if not selected:
            raise ValueError("Unknown fixture chunk group: " + operation)
        before = self._raw_probe("snapshot", label + "-native-before")
        current_label = label + "-native-initial"
        current = self._raw_probe(operation, current_label)
        initial_tick, loaded_tick, started = current["worldTime"], None, time.monotonic()
        record = dict(label=label, phase="native-subset-load-readiness", operation=operation,
                      selected_chunks=sorted(selected), samples=[], max_seconds=20, max_ticks=160)
        self.observations.append(record)
        try:
            for attempt in range(200):
                errors = subset_load_issues(before, current, selected)
                tick = current["worldTime"]
                if tick < initial_tick:
                    raise AssertionError("Native worldTime moved backwards")
                loaded_tick = None if errors else (tick if loaded_tick is None else loaded_tick)
                record["samples"].append(dict(label=current_label, world_time=tick, issues=errors,
                                               loaded_since_world_time=loaded_tick))
                if loaded_tick is not None and tick - loaded_tick >= 1:
                    self._canonical(current_label, label)
                    record.update(status="PASS", selected_label=current_label, canonical_label=label)
                    self._record()
                    return current
                if time.monotonic() - started >= 20 or tick - initial_tick >= 160 or attempt == 199:
                    raise TimeoutError("Native selected chunks did not load and advance a real tick: " + label)
                self._record()
                time.sleep(0.01)
                current_label = label + f"-native-pending-{attempt + 1:03}"
                current = self._raw_probe("snapshot", current_label)
        except BaseException as error:
            record.update(status="FAIL", failure=str(error))
            self._record()
            raise

    def _snapshot_ready(self, operation, label):
        current_label = label + "-native-initial"
        current = self._raw_probe(operation, current_label)
        record = dict(label=label, phase="native-subset-unload-callback-readiness", samples=[])
        self.observations.append(record)
        for attempt in range(45):
            pending_groups = []
            for group, context in self._released_groups.items():
                absent, pending = subset_unload_pending(context["before"], current, context["selected"])
                if absent and pending and time.monotonic() < context["deadline"]:
                    pending_groups.append(group)
            record["samples"].append(dict(label=current_label, world_time=current["worldTime"],
                                           pending_groups=pending_groups))
            if not pending_groups or attempt == 44:
                break
            self._record()
            deadline = min(self._released_groups[g]["deadline"] for g in pending_groups)
            time.sleep(min(1, max(0, deadline - time.monotonic())))
            current_label = label + f"-native-pending-{attempt + 1:02}"
            current = self._raw_probe("snapshot", current_label)
        self._canonical(current_label, label)
        record.update(status="OBSERVED", selected_label=current_label, canonical_label=label)
        self._record()
        return current

    def probe(self, operation, label):
        if operation == "load" or operation.startswith("load-"):
            result = self._load_ready(operation, label)
            if operation == "load":
                self._released_groups.clear()
            else:
                self._released_groups.pop(operation[5:], None)
            return result
        if operation.startswith("release-"):
            group = operation[8:]
            selected = selected_chunks(self.definition, group)
            if not selected:
                raise ValueError("Unknown fixture chunk group: " + group)
            before = self._raw_probe("snapshot", label + "-native-before")
            result = self._raw_probe(operation, label)
            self._released_groups[group] = dict(before=before, selected=selected,
                                                deadline=time.monotonic() + 45)
            return result
        if self._released_groups and operation in ("snapshot", "isolated"):
            return self._snapshot_ready(operation, label)
        return super().probe(operation, label)

    def boot(self, label):
        super().boot(label)
        require_native_boot(self.runtime / f"console-{label}.log")
        if label == "blocked-restart":
            self.probe("load", "blocked-restart-native-restored")


class NativeExtendedRun(extended.ExtendedRun, NativePartialRun):
    pass


def orders(runner, process=False):
    results = []
    for order in (("machine", "supply"), ("supply", "machine")):
        label = "order-" + "-".join(order)
        before = runner.probe("snapshot", label + "-before")
        runner.probe("release-supply", label + "-release-supply")
        runner.probe("release-machine", label + "-release-machine")
        time.sleep(5)
        absent = runner.probe("snapshot", label + "-absent")
        first = runner.probe("load-" + order[0], label + "-first")
        time.sleep(2)
        settled = runner.probe("snapshot", label + "-settled")
        loaded = runner.probe("load-" + order[1], label + "-loaded")
        results.append(dict(label=label, order=list(order), cases=extended.order_results(
            runner.definition, before, absent, first, settled, loaded, order)))
        if process:
            runner.replenish()
            time.sleep(50)
            runner.processing(label + "-resumed", 5000)
    return results


def exercise_partial(runner, progress):
    runner.boot("partial-trace")
    runner.build()
    time.sleep(50)
    runner.processing("commissioned", 1000)
    runner.probe("trace-loads", "trace-enabled")
    result = dict(status="RUNNING", partial_networks=[], reload_orders=[])
    for cycle, group in enumerate(("machine", "supply"), 1):
        result["partial_networks"].append(runner.partial(group, cycle))
        progress(result)
    result["reload_orders"] = orders(runner)
    result["status"] = "PASS" if all(c["status"] == "PASS" for r in
        result["partial_networks"] + result["reload_orders"] for c in r["cases"].values()) else "FAIL"
    runner.command("save-all flush")
    runner.close()
    progress(result)
    return result


def exercise_bridge(runner, progress):
    runner.boot("pipe-only")
    runner.probe("load", "loaded")
    for f in runner.definition:
        runner.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
        fluid, steam = f["id"] == "C-FLUID", f["id"] == "C-STEAM"
        pipe = "poweradvantage:fluid_pipe" if fluid else "steamadvantage:steam_pipe" if steam else "electricadvantage:electric_conduit"
        runner.block(f["witnesses"][0], pipe if fluid else "poweradvantage:infinite_" + ("steam" if steam else "electricity"))
        for pos in f["pipes"]:
            runner.block(pos, pipe)
        runner.block(f["witnesses"][1], "poweradvantage:fluid_metal_tank" if fluid else
                     "steamadvantage:steam_furnace" if steam else "electricadvantage:electric_furnace")
        if steam:
            runner.inventory(f["witnesses"][1], 0, "minecraft:coal", 64)
    runner.probe("trace-loads", "trace-enabled")
    time.sleep(2)
    runner.probe("snapshot", "commissioned")
    result = dict(status="RUNNING", cycles=[])
    for cycle in range(3):
        before = runner.probe("snapshot", f"bridge-{cycle}-before")
        runner.command("save-all flush")
        runner.probe("release-bridge", f"bridge-{cycle}-released")
        time.sleep(3)
        absent = runner.probe("isolated", f"bridge-{cycle}-isolated")
        runner.probe("offer-blocked", f"bridge-{cycle}-blocked-offer")
        time.sleep(3)
        confirmed = runner.probe("snapshot", f"bridge-{cycle}-confirmed")
        loaded = runner.probe("load-bridge", f"bridge-{cycle}-loaded")
        runner.probe("offer", f"bridge-{cycle}-reconnected-offer")
        for f in runner.definition:
            if f["id"] == "C-FLUID":
                continue
            machine, steam = f["witnesses"][1], f["id"] == "C-STEAM"
            for slot in range(1 if steam else 0, 7 if steam else 10):
                runner.inventory(machine, slot, "minecraft:air", 1)
            runner.inventory(machine, 1 if steam else 0, "minecraft:iron_ore", 4)
        time.sleep(50)
        resumed = runner.probe("snapshot", f"bridge-{cycle}-resumed")
        verdict = bridge_result(runner.definition, before, absent, confirmed, loaded, resumed, cycle)
        result["cycles"].append(dict(cycle=cycle, **verdict))
        progress(result)
        print(f"{runner.runtime.name}: bridge cycle {cycle + 1}: {verdict['status']}", flush=True)
    result["status"] = "PASS" if all(c["status"] == "PASS" for c in result["cycles"]) else "FAIL"
    runner.command("save-all flush")
    runner.close()
    progress(result)
    return result


def common_parser(description):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--source-key", default="target113")
    parser.add_argument("--source-hashes", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True, choices=(25580, 25581, 25582, 25583))
    return parser


def campaign(args, name, definition, exercise, runner_type=NativeExtendedRun):
    output = output_path(args.output)
    for protected in (args.source, args.source_manifest, args.source_hashes, args.target_manifest):
        require_disjoint(output, protected)
    if not args.java8.is_file():
        raise FileNotFoundError(args.java8)
    source_before, history = verify_source(args)
    target = pinned_profile(args.target_manifest.resolve(), "target115")
    for protected in (target[0], *target[2]):
        require_disjoint(output, protected)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    output.mkdir(parents=True)
    report_path = output / ("native115-" + name + "-results.json")
    helpers = [Path(__file__), Path(extended.__file__), Path(bridge.__file__),
               Path(__file__).with_name("lifecycle.py"), Path(__file__).with_name("native115_lifecycle.py"),
               Path(__file__).with_name("native115_extended.py")]
    hashes = {path.name: digest(path) for path in helpers if path.is_file()}
    report = dict(status="RUNNING", campaign=name, started_at_utc=datetime.now(timezone.utc).isoformat(),
                  scope="native115 fresh and hash-protected retained13 copy; unchanged historical semantic oracles",
                  source=str(args.source.resolve()), sourceRuntimeBefore=source_before, sourceStateGates=history,
                  sourceManifestSha256=digest(args.source_manifest), sourceHashesSha256=digest(args.source_hashes),
                  targetManifest=str(args.target_manifest.resolve()), targetManifestSha256=digest(args.target_manifest),
                  targetProfile=read_json(args.target_manifest)["target115"], helpersBefore=hashes, port=args.port, profiles={})
    for path in helpers:
        if path.is_file():
            archive = output / "protocol-sources" / path.name
            archive.parent.mkdir(exist_ok=True)
            shutil.copy2(path, archive)
    write_json(output / "lifecycle-fixture.json", definition)
    write_json(report_path, report)
    try:
        for label, source in (("fresh115", None), ("original110-chained113-native115", args.source)):
            runtime, runner = output / label, None
            result = report["profiles"][label] = dict(status="RUNNING")
            write_json(report_path, report)
            try:
                launcher = prepare(runtime, target, definition, args.port, source)
                runner = runner_type(runtime, args.java8, launcher, definition)
                if source is not None:
                    result["frozen_import"] = frozen_import(runner, source)
                authorize_lifecycle(runtime)
                def progress(value):
                    result.update(value)
                    write_json(report_path, report)
                exercise(runner, progress)
            except BaseException as error:
                result.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
                print(label + ": FAIL: " + str(error), flush=True)
            finally:
                if runner is not None:
                    try:
                        runner.close()
                    except BaseException as error:
                        result.update(status="FAIL", shutdown_error=str(error))
                if (runtime / "input-jars.json").is_file():
                    try:
                        verify_staged(runtime, target)
                        logs = sorted(runtime.glob("console-*.log"))
                        for log in logs:
                            require_native_boot(log)
                            text = log.read_text(encoding="utf-8", errors="replace")
                            if "Stopping server" not in text:
                                raise AssertionError("Missing graceful shutdown: " + log.name)
                        result["native_boots"] = [log.name for log in logs]
                        result["keep_up_warnings"] = {log.name: log.read_text(encoding="utf-8").count("Can't keep up!") for log in logs}
                    except BaseException as error:
                        result.update(status="FAIL", runtime_audit_error=str(error))
                write_json(report_path, report)
        report["status"] = "PASS" if all(p["status"] == "PASS" for p in report["profiles"].values()) else "FAIL"
    finally:
        report["sourceRuntimeAfter"] = fingerprint(args.source.resolve())
        report["source_fixture_unchanged"] = report["sourceRuntimeAfter"] == source_before
        report["helpersAfter"] = {path.name: digest(path) for path in helpers if path.is_file()}
        report["helpers_unchanged"] = hashes == report["helpersAfter"]
        if not report["source_fixture_unchanged"] or not report["helpers_unchanged"]:
            report["status"] = "FAIL"
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(report_path, report)
    print(report["status"] + ": " + str(report_path), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = common_parser(__doc__)
    parser.add_argument("--campaign", choices=("partial", "bridge"), required=True)
    args = parser.parse_args()
    definition = [f for f in extended.fixtures() if "groups" in f] if args.campaign == "partial" else bridge.fixtures()
    return campaign(args, args.campaign, definition, exercise_partial if args.campaign == "partial" else exercise_bridge)


if __name__ == "__main__":
    raise SystemExit(main())
