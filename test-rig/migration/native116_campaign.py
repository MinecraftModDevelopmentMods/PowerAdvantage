"""Preserve historical campaign inputs and require explicit 114 and 115 source routes.

Preparation reads canonical evidence and fingerprints source worlds; it never
launches them. Runs always create new 116 destinations and reuse native116's
unchanged frozen/connected state and operating algorithms.
"""

import argparse
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch
import traceback

from matrix import WORLD, digest, fingerprint, write_json
import native116
import native116_connected
import native114
import native114_connected
from native116_lifecycle import read_json
from native116_refresh import snapshot_name
from native116_sources import ACCEPTED_POWER115, SOURCE115, pending_routes, require_source115_receipt
from native116_sources import (preflight_source115, run_source115, source115_result,
                               source115_settings, verify_source115_inputs)


ACCEPTED_POWER114 = "ef08e5cd134dc75bd73518bf4c90a6711e67280f"
HISTORICAL = ("early110", "legacy112", "maintained110", "maintained112", "original110")
SOURCE113 = tuple(name + "-direct-113" for name in HISTORICAL) + tuple(
    name + "-chained-113" for name in ("early110", "maintained110", "original110"))
FROZEN_IDS = tuple("legacy/" + name for name in HISTORICAL + SOURCE113)
CONNECTED_IDS = tuple("legacy/" + name for name in HISTORICAL)
REQUIRED = {
    "frozen": set(FROZEN_IDS) | {"native114/full"} | SOURCE115["frozen"],
    "connected": set(CONNECTED_IDS) | {"native114/" + name for name in HISTORICAL} | SOURCE115["connected"],
}


def approved_file(path, checksum):
    path = Path(path).resolve()
    if digest(path) != checksum.upper():
        raise AssertionError("Archived evidence/input changed: " + str(path))
    return path


def source_row(output, kind, route_id, source, manifest, key, snapshot, fixture_key=None):
    source, manifest = source.resolve(), manifest.resolve()
    native116.require_disjoint(output, source)
    native116.require_disjoint(output, manifest)
    native116.pinned_profile(manifest, key)
    before = fingerprint(source)
    hashes = fingerprint(source / WORLD)
    if not hashes:
        raise AssertionError("Missing canonical source world: " + str(source))
    filename = "migration-" + snapshot + ".json" if kind == "connected" else snapshot
    if not (source / filename).is_file() or not (source / "migration-fixture.json").is_file():
        raise FileNotFoundError("Missing retained source serializer checkpoint: " + str(source))
    hash_file = output / "source-hashes" / (kind + "-" + route_id.replace("/", "-") + ".json")
    write_json(hash_file, hashes)
    if fingerprint(source) != before:
        raise AssertionError("Protected canonical source changed during preparation")
    return dict(id=route_id, kind=kind, source=str(source), sourceManifest=str(manifest), sourceKey=key,
                sourceManifestSha256=digest(manifest), sourceHashes=str(hash_file), sourceHashesSha256=digest(hash_file),
                sourceSnapshot=snapshot, sourceSnapshotSha256=digest(source / filename), sourceFixtureKey=fixture_key)


def require_routes(document):
    if document.get("format") != 1 or document.get("acceptedPower114") != ACCEPTED_POWER114:
        raise AssertionError("Campaign must retain the explicitly accepted merged Power 114 identity")
    rows = document["routes"]
    for kind, expected in REQUIRED.items():
        selected = [row["id"] for row in rows if row.get("kind") == kind]
        if len(selected) != len(expected) or set(selected) != expected:
            raise AssertionError("Incomplete/duplicated " + kind + " routes: all historical and native114 cases are required")
    if len(rows) != sum(map(len, REQUIRED.values())):
        raise AssertionError("Unexpected campaign route kind")


def prepare(args):
    output = native116.output_path(args.output)
    for protected in (args.frozen_evidence, args.connected_evidence):
        native116.require_disjoint(output, protected)
    frozen, connected = read_json(args.frozen_evidence), read_json(args.connected_evidence)
    for report, count in ((frozen, 13), (connected, 5)):
        if report.get("status") != "PASS" or len(report["routes"]) != count or any(
                row.get("status") != "PASS" for row in report["routes"]):
            raise AssertionError("Select the complete accepted 114 evidence, not an incomplete/passing subset")
    output.mkdir(parents=True, exist_ok=False)
    document = dict(format=1, acceptedPower114=ACCEPTED_POWER114, qualificationStatus="UNQUALIFIED", routes=[],
                    frozenEvidence=str(args.frozen_evidence.resolve()), frozenEvidenceSha256=digest(args.frozen_evidence),
                    connectedEvidence=str(args.connected_evidence.resolve()), connectedEvidenceSha256=digest(args.connected_evidence))
    for row in frozen["routes"]:
        runtime = Path(row["runtime"])
        old = runtime / "native114-provenance.json"
        if row.get("provenanceSha256"):
            approved_file(old, row["provenanceSha256"])
        if read_json(runtime / "state-comparison.json") != row["comparison"]:
            raise AssertionError("Archived frozen comparison differs from the selected final report")
        provenance = read_json(old)
        source = Path(provenance["source"])
        name = source.name
        manifest = approved_file(provenance["sourceManifest"], provenance["sourceManifestSha256"])
        selected = snapshot_name(source, provenance["sourceSnapshotSha256"])
        if fingerprint(source / WORLD) != provenance["sourceHashes"]:
            raise AssertionError("Original frozen source fingerprint changed")
        legacy = source_row(output, "frozen", "legacy/" + name, source, manifest, provenance["sourceKey"], selected)
        document["routes"].append(legacy)
        if name == "original110-chained-113":
            document["routes"].append(dict(id="native114/full", kind="frozen", native114SourceRecipe=legacy,
                source114Manifest=provenance["targetManifest"], source114ManifestSha256=provenance["targetManifestSha256"],
                sourceStatus="REQUIRES_DISPOSABLE_114_GENERATION"))
    for row in connected["routes"]:
        old = approved_file(row["result"], row["sha256"]) if "result" in row and "sha256" in row else Path(row["result"])
        # Refresh reports record result paths without a second digest. The result
        # itself still pins every input and is tied to the selected archive above.
        provenance = read_json(old)
        if (provenance.get("status") != "PASS" or provenance.get("sourcePreservation", {}).get("status") != "PASS"
                or provenance.get("sourceRuntimeBefore") != provenance.get("sourceRuntimeAfter")):
            raise AssertionError("Native114 connected operation/source preservation did not pass")
        source = Path(provenance["source"])
        name = provenance["sourceKey"]
        manifest = approved_file(provenance["sourceManifest"], provenance["sourceManifestSha256"])
        approved_file(provenance["sourceHashes"], provenance["sourceHashesSha256"])
        if fingerprint(source / WORLD) != read_json(Path(provenance["sourceHashes"])):
            raise AssertionError("Original connected source fingerprint changed")
        legacy = source_row(output, "connected", "legacy/" + name, source, manifest, name, provenance["sourceCheckpoint"])
        document["routes"].append(legacy)
        document["routes"].append(dict(id="native114/" + name, kind="connected", native114SourceRecipe=legacy,
            source114Manifest=provenance["targetManifest"], source114ManifestSha256=provenance["targetManifestSha256"],
            sourceStatus="REQUIRES_DISPOSABLE_114_GENERATION"))
    document["acceptedPower115"] = ACCEPTED_POWER115
    document["routes"].extend(pending_routes())
    require_routes(document)
    write_json(output / "native116-upgrade-routes.json", document)
    return 0


def require_source114_receipt(row):
    if not row["id"].startswith("native114/"):
        return
    if row.get("sourceStatus") != "GENERATED_DISPOSABLE_114_VERIFIED":
        raise AssertionError("A new fingerprint of an unverified old 114 destination is not approval")
    receipt_path = approved_file(row["source114Receipt"], row["source114ReceiptSha256"])
    receipt = read_json(receipt_path)
    source = Path(row["source"])
    if (receipt.get("status") != "PASS" or receipt.get("acceptedPower114") != ACCEPTED_POWER114
            or receipt.get("sourceRuntime") != str(source.resolve())
            or receipt.get("runtimeFingerprint") != fingerprint(source)
            or receipt.get("worldFingerprint") != fingerprint(source / WORLD)):
        raise AssertionError("Recorded disposable114 source receipt/world fingerprint changed")
    approved_file(receipt["target114Manifest"], receipt["target114ManifestSha256"])
    native114.pinned_profile(Path(receipt["target114Manifest"]), "target114")
    if row["kind"] == "frozen" and (receipt.get("blocks"), receipt.get("tiles")) != (355, 332):
        raise AssertionError("Native114 source must retain the full 355-block/332-tile fixture")
    if row["kind"] == "connected" and set(receipt.get("connectedCases", [])) != {
            "C-CONVEYOR", "C-CRUSHER", "C-EDRILL", "C-ELECTRIC", "C-SDRILL", "C-STEAM", "battery"}:
        raise AssertionError("Native114 connected source lacks a required lane/drill/battery")


def generate114(args):
    document = read_json(args.routes)
    require_routes(document)
    output = native116.output_path(args.output)
    native116.require_disjoint(output, args.routes)
    for row in document["routes"]:
        if not row["id"].startswith("native114/"):
            continue
        recipe = row["native114SourceRecipe"]
        for protected in (recipe["source"], recipe["sourceManifest"], recipe["sourceHashes"], row["source114Manifest"]):
            native116.require_disjoint(output, Path(protected))
        seed, _, jars = native114.pinned_profile(Path(row["source114Manifest"]), "target114")
        for protected in (seed, *jars):
            native116.require_disjoint(output, protected)
    output.mkdir(parents=True, exist_ok=False)
    for row in document["routes"]:
        if not row["id"].startswith("native114/"):
            continue
        if row.get("sourceStatus") != "REQUIRES_DISPOSABLE_114_GENERATION":
            raise AssertionError("Do not replay or replace an existing source-generation receipt")
        recipe = row["native114SourceRecipe"]
        manifest = approved_file(row["source114Manifest"], row["source114ManifestSha256"])
        native114.pinned_profile(manifest, "target114")
        source_manifest = approved_file(recipe["sourceManifest"], recipe["sourceManifestSha256"])
        source_hashes = approved_file(recipe["sourceHashes"], recipe["sourceHashesSha256"])
        source = Path(recipe["source"])
        filename = "migration-" + recipe["sourceSnapshot"] + ".json" if row["kind"] == "connected" else recipe["sourceSnapshot"]
        approved_file(source / filename, recipe["sourceSnapshotSha256"])
        destination = output / row["id"].replace("/", "-")
        settings = Namespace(source=source, source_manifest=source_manifest, source_key=recipe["sourceKey"],
                             source_hashes=source_hashes, source_snapshot=recipe["sourceSnapshot"],
                             source_checkpoint=recipe["sourceSnapshot"], target_manifest=manifest,
                             java8=args.java8, output=destination / "runtime", ticks=512, port=args.port)
        receipt = dict(status="RUNNING", acceptedPower114=ACCEPTED_POWER114,
                       method="new disposable copy converted/operated under exact accepted archived114 jars; no canonical launch",
                       target114Manifest=str(manifest), target114ManifestSha256=digest(manifest),
                       originalSource=str(source), originalSourceHashesSha256=digest(source_hashes))
        destination.mkdir(parents=True, exist_ok=False)
        receipt_path = destination / "source114-receipt.json"
        try:
            if row["kind"] == "frozen":
                definition = read_json(source / recipe["sourceSnapshot"])
                counts = (len(definition), sum("nbtBase64" in entry for entry in definition))
                if counts != (355, 332):
                    raise AssertionError("Approved source is not the full 355-block/332-tile fixture")
                stage = native114.stage
                def stage_at_port(runtime, profile):
                    launcher = stage(runtime, profile)
                    properties = runtime / "server.properties"
                    lines = [line for line in properties.read_text().splitlines()
                             if not line.startswith(("server-port=", "generator-settings="))]
                    properties.write_text("\n".join(lines) + f"\nserver-port={args.port}\ngenerator-settings=\n", encoding="utf-8")
                    return launcher
                with patch.object(native114, "stage", stage_at_port):
                    code = native114.run(settings)
                runtime, snapshot, fixture_key = settings.output, "migration-restarted.json", None
                report = read_json(runtime / "state-comparison.json")
                if any(report.get(gate, {}).get("status") != "PASS" for gate in ("converted", "restarted", "idempotence")):
                    raise AssertionError("Disposable114 source differs from the approved frozen semantics")
                receipt.update(blocks=355, tiles=332, semanticComparison=report)
            else:
                code = native114_connected.run(settings)
                runtime, snapshot = settings.output / "target114", "restarted-worked"
                fixture_key = read_json(runtime / "connected-fixture.json")["sourceProfile"]
                report = read_json(settings.output / "native114-connected-results.json")
                if report.get("sourcePreservation", {}).get("status") != "PASS" or any(
                        value.get("status") != "PASS" for value in report["results"].values()):
                    raise AssertionError("Disposable114 source failed preserved stocks or measured operation")
                receipt["connectedCases"] = sorted(report["sourceState"]["cases"])
                receipt["operatingComparison"] = report["results"]
            if code:
                raise AssertionError("Native114 source-generation protocol failed")
            receipt.update(status="PASS", sourceRuntime=str(runtime.resolve()),
                           runtimeFingerprint=fingerprint(runtime), worldFingerprint=fingerprint(runtime / WORLD))
            write_json(receipt_path, receipt)
            pinned = source_row(output / "source-records", row["kind"], row["id"], runtime, manifest,
                                "target114", snapshot, fixture_key)
            row.update(pinned, sourceStatus="GENERATED_DISPOSABLE_114_VERIFIED", source114Receipt=str(receipt_path.resolve()),
                       source114ReceiptSha256=digest(receipt_path))
            require_source114_receipt(row)
        except BaseException as error:
            receipt.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
            write_json(receipt_path, receipt)
            raise
        finally:
            write_json(output / "native116-upgrade-routes.json", document)
    return 0


def run(args):
    document = read_json(args.routes)
    require_routes(document)
    output = native116.output_path(args.output)
    native116.require_disjoint(output, args.routes)
    native116.require_disjoint(output, args.target_manifest)
    seed, _, jars = native116.pinned_profile(args.target_manifest.resolve(), "target116")
    for protected in (seed, *jars):
        native116.require_disjoint(output, protected)
    rows = [row for row in document["routes"] if row["kind"] == args.kind]
    for row in rows:
        require_source114_receipt(row)
        require_source115_receipt(row)
        for protected in (row["source"], row["sourceManifest"], row["sourceHashes"]):
            native116.require_disjoint(output, Path(protected))
    report = dict(status="RUNNING", kind=args.kind, routesManifest=str(args.routes.resolve()),
                  routesManifestSha256=digest(args.routes), targetManifest=str(args.target_manifest.resolve()),
                  targetManifestSha256=digest(args.target_manifest), routes=[])
    output.mkdir(parents=True, exist_ok=False)
    for index, row in enumerate(rows):
        outcome = dict(id=row["id"], status="RUNNING")
        report["routes"].append(outcome)
        try:
            manifest = approved_file(row["sourceManifest"], row["sourceManifestSha256"])
            hashes = approved_file(row["sourceHashes"], row["sourceHashesSha256"])
            source = Path(row["source"])
            filename = "migration-" + row["sourceSnapshot"] + ".json" if args.kind == "connected" else row["sourceSnapshot"]
            approved_file(source / filename, row["sourceSnapshotSha256"])
            settings = Namespace(source=source, source_manifest=manifest, source_key=row["sourceKey"],
                                 source_hashes=hashes, source_snapshot=row["sourceSnapshot"], source_checkpoint=row["sourceSnapshot"],
                                 source_fixture_key=row.get("sourceFixtureKey"), target_manifest=args.target_manifest,
                                 java8=args.java8, output=output / f"route-{index:02}", ticks=512, port=args.port)
            if args.kind == "connected":
                code = native116_connected.run(settings)
            else:
                stage = native116.stage
                def stage_at_port(runtime, profile):
                    launcher = stage(runtime, profile)
                    properties = runtime / "server.properties"
                    lines = [line for line in properties.read_text().splitlines()
                             if not line.startswith(("server-port=", "generator-settings="))]
                    properties.write_text("\n".join(lines) + f"\nserver-port={args.port}\ngenerator-settings=\n", encoding="utf-8")
                    return launcher
                with patch.object(native116, "stage", stage_at_port):
                    code = native116.run(settings)
            outcome.update(status="PASS" if code == 0 else "FAIL", runtime=str(settings.output))
        except Exception as error:
            outcome.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        write_json(output / "native116-campaign-results.json", report)
    report["status"] = "PASS" if len(report["routes"]) == len(REQUIRED[args.kind]) and all(
        row["status"] == "PASS" for row in report["routes"]) else "FAIL"
    write_json(output / "native116-campaign-results.json", report)
    return int(report["status"] != "PASS")


def attach115(args):
    """Attach explicit approved receipts to a NEW route document, without launching a source."""
    document = read_json(args.routes)
    require_routes(document)
    output = native116.output_path(args.output)
    for protected in (args.routes, args.receipts):
        native116.require_disjoint(output, protected)
    bundle = read_json(args.receipts)
    if bundle.get("acceptedPower115") != ACCEPTED_POWER115:
        raise AssertionError("Receipt bundle must select the explicitly merged Power 1.15 source")
    receipts = bundle["receipts"]
    expected = SOURCE115["frozen"] | SOURCE115["connected"]
    if len(receipts) != len(expected) or {entry["id"] for entry in receipts} != expected:
        raise AssertionError("All six native115 source receipts are required exactly once")
    output.mkdir(parents=True, exist_ok=False)
    for entry in receipts:
        receipt_path = approved_file(entry["receipt"], entry["sha256"])
        receipt = read_json(receipt_path)
        source = Path(receipt["sourceRuntime"])
        native116.require_disjoint(output, source)
        original = next(row for row in document["routes"] if row["id"] == entry["id"])
        row = source_row(output / "source-records", original["kind"], original["id"], source,
                         Path(receipt["target115Manifest"]), "target115", receipt["sourceSnapshot"],
                         receipt.get("sourceFixtureKey"))
        row.update(sourceStatus="GENERATED_DISPOSABLE_115_VERIFIED", source115Receipt=str(receipt_path),
                   source115ReceiptSha256=digest(receipt_path))
        require_source115_receipt(row)
        original.clear()
        original.update(row)
    document["source115Receipts"] = str(args.receipts.resolve())
    document["source115ReceiptsSha256"] = digest(args.receipts)
    write_json(output / "native116-upgrade-routes.json", document)
    return 0


def generate115(args):
    """Generate six NEW accepted115 sources, receipts and the complete 116 route plan."""
    plan = preflight_source115(args)
    document = read_json(getattr(args, "routes", None) or args.source_routes)
    document.update(acceptedPower115=ACCEPTED_POWER115, qualificationStatus="UNQUALIFIED")
    if getattr(args, "routes", None) is None:
        document["routes"].extend(pending_routes())
    require_routes(document)
    for row in document["routes"]:
        if row["id"].startswith("native115/") and row.get("sourceStatus") != "REQUIRES_APPROVED_DISPOSABLE_115_SOURCE":
            raise AssertionError("Do not replace an existing native115 source receipt")
    if args.validate_only:
        import json
        print(json.dumps(dict(status="PREFLIGHT_ONLY_NO_RUNTIME", sourceCount=len(plan["sources"]),
            target115Manifest=plan["target115Manifest"], target115ManifestSha256=plan["target115ManifestSha256"],
            output=plan["output"], port=args.port), indent=2))
        return 0
    output = Path(plan["output"])
    output.mkdir(parents=True, exist_ok=False)
    write_json(output / "source115-input-provenance.json", plan)
    bundle = dict(status="RUNNING", scope="DISPOSABLE_NATIVE115_SOURCE_ONLY", acceptedPower115=ACCEPTED_POWER115,
        target115Manifest=plan["target115Manifest"], target115ManifestSha256=plan["target115ManifestSha256"], receipts=[])
    try:
        for selected in plan["sources"]:
            verify_source115_inputs(plan)
            identity, kind = selected["id"], selected["kind"]
            destination = output / identity.replace("/", "-")
            destination.mkdir()
            settings = source115_settings(args, selected["recipe"], destination / "runtime")
            receipt_path = destination / "source115-receipt.json"
            receipt = dict(status="RUNNING", scope="DISPOSABLE_NATIVE115_SOURCE_ONLY", acceptedPower115=ACCEPTED_POWER115,
                id=identity, kind=kind, stoppedCleanly=False, target115Manifest=plan["target115Manifest"],
                target115ManifestSha256=plan["target115ManifestSha256"], sourceRecipe=selected["recipe"],
                method="New disposable native115 copy under exact archived F; canonical sources never launch",
                java8=str(settings.java8), port=args.port)
            write_json(receipt_path, receipt)
            print("Generating " + identity + " from a protected copied fixture", flush=True)
            try:
                code, stops = run_source115(settings, kind)
                if code:
                    raise AssertionError("Native115 source-generation protocol failed")
                runtime, snapshot, fixture, evidence = source115_result(kind, settings, stops)
                verify_source115_inputs(plan)
                receipt.update(evidence, status="PASS", sourceRuntime=str(runtime.resolve()), sourceSnapshot=snapshot,
                    sourceFixtureKey=fixture, runtimeFingerprint=fingerprint(runtime), worldFingerprint=fingerprint(runtime / WORLD))
                write_json(receipt_path, receipt)
                row = source_row(output / "source-records", kind, identity, runtime, settings.target_manifest,
                                 "target115", snapshot, fixture)
                row.update(sourceStatus="GENERATED_DISPOSABLE_115_VERIFIED", source115Receipt=str(receipt_path.resolve()),
                           source115ReceiptSha256=digest(receipt_path))
                require_source115_receipt(row)
                original = next(row for row in document["routes"] if row["id"] == identity)
                original.clear()
                original.update(row)
                bundle["receipts"].append(dict(id=identity, receipt=str(receipt_path.resolve()), sha256=digest(receipt_path)))
                print("PASS " + identity + ": saved/stopped native115 source only; native116 remains UNQUALIFIED", flush=True)
            except BaseException as error:
                receipt.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
                write_json(receipt_path, receipt)
                raise
            finally:
                write_json(output / "native116-upgrade-routes.json", document)
                write_json(output / "source115-receipts.json", bundle)
        require_routes(document)
        for row in document["routes"]:
            require_source115_receipt(row)
        verify_source115_inputs(plan)
        bundle.update(status="PASS", canonicalSourcesUnchanged=True, seedCodeUnchanged=True)
        document.update(source115Receipts=str((output / "source115-receipts.json").resolve()))
    except BaseException as error:
        bundle.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        raise
    finally:
        try:
            verify_source115_inputs(plan)
        except BaseException as error:
            bundle.update(status="FAIL", protectionFailure=str(error))
            raise
        finally:
            write_json(output / "source115-receipts.json", bundle)
            document["source115ReceiptsSha256"] = digest(output / "source115-receipts.json")
            write_json(output / "native116-upgrade-routes.json", document)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pin = commands.add_parser("prepare")
    pin.add_argument("--frozen-evidence", type=Path, required=True)
    pin.add_argument("--connected-evidence", type=Path, required=True)
    pin.add_argument("--output", type=Path, required=True)
    source114 = commands.add_parser("generate114", help="Generate and shut down new disposable accepted114 sources; never launch canonical saves")
    source114.add_argument("--routes", type=Path, required=True)
    source114.add_argument("--java8", type=Path, required=True)
    source114.add_argument("--output", type=Path, required=True)
    source114.add_argument("--port", type=int, default=25595)
    source115 = commands.add_parser("attach115", help="Attach six approved stopped native115 source receipts; no source launch")
    source115.add_argument("--routes", type=Path, required=True)
    source115.add_argument("--receipts", type=Path, required=True)
    source115.add_argument("--output", type=Path, required=True)
    generate = commands.add_parser("generate115", help="Create six disposable stopped115 sources under exact archived F; one leased server at a time")
    generate.add_argument("--source-routes", type=Path, required=True)
    generate.add_argument("--routes", type=Path, help="Canonical native116 plan to retain; legacy recipes must match the sealed F source plan")
    generate.add_argument("--target115-manifest", type=Path, required=True)
    generate.add_argument("--java8", type=Path, required=True)
    generate.add_argument("--output", type=Path, required=True)
    generate.add_argument("--port", type=int, required=True)
    generate.add_argument("--validate-only", action="store_true", help="Read-only preflight; never creates an output or launches a process")
    launch = commands.add_parser("run")
    launch.add_argument("kind", choices=("frozen", "connected"))
    launch.add_argument("--routes", type=Path, required=True)
    launch.add_argument("--target-manifest", type=Path, required=True)
    launch.add_argument("--java8", type=Path, required=True)
    launch.add_argument("--output", type=Path, required=True)
    launch.add_argument("--port", type=int, default=25595)
    arguments = parser.parse_args(argv)
    return {"prepare": prepare, "generate114": generate114, "generate115": generate115,
            "attach115": attach115, "run": run}[arguments.command](arguments)


if __name__ == "__main__":
    raise SystemExit(main())
