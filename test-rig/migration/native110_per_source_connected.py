"""Explicit source-matched 1.10 connected matrix; no overlays or auditor edits.

Each destination has a full sealed manifest. Base24 diagnostics require the
separate four-boot proof; Beta4 retains the ordinary strict boot auditor.
A singleton is PASS_ROUTE only. Only all three sources and six boots can qualify
this matrix, and the ordinary work/resource oracles are rerun on raw documents.
"""
import argparse
from datetime import datetime, timezone
import importlib
import json
from pathlib import Path
import socket
import traceback
from unittest.mock import patch

import legacy_bucket_connected as permit

BASE24 = permit.proof.BASE_SHA
BETA4 = "51F04770C316E62D9ED3EF713144F02FA2EC899645813627091365E12F61C3EF"
SOURCES = set(permit.SOURCES)
FULL_MODS = permit.proof.MOD_IDS | permit.EXTRAS


def require(value, message):
    if not value:
        raise AssertionError(message)


def sealed_json(path, checksum):
    path = Path(path).resolve()
    require(permit.proof.sha(path) == checksum.upper(), "Changed explicit JSON manifest/receipt: " + str(path))
    return permit.proof.read(path)


def table_contract(table):
    routes = table.get("routes", [])
    require(len(routes) == 3 and {row.get("sourceKey") for row in routes} == SOURCES,
            "Exactly the three known source routes are required")
    for row in routes:
        require(row.get("sourceBaseSha256") in (BASE24, BETA4), "Unknown source Base Metals identity")
        wanted = "base24-proof" if row["sourceBaseSha256"] == BASE24 else "strict"
        require(row.get("noticePolicy") == wanted, "Wrong source-specific diagnostic policy")
        require(row.get("targetManifest") and len(row.get("targetManifestSha256", "")) == 64,
                "Every source needs its full sealed target manifest")
    require(table.get("bucketProof") and len(table.get("bucketProofSha256", "")) == 64,
            "The four-boot bucket proof and explicit hash are required")


def target_pins(path, checksum, connected):
    document = sealed_json(path, checksum)
    target = document["target110"]
    connected.pinned_profile(Path(path), "target110")
    jars = target["jars"]
    require(len(jars) == len(FULL_MODS) and {j["id"] for j in jars} == FULL_MODS,
            "Target must contain all six exact mods, not a dependency overlay")
    bound = []
    for jar in jars:
        source = (Path(path).resolve().parent / jar["path"]).resolve()
        require(permit.proof.sha(source) == jar["sha256"], "Target jar bytes changed")
        runtime = "2.4.0" if jar["id"] == "basemetals" and jar["sha256"] == BASE24 else jar["version"]
        bound.append(dict(id=jar["id"], path=str(source), sha256=jar["sha256"],
                          version=jar["version"], runtimeVersion=runtime))
    return dict(manifest=str(Path(path).resolve()), manifestSha256=checksum,
                mods=bound, codeInventory=target["serverCode"])


def source_binding(previous, source_key, connected):
    matches = []
    for row in previous["sources"]:
        history = sealed_json(row["report"], row["sha256"])
        if history.get("sourceKey") == source_key:
            matches.append((row, history))
    require(len(matches) == 1, "Missing or duplicate retained source provenance")
    reference, history = matches[0]
    require(history.get("status") == "PASS", "Retained source provenance is not complete")
    source = Path(history["source"]).resolve()
    manifest = sealed_json(history["sourceManifest"], history["sourceManifestSha256"])
    archived = manifest[source_key]
    expected = {Path(j["path"]).name: j["sha256"].upper() for j in archived["jars"]}
    actual = {p.name: permit.proof.sha(p) for p in (source / "mods").glob("*.jar")}
    require(actual == expected, "Protected source mod inventory changed")
    hashes = sealed_json(history["sourceHashes"], history["sourceHashesSha256"])
    require(connected.fingerprint(source / connected.WORLD) == hashes, "Protected source world changed")
    bases = [j for j in archived["jars"] if j.get("id") == "basemetals"]
    # Historical source manifests may omit id; the archived jar filename is exact.
    if not bases:
        bases = [j for j in archived["jars"] if Path(j["path"]).name.startswith(("BaseMetals_", "BaseMetals-"))]
    require(len(bases) == 1, "Source must have exactly one declared Base Metals jar")
    base = bases[0]
    require(actual[Path(base["path"]).name] == base["sha256"].upper(), "Source Base bytes changed")
    probes = [j for j in archived["jars"] if j["sha256"].upper() == permit.PROBE_SHA]
    require(len(probes) == 1, "Source must retain the exact LegacyProbe")
    return dict(sourceKey=source_key, source=str(source), report=reference["report"],
                reportSha256=reference["sha256"], history=history,
                baseFilename=Path(base["path"]).name, baseSha256=base["sha256"].upper(),
                sourceBefore=connected.fingerprint(source))


def bind_source(row, source, target, qualified):
    mods = {m["id"]: m for m in target["mods"]}
    require(row["sourceBaseSha256"] == source["baseSha256"] == mods["basemetals"]["sha256"],
            "Destination Base does not match its exact source; downgrades/overlays are forbidden")
    require(target["codeInventory"] == qualified["inputs"]["candidate"]["codeInventory"],
            "Target Forge inventory differs from the proven engine")
    if row["noticePolicy"] == "base24-proof":
        permit.bind_target(qualified, target)
    else:
        require(source["baseSha256"] == BETA4, "Strict route is not the declared Beta4 source")


def preflight(table, connected):
    table_contract(table)
    previous = sealed_json(table["previous"], table["previousSha256"])
    require(previous.get("status") == "PASS", "Retained source provenance must be complete")
    qualified = permit.verify_proof(Path(table["bucketProof"]), table["bucketProofSha256"])
    routes = {}
    common = None
    for row in table["routes"]:
        source = source_binding(previous, row["sourceKey"], connected)
        target = target_pins(row["targetManifest"], row["targetManifestSha256"], connected)
        bind_source(row, source, target, qualified)
        same = {m["id"]: (m["sha256"], m["version"], m["runtimeVersion"])
                for m in target["mods"] if m["id"] != "basemetals"}
        require(common is None or common == same, "Non-Base final target artifacts must be identical")
        common = same
        routes[row["sourceKey"]] = dict(declaration=row, source=source, target=target)
    return routes


def preboot(source, target, runtime, connected):
    history = source["history"]
    sealed_json(source["report"], source["reportSha256"])
    sealed_json(history["sourceManifest"], history["sourceManifestSha256"])
    source_base = Path(source["source"]) / "mods" / source["baseFilename"]
    require(permit.proof.sha(source_base) == source["baseSha256"], "Source Base changed before boot")
    sealed_json(target["manifest"], target["manifestSha256"])
    expected = {Path(j["path"]).name: j["sha256"] for j in target["mods"]}
    actual = {p.name: permit.proof.sha(p) for p in (runtime / "mods").glob("*.jar")}
    extras = {name: sha for name, sha in actual.items() if name not in expected}
    require(len(extras) == 1 and next(iter(extras.values())) == permit.PROBE_SHA,
            "Only the exact archived LegacyProbe may supplement the full target")
    require(actual == {**expected, **extras}, "Destination does not match its full sealed target")
    target_base = next(j for j in target["mods"] if j["id"] == "basemetals")
    require(actual[Path(target_base["path"]).name] == source["baseSha256"], "Source/target Base mismatch at boot")
    connected.verify_code(runtime, target["codeInventory"])
    return dict(sourceBaseSha256=source["baseSha256"], targetBaseSha256=source["baseSha256"],
                stagedMods=actual)


def raw_route(connected, runtime, source, primary):
    require(len(primary.get("routes", [])) == 1, "A per-source result must have exactly one route")
    outcome = primary["routes"][0]
    require(outcome.get("explicitDependencyOverlay") == [], "No dependency overlay is permitted")
    previous = connected.oracle.read(Path(source["source"]), source["history"]["sourceCheckpoint"])
    definition = permit.proof.read(runtime / "connected-fixture.json")
    intervals = {}
    for phase in ("converted", "restarted"):
        loaded = connected.oracle.read(runtime, phase + "-loaded")
        worked = connected.oracle.read(runtime, phase + "-worked")
        require(loaded.get("held") is True and loaded.get("buildCallsThisProcess") == 0
                and loaded.get("advancedTicks") == 0, "Loaded preservation is not untouched")
        require(worked.get("held") is True and worked.get("buildCallsThisProcess") == 0
                and type(worked.get("requestedTicks")) is int and worked["requestedTicks"] == 2048
                and type(worked.get("advancedTicks")) is int and worked["advancedTicks"] == 2048,
                "Each route requires two actual 2048-tick intervals")
        results = dict(preservation=connected.strict_snapshot(previous, loaded),
            operation=connected.oracle.operation(loaded, worked, 2048),
            completedWork=connected.semantic_machine_audit.connected_interval(loaded, worked, definition, 2048))
        require(all(r["status"] == "PASS" for r in results.values()), "Raw preserved state or actual work failed")
        intervals[phase] = dict(status="PASS", requestedTicks=2048, advancedTicks=2048, results=results)
        previous = worked
    require(connected.fingerprint(Path(source["source"])) == source["sourceBefore"], "Source custody changed")
    return intervals


def require_matrix(routes, boots):
    require(len(routes) == 3 and {row.get("sourceKey") for row in routes} == SOURCES,
            "A missing/unknown route cannot qualify the matrix")
    for row in routes:
        require(row.get("status") == "PASS_ROUTE" and row.get("sourceUnchanged") is True
                and row.get("targetManifest") and row.get("targetManifestSha256")
                and row.get("sourceBaseSha256") in (BASE24, BETA4),
                "Source-only or partial PASS is not a completed connected route")
        intervals = row.get("intervals", {})
        require(set(intervals) == {"converted", "restarted"}
                and all(r.get("status") == "PASS" and r.get("requestedTicks") == 2048
                        and r.get("advancedTicks") == 2048 for r in intervals.values()),
                "Missing bounded work intervals")
    expected = {(key, phase) for key in SOURCES for phase in ("converted", "restarted")}
    expected_bases = {row["sourceKey"]: row["sourceBaseSha256"] for row in routes}
    require(len(boots) == 6 and {(b.get("sourceKey"), b.get("phase")) for b in boots} == expected
            and all(b.get("status") == "PASS" and b.get("sourceBaseSha256") == b.get("targetBaseSha256")
                    and b.get("sourceBaseSha256") == expected_bases.get(b.get("sourceKey")) for b in boots),
            "All six source-matched, audited destination boots are required")


def run(args):
    connected = importlib.import_module("native110_connected")
    table_path = args.table.resolve()
    table_sha = permit.proof.sha(table_path)
    table = permit.proof.read(table_path)
    bindings = preflight(table, connected)
    output = connected.output_path(args.output)
    for protected in (table_path, Path(table["previous"]), Path(table["bucketProof"]).parent,
                      *(Path(b["source"]["source"]) for b in bindings.values())):
        connected.require_disjoint(output, protected)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    output.mkdir(parents=True, exist_ok=False)
    receipt = output / "native110-per-source-connected-results.json"
    helpers = [Path(__file__), Path(permit.__file__), Path(permit.proof.__file__), Path(connected.__file__),
               Path(connected.oracle.__file__), Path(connected.semantic_machine_audit.__file__),
               *(Path(connected.__file__).with_name(n) for n in
                 ("native110.py", "native110_code.py", "native110_lifecycle.py", "native110_diagnostics.py", "rig_transport.py"))]
    report = dict(status="RUNNING", scope="Exact three-source/six-boot source-matched connected matrix only",
                  table=str(table_path), tableSha256=table_sha, routes=[], boots=[],
                  bucketProof=table["bucketProof"], bucketProofSha256=table["bucketProofSha256"],
                  startedAtUtc=datetime.now(timezone.utc).isoformat(),
                  protocolSha256={str(p): permit.proof.sha(p) for p in helpers})
    def write():
        permit.proof.write(receipt, report)
    write()
    try:
        for key in ("original110", "early110", "maintained110"):
            binding = bindings[key]; declaration = binding["declaration"]; source = binding["source"]; target = binding["target"]
            route_output = output / key
            original_server = connected.NativeServer

            class BoundServer(original_server):
                def __init__(self, runtime, java, launcher, label):
                    runtime = Path(runtime).resolve()
                    require(runtime == route_output / key and label in ("converted", "restarted"), "Unexpected native process")
                    record = dict(sourceKey=key, phase=label, status="RUNNING",
                                  **preboot(source, target, runtime, connected))
                    report["boots"].append(record); write()
                    self.bound_record = record; self.bound_runtime = runtime
                    try:
                        super().__init__(runtime, java, launcher, label)
                    except BaseException:
                        if getattr(self, "process", None) is not None and self.process.poll() is None:
                            try: self.close()
                            except BaseException: pass
                        record["status"] = "FAIL"; write()
                        raise

                def close(self):
                    try:
                        result = super().close()
                        preboot(source, target, self.bound_runtime, connected)
                        self.bound_record.update(status="PASS", logSha256=permit.proof.sha(Path(self.log.name)))
                        write()
                        return result
                    except BaseException as error:
                        self.bound_record.update(status="FAIL", failure=str(error)); write()
                        raise

            args_native = argparse.Namespace(target_manifest=Path(declaration["targetManifest"]),
                previous=Path(table["previous"]), output=route_output, java8=args.java8, port=args.port,
                match_source_metals=False)
            with patch.object(connected, "SOURCE_KEYS", (key,)), patch.object(connected, "NativeServer", BoundServer):
                if declaration["noticePolicy"] == "base24-proof":
                    result = permit.run(argparse.Namespace(**vars(args_native),
                        bucket_proof=Path(table["bucketProof"]), bucket_proof_sha256=table["bucketProofSha256"],
                        source_keys=(key,)))
                    child_path = output / (key + "-bucket-permit.json")
                    child = permit.proof.read(child_path)
                    require(child.get("status") == "PASS_ROUTE" and child.get("proofUnchanged") is True,
                            "Legacy notice route lacks exact proof-bound PASS_ROUTE")
                    require(child.get("bucketProofSha256") == table["bucketProofSha256"]
                            and child.get("declaredSources") == [key]
                            and child["target"]["manifestSha256"] == target["manifestSha256"],
                            "Route permit is bound to a different proof, source or target")
                    permit.verify_proof(Path(table["bucketProof"]), table["bucketProofSha256"])
                else:
                    result = connected.run(args_native)
                    child_path = route_output / "native110-connected-results.json"
            require(result == 0, "Connected source route did not pass: " + key)
            primary = permit.proof.read(route_output / "native110-connected-results.json")
            processes = {str(i): b for i, b in enumerate(report["boots"]) if b["sourceKey"] == key}
            permit.require_primary(primary, processes, (key,))
            intervals = raw_route(connected, route_output / key, source, primary)
            report["routes"].append(dict(sourceKey=key, status="PASS_ROUTE", sourceUnchanged=True,
                targetManifest=target["manifest"], targetManifestSha256=target["manifestSha256"],
                sourceBaseSha256=source["baseSha256"], noticePolicy=declaration["noticePolicy"],
                childReceipt=str(child_path), childReceiptSha256=permit.proof.sha(child_path), intervals=intervals))
            write()
        require_matrix(report["routes"], report["boots"])
        report["status"] = "PASS"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        try:
            require(permit.proof.sha(table_path) == table_sha, "Explicit source table changed")
            preflight(table, connected)
            for binding in bindings.values():
                require(connected.fingerprint(Path(binding["source"]["source"]))
                        == binding["source"]["sourceBefore"], "Protected source custody changed during matrix")
            require(report["protocolSha256"] == {str(p): permit.proof.sha(p) for p in helpers},
                    "Orchestration/ordinary audit source changed")
            report["custodyUnchanged"] = True
        except BaseException as error:
            report.update(status="FAIL", custodyUnchanged=False, custodyFailure=str(error))
        report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
        report["filesSha256"] = {p.relative_to(output).as_posix(): permit.proof.sha(p)
                                 for p in output.rglob("*") if p.is_file() and p != receipt}
        write()
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--table", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
