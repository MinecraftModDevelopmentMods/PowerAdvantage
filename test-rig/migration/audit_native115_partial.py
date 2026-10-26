"""Recompute native broader-network/drill verdicts without trusting run summaries."""

import argparse
import json
from pathlib import Path

from audit_pipe_only import bridge_result
import extended_lifecycle as historical
import pipe_only_lifecycle
import lifecycle
from lifecycle import (LifecycleRun, at, changed_target_results, contents, counts,
                       parse, progress_results)
from matrix import digest, fingerprint, write_json
from native115 import pinned_profile
from native115_geometry import require_geometry
from native115_lifecycle import (load_event_issues, require_native_boot,
                                 unload_event_issues, verify_staged)


def snapshot(runtime, label):
    value = json.loads((runtime / "advantage-works-results" / ("lifecycle-" + label + ".json")).read_text())
    require_geometry(value)
    return value


def geometry_evidence(runtime):
    paths = sorted((runtime / "advantage-works-results").glob("lifecycle-*.json"))
    if not paths:
        raise AssertionError("No raw native geometry observations")
    forced = 0
    for path in paths:
        value = json.loads(path.read_text())
        geometry = require_geometry(value)
        forced += geometry["observedForcedLevels"] == [31]
    if not forced:
        raise AssertionError("No observed live native forced ticket")
    return dict(snapshots=len(paths), forced_ticket_snapshots=forced,
                geometry=geometry)


def lifecycle_results(runtime, definition):
    reader = LifecycleRun(None, None, None, definition)
    reader.probe = lambda operation, label: snapshot(runtime, label)
    movements = snapshot(runtime, "commissioned")["movements"]
    recorded = {row["id"]: row for row in movements}
    if len(recorded) != len(movements):
        raise AssertionError("Duplicate raw movement witness")
    computed = dict(move_cases={f["id"]: lifecycle.move_result(f, recorded[f["id"]])
                               for f in definition if "from" in f})
    for label in [f"chunk-cycle-{n}" for n in range(1, 4)] + ["mining-chunk-reload", "midblock-chunk"]:
        before, absent, loaded = (snapshot(runtime, label + suffix)
                                  for suffix in ("-before", "-absent-confirmed", "-loaded"))
        lifecycle.require_unloaded(before, absent)
        if unload_event_issues(before, absent) or load_event_issues(before, loaded):
            raise AssertionError("Missing actual lifecycle callback: " + label)
        for fixture in definition:
            for pos in [fixture["to"]] if "to" in fixture else fixture["witnesses"]:
                old, new = at(before, fixture["id"], pos), at(loaded, fixture["id"], pos)
                if (old["block"] != new["block"] or old["state"] != new["state"]
                        or contents(old) != contents(new)):
                    raise AssertionError("Chunk reload changed block/state/items: " + label)
                if "tileIdentity" in old and old["tileIdentity"] == new.get("tileIdentity"):
                    raise AssertionError("Chunk reload reused old tile object: " + label)
                if "Tank" in parse(old) and parse(old)["Tank"] != parse(new).get("Tank"):
                    raise AssertionError("Chunk reload changed tank: " + label)
    for label, amount in [("commissioned", 1000), ("restart-retained", 4000),
                          ("restart-resumed", 5000)] + [(f"chunk-resumed-{n}", (n+1)*1000) for n in range(1, 4)]:
        reader.processing(label, amount)
    before, after = (snapshot(runtime, label) for label in ("before-restart", "after-restart"))
    for fixture in definition:
        if "to" not in fixture:
            continue
        old, new = at(before, fixture["id"], fixture["to"]), at(after, fixture["id"], fixture["to"])
        if (contents(old) != contents(new) or old["block"] != new["block"]
                or "facing=" + fixture["facing"] not in new["state"]):
            raise AssertionError("Moved drill changed across process restart")
    computed.update(mining=reader.mining_results(snapshot(runtime, "restart-resumed")),
        mining_after_chunk_reload=reader.mining_results(snapshot(runtime, "mining-retained")),
        mid_block_progress=dict(
            chunk_reload=progress_results(definition, snapshot(runtime, "midblock-before-chunk"), snapshot(runtime, "midblock-after-chunk")),
            full_restart=progress_results(definition, snapshot(runtime, "midblock-before-restart"), snapshot(runtime, "midblock-after-restart"))),
        save_read_guards=dict(
            legacy_read=progress_results(definition, snapshot(runtime, "save-read-before"), snapshot(runtime, "save-read-legacy")),
            changed_target_read=changed_target_results(definition, snapshot(runtime, "save-read-legacy"), snapshot(runtime, "save-read-changed"))))
    groups = [computed["move_cases"], computed["mining"], computed["mining_after_chunk_reload"],
              *computed["mid_block_progress"].values(), *computed["save_read_guards"].values()]
    if any(len(group) != 12 for group in groups):
        raise AssertionError("Incomplete twelve-route lifecycle group")
    qualification = "PASS" if all(row["status"] == "PASS" for group in groups for row in group.values()) else "FAIL"
    computed.update(chunk_cycles=5, full_restart="PASS", network_processing="PASS", fluid_mB=5000)
    return computed, qualification, groups


def partial_cases(runtime, definition, cycles):
    results = []
    for cycle, group in enumerate(cycles, 1):
        label = f"partial-{cycle}-{group}"
        samples = [snapshot(runtime, label + f"-sample-{n}") for n in range(8)]
        confirmed = snapshot(runtime, label + "-confirmed")
        confirmed["confirmed"] = True
        results.append(dict(label=label, group=group, cases=historical.partial_result(
            definition, snapshot(runtime, label + "-before"), samples + [confirmed],
            snapshot(runtime, label + "-loaded"), group)))
    return results


def order_cases(runtime, definition):
    results = []
    for order in (("machine", "supply"), ("supply", "machine")):
        label = "order-" + "-".join(order)
        results.append(dict(label=label, order=list(order), cases=historical.order_results(
            definition, *(snapshot(runtime, label + "-" + phase) for phase in
                          ("before", "absent", "first", "settled", "loaded")), order)))
    return results


def check_processing(runtime, definition):
    for label, quantity in [("network-commissioned", 1000)] + [
            (f"partial-resumed-{n}", (n + 1) * 1000) for n in range(1, 5)] + [
            ("order-machine-supply-resumed", 5000), ("order-supply-machine-resumed", 5000)]:
        state = snapshot(runtime, label)
        for f in definition:
            if "groups" not in f:
                continue
            endpoint = at(state, f["id"], f["witnesses"][4])
            if f["id"] == "C-FLUID":
                tank = parse(endpoint).get("Tank", {})
                if tank.get("FluidName") != "minecraft:water" or tank.get("Amount") != quantity:
                    raise AssertionError("Fluid conservation: " + label)
            else:
                items = counts([endpoint])
                if items["minecraft:iron_ingot"] != 4 or items["minecraft:iron_ore"] != 0 or sum(endpoint["energy"].values()) <= 0:
                    raise AssertionError("Network processing: " + label)


def check_canonical(runtime):
    path = runtime / "native115-readiness.json"
    records = json.loads(path.read_text()) if path.is_file() else []
    checked = 0
    for record in records:
        if "canonical_label" not in record:
            continue
        source = runtime / "advantage-works-results" / ("lifecycle-" + record["selected_label"] + ".json")
        canonical = runtime / "advantage-works-results" / ("lifecycle-" + record["canonical_label"] + ".json")
        if source.read_bytes() != canonical.read_bytes():
            raise AssertionError("Canonical snapshot differs from actual raw bytes: " + canonical.name)
        checked += 1
    return checked


def audit(root):
    files = list(root.glob("native115-*-results.json"))
    if len(files) != 1:
        raise AssertionError("Specify one native campaign root")
    report = json.loads(files[0].read_text())
    campaign = report.get("campaign", "lifecycle" if files[0].name == "native115-lifecycle-results.json" else "unknown")
    if not report.get("source_fixture_unchanged") or not report.get("helpers_unchanged"):
        raise AssertionError("Protected source/helper changed")
    if fingerprint(Path(report["source"])) != report["sourceRuntimeBefore"]:
        raise AssertionError("Protected source no longer matches recorded fingerprint")
    if digest(Path(report["targetManifest"])) != report["targetManifestSha256"]:
        raise AssertionError("Target manifest changed")
    helpers = report.get("helpersBefore", report.get("helperSha256", {}))
    if not helpers:
        raise AssertionError("Missing archived protocol fingerprints")
    for name, checksum in helpers.items():
        if digest(root / "protocol-sources" / name) != checksum:
            raise AssertionError("Archived protocol differs: " + name)
    target = pinned_profile(Path(report["targetManifest"]), "target115")
    if set(report["profiles"]) != {"fresh115", "original110-chained113-native115"}:
        raise AssertionError("Incomplete profile set")
    definition = json.loads((root / "lifecycle-fixture.json").read_text())
    kind = campaign.removeprefix("halo-").removeprefix("holder-")
    expected = (lifecycle.fixtures() if kind == "lifecycle" else historical.fixtures() if kind in ("extended", "extended-reads") else
                pipe_only_lifecycle.fixtures() if kind == "bridge" else
                [f for f in historical.fixtures() if "groups" in f])
    if campaign.startswith("halo-"):
        from native115_ticket_networks import bridge_fixtures, extended_fixtures, linear_fixtures
        expected = {"partial": linear_fixtures, "bridge": bridge_fixtures, "extended": extended_fixtures}[kind]()
    if campaign.startswith("holder-"):
        from native115_holder_networks import bridge_fixtures, extended_fixtures, linear_fixtures
        expected = {"partial": linear_fixtures, "bridge": bridge_fixtures, "extended": extended_fixtures}[kind]()
    if definition != json.loads(json.dumps(expected)):
        raise AssertionError("Historical fixture geometry differs")
    result = dict(evidence_audit="PASS", qualification="PASS", campaign=campaign, profiles={})
    for name, summary in report["profiles"].items():
        runtime = root / name
        details = result["profiles"][name] = dict(evidence_audit="PASS", failures=[], machine_qualification="FAIL")
        try:
            verify_staged(runtime, target)
            if json.loads((runtime / "lifecycle-fixture.json").read_text()) != definition:
                raise AssertionError("Runtime fixture geometry changed")
            details["raw_canonical_pairs"] = check_canonical(runtime)
            details["native_geometry"] = geometry_evidence(runtime)
            if campaign.startswith("halo-"):
                if not summary.get("native_ticket_protocol_unchanged") or digest(
                        runtime / "native115-ticket-network-protocol.py") != summary["native_ticket_protocol_sha256"]:
                    raise AssertionError("Native ticket-control protocol changed")
            if campaign.startswith("holder-"):
                if not summary.get("holder_protocol_unchanged") or digest(runtime / "native115_holder_networks.py") != summary["holder_protocol_sha256"] \
                        or digest(runtime / "native115_ticket_networks.py") != summary["holder_transport_sha256"]:
                    raise AssertionError("Native holder-control protocol changed")
            if kind == "lifecycle":
                computed, qualification, groups = lifecycle_results(runtime, definition)
                details["drill_groups"] = [{"PASS": sum(c["status"] == "PASS" for c in group.values()),
                                           "FAIL": sum(c["status"] != "PASS" for c in group.values())} for group in groups]
                details["real_chunk_cycles"] = 5
            elif kind == "extended":
                early = dict(insufficient_energy=historical.reserve_results(
                    definition, snapshot(runtime, "low-before"), snapshot(runtime, "low-stalled")),
                    moves=historical.movement_results(definition, snapshot(runtime, "step-4-complete")))
                details["completed_early_drill_groups"] = {
                    key: {"PASS": sum(c["status"] == "PASS" for c in group.values()),
                          "FAIL": sum(c["status"] != "PASS" for c in group.values())}
                    for key, group in early.items()}
                details["native_moves"] = len(snapshot(runtime, "step-4-complete")["movements"])
                for key, value in early.items():
                    if summary.get(key) != value:
                        raise AssertionError("Early verdict differs from raw evidence: " + key)
                computed = dict(
                    insufficient_energy=historical.reserve_results(definition, snapshot(runtime, "low-before"), snapshot(runtime, "low-stalled")),
                    moves=historical.movement_results(definition, snapshot(runtime, "step-4-complete")),
                    partial_networks=partial_cases(runtime, definition, ("machine", "supply", "supply", "machine")),
                    reload_orders=order_cases(runtime, definition),
                    blocked_output=historical.output_results(definition, snapshot(runtime, "blocked-output"), True),
                    restart=historical.restart_results(definition, snapshot(runtime, "blocked-output"), snapshot(runtime, "blocked-retained")),
                    blocked_after_restart=historical.output_results(definition, snapshot(runtime, "blocked-retained"), True),
                    resumed_output=historical.output_results(definition, snapshot(runtime, "freed-output"), False),
                    redstone=historical.redstone_results(definition, *(snapshot(runtime, label) for label in
                        ("redstone-before", "redstone-held", "redstone-resumed"))), network_processing="PASS")
                check_processing(runtime, definition)
                qualification = historical.qualification(computed)
                details["native_moves"] = len(snapshot(runtime, "step-4-complete")["movements"])
                details["drill_groups"] = {key: {"PASS": sum(c["status"] == "PASS" for c in computed[key].values()),
                                                  "FAIL": sum(c["status"] != "PASS" for c in computed[key].values())}
                                          for key in ("insufficient_energy", "moves", "blocked_output", "restart",
                                                      "blocked_after_restart", "resumed_output", "redstone")}
            elif kind == "extended-reads":
                reader = LifecycleRun(None, None, None, definition)
                computed = dict(moves=historical.movement_results(definition, snapshot(runtime, "step-4-complete")),
                    mining=reader.mining_results(snapshot(runtime, "reads-mining-complete")),
                    mid_block_progress=dict(
                        chunk_reload=progress_results(definition, snapshot(runtime, "midblock-before-chunk"), snapshot(runtime, "midblock-after-chunk")),
                        full_restart=progress_results(definition, snapshot(runtime, "midblock-before-restart"), snapshot(runtime, "midblock-after-restart"))),
                    saved_read_guards=dict(
                        legacy_read=progress_results(definition, snapshot(runtime, "save-read-before"), snapshot(runtime, "save-read-legacy")),
                        changed_target_read=changed_target_results(definition, snapshot(runtime, "save-read-legacy"), snapshot(runtime, "save-read-changed"))))
                groups = [computed["moves"], computed["mining"], *computed["mid_block_progress"].values(),
                          *computed["saved_read_guards"].values()]
                qualification = "PASS" if all(c["status"] == "PASS" for group in groups for c in group.values()) else "FAIL"
                for f in definition:
                    if "route" in f and contents(at(snapshot(runtime, "save-read-before"), f["id"], f["to"])) != contents(
                            at(snapshot(runtime, "save-read-changed"), f["id"], f["to"])):
                        raise AssertionError("Saved-read item conservation")
                if not summary.get("own_protocol_unchanged") or digest(runtime / "native115-extended-reads-protocol.py") != summary["protocolSha256"]:
                    raise AssertionError("Extra saved-read protocol changed")
                details["native_moves"] = len(snapshot(runtime, "step-4-complete")["movements"])
            elif kind in ("partial", "empty-control"):
                computed = dict(partial_networks=partial_cases(runtime, definition, ("machine", "supply")),
                                reload_orders=order_cases(runtime, definition))
                qualification = "PASS" if all(c["status"] == "PASS" for key in computed for row in computed[key]
                                                for c in row["cases"].values()) else "FAIL"
            elif kind == "bridge":
                computed = dict(cycles=[dict(cycle=cycle, **bridge_result(definition, *(snapshot(runtime,
                    f"bridge-{cycle}-" + phase) for phase in ("before", "isolated", "confirmed", "loaded", "resumed")), cycle))
                                       for cycle in range(3)])
                qualification = "PASS" if all(c["status"] == "PASS" for c in computed["cycles"]) else "FAIL"
            else:
                raise ValueError("Unknown native campaign")
            for key, value in computed.items():
                if summary.get(key) != value:
                    raise AssertionError("Summary differs from unchanged raw oracle: " + key)
            if summary["status"] != qualification:
                raise AssertionError("Reported machine status differs from raw evidence")
            details["machine_qualification"] = qualification
        except (AssertionError, FileNotFoundError, KeyError, ValueError) as error:
            details.update(evidence_audit="FAIL", failures=[str(error)])
        logs = sorted(runtime.glob("console-*.log"))
        details["boots"] = [log.name for log in logs]
        details["timing_warnings"] = {
            log.name: [line for line in log.read_text(encoding="utf-8", errors="replace").splitlines()
                       if "Can't keep up!" in line] for log in logs}
        for log in logs:
            try:
                require_native_boot(log)
                text = log.read_text(encoding="utf-8", errors="replace")
                if "Stopping server" not in text:
                    raise AssertionError("No graceful stop: " + log.name)
                if "An unexpected error occurred trying to execute that command" in text:
                    raise AssertionError("Native command exception retained: " + log.name)
            except AssertionError as error:
                details["failures"].append(str(error))
        if details["evidence_audit"] != "PASS":
            result["evidence_audit"] = "FAIL"
        if details["machine_qualification"] != "PASS" or details["failures"]:
            result["qualification"] = "FAIL"
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = audit(args.run)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
    raise SystemExit(int(result["evidence_audit"] != "PASS" or result["qualification"] != "PASS"))
