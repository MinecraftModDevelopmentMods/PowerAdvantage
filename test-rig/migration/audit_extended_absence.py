"""Offline amended-validator audit. Original native reports are never rewritten."""
import argparse
import ast
from collections import Counter
import json
import math
from pathlib import Path

import extended_lifecycle as current
from broad_result_audit import Evidence, VERSIONS, require, sha, staged_pins
from lifecycle import at, counts, parse


def old_partial(evidence, path, expected):
    data = evidence.read(path)
    require(sha(data) == expected, "Archived old helper hash mismatch")
    old_tree = ast.parse(data.decode("utf-8-sig"))
    new_path = Path(current.__file__)
    new_tree = ast.parse(evidence.read(new_path).decode("utf-8-sig"))
    def unchanged(tree):
        return ast.dump(ast.Module(body=[n for n in tree.body if not (
            isinstance(n, ast.FunctionDef) and n.name in ("partial_result", "continuous_absence"))], type_ignores=[]))
    require(unchanged(old_tree) == unchanged(new_tree), "Changes outside pure absence validators")
    function = next(n for n in old_tree.body if isinstance(n, ast.FunctionDef) and n.name == "partial_result")
    namespace = dict(vars(current))
    exec(compile(ast.Module(body=[function], type_ignores=[]), str(path), "exec"), namespace)
    return namespace["partial_result"]


def interval_proof(fixture, before, samples, group):
    """Independent event-vector proof; do not call the amended validator."""
    selected = [tuple(p) for p in fixture["groups"][group]]
    baseline = {tuple(c["pos"]): c for c in before["chunks"]}
    points = []
    for sample in samples:
        rows = {tuple(c["pos"]): c for c in sample["chunks"]}
        require(all(p in rows for p in selected), "Missing selected raw chunk")
        points.append(dict(tick=sample["worldTime"], absent=all(rows[p]["loaded"] is False
            and rows[p]["unloads"] > baseline[p]["unloads"] for p in selected),
            counters=[(rows[p]["loads"], rows[p]["unloads"]) for p in selected]))
    start = next((i for i, point in enumerate(points) if point["absent"]), None)
    ordered = all(type(point["tick"]) is int for point in points) and all(a["tick"] <= b["tick"] for a, b in zip(points, points[1:]))
    segment = points[start:] if start is not None else []
    event_stable = bool(segment) and all(point["absent"] and point["counters"] == segment[0]["counters"] for point in segment)
    span = segment[-1]["tick"] - segment[0]["tick"] if segment else 0
    return dict(status="PASS" if ordered and event_stable and span >= 40 else "FAIL",
        firstAbsentTick=segment[0]["tick"] if segment else None, confirmedTick=points[-1]["tick"],
        nativeTicks=span, lastAdjacentTicks=points[-1]["tick"]-points[-2]["tick"],
        observedEventCountsUnchanged=event_stable, selectedChunks=[list(p) for p in selected],
        anchorEventCounts=segment[0]["counters"] if segment else [], samples=len(points))


def compute(definition, snapshot, partial):
    result = dict(insufficient_energy=current.reserve_results(definition, snapshot("low-before"), snapshot("low-stalled")),
        moves=current.movement_results(definition, snapshot("step-4-complete")),
        blocked_output=current.output_results(definition, snapshot("blocked-output"), True),
        restart=current.restart_results(definition, snapshot("blocked-output"), snapshot("blocked-retained")),
        blocked_after_restart=current.output_results(definition, snapshot("blocked-retained"), True),
        resumed_output=current.output_results(definition, snapshot("freed-output"), False),
        redstone=current.redstone_results(definition, snapshot("redstone-before"), snapshot("redstone-held"), snapshot("redstone-resumed")),
        partial_networks=[], reload_orders=[], network_processing="PASS")
    for cycle, group in enumerate(("machine", "supply", "supply", "machine"), 1):
        label = f"partial-{cycle}-{group}"
        samples = [snapshot(f"{label}-sample-{n}") for n in range(8)]
        # This flag is runner metadata, not an edited native measurement.
        samples.append(dict(snapshot(label+"-confirmed"), confirmed=True))
        result["partial_networks"].append(dict(label=label, group=group,
            cases=partial(definition, snapshot(label+"-before"), samples, snapshot(label+"-loaded"), group)))
    for order in (("machine", "supply"), ("supply", "machine")):
        label="order-"+"-".join(order)
        result["reload_orders"].append(dict(label=label, order=list(order), cases=current.order_results(definition,
            snapshot(label+"-before"),snapshot(label+"-absent"),snapshot(label+"-first"),snapshot(label+"-settled"),snapshot(label+"-loaded"),order)))
    # Re-prove processing and conservation instead of trusting a summary PASS.
    for label, volume in [("network-commissioned",1000)]+[(f"partial-resumed-{n}",(n+1)*1000) for n in range(1,5)]+[
            ("order-machine-supply-resumed",5000),("order-supply-machine-resumed",5000)]:
        raw=snapshot(label)
        for fixture in definition:
            if fixture["id"] not in ("C-STEAM","C-ELECTRIC","C-FLUID"): continue
            machine=at(raw,fixture["id"],fixture["witnesses"][4])
            if fixture["id"]=="C-FLUID":
                tank=parse(machine).get("Tank",{})
                require(tank.get("FluidName")=="minecraft:water" and tank.get("Amount")==volume,"Raw fluid conservation failed: "+label)
            else:
                items=counts([machine]); energy=list(machine["energy"].values())
                require(items["minecraft:iron_ingot"]==4 and items["minecraft:iron_ore"]==0
                    and all(math.isfinite(v) for v in energy) and sum(energy)>0,"Raw processing/recovery failed: "+label)
    return result


def log_check(evidence, path, line):
    text=evidence.read(path).decode("utf-8",errors="replace")
    minecraft,forge=VERSIONS[line]
    require("Starting minecraft server version "+minecraft in text and "Forge mod loading, version "+forge+", for MC "+minecraft in text,
        "Missing native engine boot identity")
    require("Stopping server" in text and "Saving players" in text and "Saving worlds" in text,"Missing full native shutdown saves")
    for row in text.splitlines():
        require("/ERROR]" not in row and not any(marker in row for marker in (
            "NoSuchFieldError","NoSuchMethodError","Encountered an unexpected exception","Could not replace slot","Unknown item slot")),
            "Native error or failed command: "+row)
    if line=="113":
        from native113_diagnostics import audit_lines
        audit_lines(text.splitlines())
    elif line=="114":
        notice="[ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!"
        require(not any("/FATAL]" in row and not row.endswith(notice) for row in text.splitlines()),"Unclassified fatal native diagnostic")
    else:
        require("/FATAL]" not in text,"Fatal native diagnostic")


def audit(args):
    evidence=Evidence(); root=args.runtime.resolve();line=args.line
    report_path=root/("extended-results.json" if line=="113" else f"native{line}-holder-extended-results.json")
    original=evidence.json(report_path)
    manifest=args.manifest.resolve();manifest_data=evidence.read(manifest)
    pins=json.loads(manifest_data)["target"+line]
    if line=="113":
        custody=evidence.json(root/"native113-replay-audit.json")
        require(custody["status"]=="PASS" and custody["protocolUnchanged"] and custody["sourceUnchanged"],"Original 113 source custody failed")
        require(custody["targetManifestSha256"]==sha(manifest_data),"Original 113 manifest hash mismatch")
        expected=next(v for k,v in custody["protocolSha256"].items() if Path(k).name=="extended_lifecycle.py")
        require(evidence.json(root/"source-fingerprint-before.json")==evidence.json(root/"source-fingerprint-after.json"),"Protected source fingerprints differ")
        require({p["file"]:p["sha256"] for p in original["artifacts"]}=={Path(p["path"]).name:p["sha256"] for p in pins["jars"]},"Original artifact pins differ")
    else:
        require(original["helpers_unchanged"] and original["helpersBefore"]==original["helpersAfter"],"Original helper custody failed")
        require(original["targetManifestSha256"]==sha(manifest_data) and original["targetProfile"]==pins,"Original target pins differ")
        expected=original["helpersBefore"]["extended_lifecycle.py"]
        require(sha(evidence.read(root/"protocol-sources/extended_lifecycle.py"))==expected,"Archived native helper changed")
        require(original["sourceRuntimeBefore"]==original["sourceRuntimeAfter"],"Protected source runtime fingerprints differ")
    require(original["source_fixture_unchanged"] is True,"Protected source was modified")
    previous=old_partial(evidence,args.old_helper,expected)
    names=("fresh113","original110-chained113") if line=="113" else ("fresh"+line,"original110-chained113-native"+line)
    require(set(original["profiles"])==set(names),"Incomplete requested profile set")
    profiles={}; changed=[]
    minecraft,forge=VERSIONS[line]
    mods={row["id"]:row["version"] for row in pins["jars"]};mods.update(minecraft=minecraft,forge=forge)
    for name in names:
        runtime=root/name;summary=original["profiles"][name]
        staged_pins(evidence,runtime,pins,name)
        definition=evidence.json(runtime/"lifecycle-fixture.json")
        require(len(definition)==21 and len({f["id"] for f in definition})==21
            and sum("route" in f for f in definition)==18,"Incomplete native extended fixture")
        cache={}
        def snapshot(label):
            if label not in cache:
                row=evidence.json(runtime/"advantage-works-results"/("lifecycle-"+label+".json"))
                require(row["minecraft"]==minecraft and row["forge"]==forge and row["mods"]==mods,"Wrong raw engine/mod identity")
                cache[label]=row
            return cache[label]
        old=compute(definition,snapshot,previous)
        new=compute(definition,snapshot,current.partial_result)
        for key,value in old.items():
            require(value==summary[key],"Original verdict cannot be reproduced from raw measurements: "+name+"/"+key)
        require(current.qualification(old)==summary["status"],"Original profile status differs from raw proof")
        proofs=[]
        for first,second in zip(old["partial_networks"],new["partial_networks"]):
            label,group=first["label"],first["group"]
            samples=[snapshot(f"{label}-sample-{n}") for n in range(8)]+[snapshot(label+"-confirmed")]
            for fixture in definition:
                if "groups" not in fixture:continue
                proof=interval_proof(fixture,snapshot(label+"-before"),samples,group)
                case=second["cases"][fixture["id"]]
                require(case["stable_unload"]==(proof["status"]=="PASS"),"Amended verdict disagrees with independent event-vector proof")
                proofs.append(dict(label=label,case=fixture["id"],**proof))
                if first["cases"][fixture["id"]]!=case:
                    changed.append(dict(profile=name,label=label,case=fixture["id"],old=first["cases"][fixture["id"]],amended=case))
        logs=sorted(runtime.glob("console-*.log"))
        expected_logs={"console-extended.log","console-blocked-restart.log"}
        if line!="113" and name.startswith("original"):
            expected_logs.add("console-retained13-import.log")
        require({p.name for p in logs}==expected_logs,"Native operation/restart/import boot set incomplete or unexpected")
        for log in logs:log_check(evidence,log,line)
        profiles[name]=dict(originalQualification=summary["status"],amendedQualification=current.qualification(new),
            partialCases=12,drillRoutes=18,intervalProofs=proofs,nonPartialVerdictsUnchanged=all(old[k]==new[k] for k in old if k!="partial_networks"))
    require(original["status"]==("PASS" if all(p["originalQualification"]=="PASS" for p in profiles.values()) else "FAIL"),"Original top status masks a profile")
    evidence.unchanged()
    amended="PASS" if all(p["amendedQualification"]=="PASS" for p in profiles.values()) else "FAIL"
    return dict(status=amended,evidenceAuditStatus="PASS",scope="Independent raw replay under amended pure validator; NOT a new native world run",
        auditorSha256=sha(evidence.read(Path(__file__))),
        originalReport=str(report_path),originalReportSha256=sha(evidence.read(report_path)),originalNativeStatus=original["status"],
        oldHelperSha256=expected,newHelperSha256=sha(evidence.read(Path(current.__file__))),
        targetManifest=str(manifest),targetManifestSha256=sha(manifest_data),profiles=profiles,changedCases=changed,
        amendedQualification=amended,
        originalEvidenceUnchanged=True,evidenceSha256=evidence.hashes)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime",type=Path,required=True)
    parser.add_argument("--line",choices=("113","114","115","116"),required=True)
    parser.add_argument("--manifest",type=Path,required=True)
    parser.add_argument("--old-helper",type=Path,required=True)
    args=parser.parse_args()
    try:
        result=audit(args)
    except (AssertionError,OSError,ValueError,KeyError,TypeError) as error:
        result=dict(status="FAIL",error=str(error),runtime=str(args.runtime))
    print(json.dumps(result,indent=2,sort_keys=True))
    return 0 if result["status"]=="PASS" and result["amendedQualification"]=="PASS" else 1


if __name__=="__main__":
    raise SystemExit(main())
