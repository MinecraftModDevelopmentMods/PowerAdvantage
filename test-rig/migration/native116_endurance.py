"""Measured native116 endurance with historical and independent semantic checks."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import socket
import time
import traceback

import nbtlib

import endurance
import semantic_machine_audit
from audit_endurance import expected_inventory_acknowledgements
from matrix import WORLD, compare, digest, fingerprint, write_json
from native116 import native_normalized, output_path, pinned_profile, require_disjoint
from native116_lifecycle import (prepare, read_json, require_native_boot,
                                 verify_source, verify_staged)
from native116_format import world_info as native_world_info, worldgen_settings


NAMES = ("fresh116", "original110-chained113-native116")
FRESH_FIXTURE_SEED = 5919679243107101036
POI_POSITION = (-126, 5, 27)


def world_info(path):
    data = nbtlib.load(path)["Data"]
    return native_world_info(data.unpack(), allow_legacy=True)


def configure_new_fresh_fixture(runtime):
    endurance.require(not (runtime / WORLD).exists(), "Fresh isolation refuses an existing/imported world")
    properties = runtime / "server.properties"
    before = digest(properties)
    lines = [line for line in properties.read_text(encoding="utf-8").splitlines()
             if line.strip().partition("=")[0] not in ("generate-structures", "level-seed")]
    properties.write_text("\n".join(lines) + "\ngenerate-structures=false\n"
                          + f"level-seed={FRESH_FIXTURE_SEED}\n", encoding="utf-8")
    proof = runtime / "fresh-fixture-proof"
    proof.mkdir(exist_ok=False)
    shutil.copy2(properties, proof / "server-before-first-creation.properties")
    return dict(existing_world_absent=True, original_properties_sha256=before,
                configured_properties_sha256=digest(properties))


def verify_fresh_fixture_proof(runtime, proof):
    endurance.require(proof.get("existing_world_absent") is True, "Missing new-world isolation guard")
    for name, expected in proof["files"].items():
        endurance.require(digest(runtime / name) == expected, "Fresh isolation evidence changed: " + name)
    properties = dict(line.split("=", 1) for line in
                      (runtime / "fresh-fixture-proof/server-before-first-creation.properties")
                      .read_text(encoding="utf-8").splitlines() if line and not line.startswith("#") and "=" in line)
    endurance.require(properties.get("generate-structures") == "false"
                      and properties.get("level-seed") == str(FRESH_FIXTURE_SEED), "Wrong fresh creation settings")
    actual = world_info(runtime / "fresh-fixture-proof/worldinfo-before-build.dat")
    raw_settings = worldgen_settings(nbtlib.load(runtime / "fresh-fixture-proof/worldinfo-before-build.dat")["Data"].unpack())
    endurance.require(raw_settings == proof["native_worldgen_settings"], "Native WorldGenSettings proof changed")
    endurance.require(actual["MapFeatures"] == 0, "Actual WorldInfo MapFeatures is not zero")
    endurance.require(actual["RandomSeed"] == FRESH_FIXTURE_SEED, "Actual WorldInfo seed changed")
    endurance.require(actual == proof["actual_world_info"], "WorldInfo report differs from raw NBT")
    rows = read_json(runtime / "migration-fresh-isolation-before-build.json")
    endurance.require(len(rows) == 1 and tuple(rows[0][key] for key in ("x", "y", "z")) == POI_POSITION,
                      "Missing exact original composter coordinate")
    endurance.require(rows[0]["actualName"] == "minecraft:air", "Original composter coordinate is not AIR")
    endurance.require(rows[0] == proof["critical_block"], "Critical block report differs from native snapshot")
    return actual


def prove_fresh_fixture_before_build(rig, configuration):
    runtime = rig.runtime
    write_json(runtime / "migration-fixture.json", [dict(id="poi-target", x=POI_POSITION[0],
                                                        y=POI_POSITION[1], z=POI_POSITION[2])])
    rig.server.snapshot("fresh-isolation-before-build")
    rig.command("save-all flush", "Saved the game")
    saved = runtime / "fresh-fixture-proof/worldinfo-before-build.dat"
    shutil.copy2(runtime / WORLD / "level.dat", saved)
    files = ["fresh-fixture-proof/server-before-first-creation.properties",
             "fresh-fixture-proof/worldinfo-before-build.dat", "migration-fresh-isolation-before-build.json"]
    proof = dict(configuration, status="UNVERIFIED", actual_world_info=world_info(saved),
                 native_worldgen_settings=worldgen_settings(nbtlib.load(saved)["Data"].unpack()),
                 critical_block=read_json(runtime / files[-1])[0], files={p: digest(runtime / p) for p in files},
                 scope="NEW fresh world only, before any Works build; no imported world or POI data edits")
    path = runtime / "fresh-fixture-proof.json"
    write_json(path, proof)
    verify_fresh_fixture_proof(runtime, proof)
    proof["status"] = "PASS"
    write_json(path, proof)
    return proof


class NativeEnduranceRig(endurance.Rig):
    def batch(self, label):
        measured = semantic_machine_audit.endurance_batch(
            {station: self.documents[station] for station in ("S-04", "E-03", "W-04", "W-03")}, self.definitions)
        evidence = self.runtime / ("semantic-batch-" + label + ".json")
        with evidence.open("x", encoding="utf-8") as stream:
            json.dump(measured, stream, indent=2)
        endurance.require(measured["status"] == "PASS", "Independent batch measurements failed: " + str(measured))
        return super().batch(label)

    def boot(self, phase):
        from native116_code import verify_if_sealed
        verify_if_sealed(self.runtime)
        super().boot(phase)
        if phase != "endurance-restart":
            return
        # Restore only chunk tickets, never station actions or machine NBT.
        # Observing a real tick is transport readiness, not a desired output.
        for station in endurance.CHECKED:
            definition = self.definitions[station]
            low = self.pos(station, definition["boundsMin"])
            high = self.pos(station, definition["boundsMax"])
            self.command(f"forceload add {low[0]} {low[2]} {high[0]} {high[2]}")
        self.checkpoint("native-restart-immediate")
        initial = self.documents["W-01"]["worldTime"]
        started = time.monotonic()
        for attempt in range(200):
            self.checkpoint(f"native-restart-ready-{attempt:03}")
            observed = self.documents["W-01"]["worldTime"]
            if observed > initial:
                write_json(self.runtime / "native-restart-readiness.json", {
                    "initial_world_time": initial, "selected_world_time": observed,
                    "selected_attempt": attempt, "elapsed_seconds": time.monotonic() - started,
                    "selection": "First measured tick advance; no inventory/output inspection",
                })
                return
            if time.monotonic() - started >= 20:
                break
            time.sleep(0.01)
        raise TimeoutError("No actual native tick after restoring endurance tickets")


def audit(root, require_endurance=True):
    report = read_json(root / "native116-endurance-results.json")
    expected_status = "PASS" if require_endurance else "SMOKE_PASS_NOT_ENDURANCE"
    endurance.require(report["status"] == expected_status, "No completed native endurance result")
    endurance.require(report["interval_seconds"] == 60, "Not the one-minute batch protocol")
    endurance.require(report["duration_seconds"] >= (1800 if require_endurance else 60), "Duration too short")
    endurance.require(report["source_fixture_unchanged"] and report["helpers_unchanged"], "Source/protocol changed")
    endurance.require(set(report["profiles"]) == set(NAMES), "Missing native environment")
    endurance.require(report.get("fresh_fixture_proof", {}).get("status") == "PASS", "Missing fresh isolation proof")
    verify_fresh_fixture_proof(root / NAMES[0], report["fresh_fixture_proof"])
    endurance.require(report["copied_world_info_before_build"] == report["source_world_info"],
                      "Imported world generation settings changed")
    result = dict(status="PASS", scope="endurance" if require_endurance else "smoke-only", profiles={})
    failures = ("Unknown slot", "Could not replace", "Unknown or incomplete command", "Incorrect argument",
                "Unknown item", "There is no container")
    for name, profile in report["profiles"].items():
        endurance.require(profile["status"] == "PASS" and profile["restart"] == "PASS"
                          and profile["resumed_processing"] == "PASS", "Incomplete profile")
        endurance.require(profile["elapsed_seconds"] >= report["duration_seconds"], "Wall time too short")
        endurance.require(profile["game_ticks"] >= report["duration_seconds"] * 18, "Tick rate too low")
        runtime = root / name
        if name != NAMES[0]:
            endurance.require(world_info(runtime / WORLD / "level.dat") == report["source_world_info"],
                              "Saved imported world generation settings changed")
        counts = expected_inventory_acknowledgements(profile["endurance_batches"])
        for filename, count in zip(("console-endurance.log", "console-endurance-restart.log"), counts):
            text = (runtime / filename).read_text(encoding="utf-8")
            require_native_boot(runtime / filename)
            endurance.require(not any(f in text for f in failures), "Native command failure")
            endurance.require(text.count("Replaced a slot at") == count, "Inventory command acknowledgements differ")
            if "restart" in filename:
                endurance.require(not any(f"Started {s} " in text for s in ("S-04", "E-03", "E-02", "S-02", "E-01")),
                                  "Restart replaced source machines")
                endurance.require("Advantage Works built" not in text and "Reset 1 station" not in text,
                                  "Restart rebuilt the rig")
        pins = {item["file"]: item["sha256"] for item in report["artifacts"]}
        actual = {jar.name: digest(jar) for jar in (runtime / "mods").glob("*.jar")}
        endurance.require(actual == pins, "Staged native artifacts changed")
        ledger = read_json(runtime / "endurance-ledger.json")
        batches = [b for b in ledger if b["label"].startswith("cycle-")]
        total = report["duration_seconds"] // report["interval_seconds"]
        endurance.require(len(batches) == profile["endurance_batches"] == total, "Batch count differs")
        endurance.require({b["label"] for b in batches} == {f"cycle-{i:03}" for i in range(1, total + 1)},
                          "Repeated or missing batch")
        endurance.require([b["label"] for b in ledger[-3:]] == ["before-restart", "after-restart", "restart-resumed"],
                          "Missing restart checkpoints")
        result["profiles"][name] = dict(status="PASS", batches=total, inventory_acknowledgements=list(counts))
    return result


def run(args):
    endurance.require(args.seconds >= 60 and args.seconds % 60 == 0, "Use whole one-minute batches")
    output = output_path(args.output)
    for protected in (args.source, args.source_manifest, args.source_hashes, args.target_manifest):
        require_disjoint(output, protected)
    source_before, history = verify_source(args)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    for protected in (target[0], *target[2]):
        require_disjoint(output, protected)
    for port in (args.port, args.port + 1):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", port))
    definition_path = Path(__file__).parents[1] / "src/main/resources/assets/advantageworkstest/works/works-1.13.2.json"
    definitions = {s["id"]: s for s in read_json(definition_path)["stations"]}
    endurance.require(sum(bool(definitions[s].get("exclusive")) for s in endurance.ACTIVE) == 1,
                      "Exclusive stations interfere")
    helpers = [Path(endurance.__file__), Path(__file__), Path(semantic_machine_audit.__file__), definition_path]
    before_helpers = {str(p): digest(p) for p in helpers}
    output.mkdir(parents=True, exist_ok=False)
    for path in helpers:
        destination = output / "protocol-sources" / path.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
    report_path = output / "native116-endurance-results.json"
    report = dict(status="RUNNING", duration_seconds=args.seconds, interval_seconds=60,
                  feeding_protocol="IInventory replaceitem and fluid handlers; no inventory NBT writes",
                  source=str(args.source.resolve()), source_state_gates=history,
                  source_fingerprint=source_before, helper_sha256=before_helpers,
                  source_world_info=world_info(args.source / WORLD / "level.dat"),
                  fresh_fixture_policy=dict(seed=FRESH_FIXTURE_SEED, generate_structures=False,
                                            scope="NEW fresh116 world only; imported world settings preserved"),
                  target_manifest=str(args.target_manifest.resolve()), target_manifest_sha256=digest(args.target_manifest),
                  artifacts=[dict(file=j.name, sha256=digest(j)) for j in target[2]],
                  active_stations=endurance.ACTIVE, checked_stations=endurance.CHECKED, profiles={},
                  coverage="Rebuilt Works in fresh and migrated-copy worlds; not endurance of original source machinery",
                  manual_checks="Machine GUIs, weapons, sound and player interaction remain manual")
    write_json(report_path, report)
    rigs = []
    try:
        for index, name in enumerate(NAMES):
            runtime = output / name
            launcher = prepare(runtime, target, [], args.port + index, args.source if index else None)
            configuration = configure_new_fresh_fixture(runtime) if index == 0 else None
            for marker in ("migration-operate.allow", "advantage-works.allow"):
                (runtime / marker).touch()
            rig = NativeEnduranceRig(name, runtime, args.java8, launcher, definitions)
            rigs.append(rig)
            if index:
                rig.boot("frozen-import")
                rig.server.snapshot("frozen-import")
                rig.command("save-all flush")
                rig.shutdown()
                preserved = compare(args.source / "migration-restarted.json", runtime / "migration-frozen-import.json",
                                    native_normalized)
                write_json(runtime / "native116-import-comparison.json", preserved)
                endurance.require(preserved["status"] == "PASS", "Copied source did not preserve frozen state")
                require_native_boot(runtime / "console-frozen-import.log")
            rig.boot("endurance")
            if index == 0:
                report["fresh_fixture_proof"] = prove_fresh_fixture_before_build(rig, configuration)
            else:
                report["copied_world_info_before_build"] = world_info(runtime / WORLD / "level.dat")
                endurance.require(report["copied_world_info_before_build"] == report["source_world_info"],
                                  "Imported world generation settings changed")
            write_json(report_path, report)
            rig.start(build=True)
        initial_ticks = {r.name: r.documents["W-01"]["worldTime"] for r in rigs}
        started = time.monotonic()
        report["started_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(report_path, report)
        print(f"Measured {args.seconds}-second protocol started: " + report["started_at_utc"], flush=True)
        cycles = args.seconds // 60
        for cycle in range(1, cycles + 1):
            deadline = started + cycle * 60
            while time.monotonic() < deadline:
                for rig in rigs:
                    endurance.require(rig.server.process.poll() is None, rig.name + ": server died")
                time.sleep(min(1, max(0, deadline - time.monotonic())))
            for rig in rigs:
                rig.checkpoint(f"cycle-{cycle:03}")
                rig.batch(f"cycle-{cycle:03}")
                if cycle < cycles:
                    rig.seed()
                report["profiles"][rig.name] = dict(status="RUNNING", completed_batches=len(rig.ledger),
                                                   latest_world_time=rig.documents["W-01"]["worldTime"])
            write_json(report_path, report)
            print(f"{cycle * 60}s: both native profiles passed batch {cycle}", flush=True)
        elapsed = time.monotonic() - started
        ending_ticks = {r.name: r.documents["W-01"]["worldTime"] for r in rigs}
        for rig in rigs:
            endurance.require(ending_ticks[rig.name] - initial_ticks[rig.name] >= args.seconds * 18,
                              rig.name + ": fewer than 18 game ticks per second")
            rig.structure()
            rig.checkpoint("before-restart")
            rig.batch("before-restart")
            rig.command("save-all flush")
            rig.shutdown()
            rig.boot("endurance-restart")
            rig.checkpoint("after-restart")
            rig.batch("after-restart")
            rig.structure()
            rig.seed()
        time.sleep(60)
        for rig in rigs:
            rig.checkpoint("restart-resumed")
            rig.batch("restart-resumed")
            statuses = rig.structure()
            rig.command("save-all flush")
            rig.shutdown()
            totals = Counter()
            for batch in rig.ledger:
                if batch["label"].startswith("cycle-"):
                    totals.update(batch["outputs"])
            report["profiles"][rig.name] = dict(status="PASS", elapsed_seconds=elapsed,
                                               endurance_batches=cycles, total_verified_checkpoints=len(rig.ledger),
                                               station_statuses=statuses, restart="PASS", resumed_processing="PASS",
                                               game_ticks=ending_ticks[rig.name] - initial_ticks[rig.name],
                                               endurance_outputs=dict(totals))
        report["status"] = "PASS" if args.seconds >= 1800 else "SMOKE_PASS_NOT_ENDURANCE"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        raise
    finally:
        for rig in rigs:
            try:
                rig.shutdown()
                verify_staged(rig.runtime, target)
                for log in rig.runtime.glob("console-*.log"):
                    require_native_boot(log)
            except Exception as error:
                report.update(status="FAIL", shutdown_or_audit_error=str(error))
        report["source_fixture_unchanged"] = fingerprint(args.source.resolve()) == source_before
        report["helpers_unchanged"] = {str(p): digest(p) for p in helpers} == before_helpers
        if not report["source_fixture_unchanged"] or not report["helpers_unchanged"]:
            report.update(status="FAIL", provenance_error="Protected source or protocol changed")
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(report_path, report)
    endurance.require(report["status"] != "FAIL", "See saved native endurance failure")
    write_json(output / "qualification-audit.json", audit(output, require_endurance=args.seconds >= 1800))
    print(report["status"] + ": " + str(report_path), flush=True)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--source-key", default="target113")
    parser.add_argument("--source-hashes", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seconds", type=int, default=1800)
    parser.add_argument("--port", type=int, default=25590)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
