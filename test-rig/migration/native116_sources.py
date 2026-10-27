"""Explicit 1.15 source receipts; source approval is not 1.16 qualification."""

from argparse import Namespace
import json
from pathlib import Path
import types
from unittest.mock import patch

import connected
import matrix
import native115
import native115_connected
import native116
from matrix import WORLD, digest, fingerprint


ACCEPTED_POWER115 = "5b8e73a78391960643420a09798a1ef8eb06f518"
HISTORICAL = ("early110", "legacy112", "maintained110", "maintained112", "original110")
SOURCE115 = {"frozen": {"native115/full"},
             "connected": {"native115/" + name for name in HISTORICAL}}
ARCHIVED115_F_SHA256 = "A9BCD5A442F6332010F9D467BD00A685AA953339C39545812B32B825B96E64AA"
ARCHIVED115_ROUTES_SHA256 = "A5241F5F41426DC57DD85A15B62B211415BE8B69D004154F8FC47DDF239B35C2"
CONNECTED_CASES = {"C-CONVEYOR", "C-CRUSHER", "C-EDRILL", "C-ELECTRIC", "C-SDRILL", "C-STEAM", "battery"}


def pending_routes():
    return [dict(id=identity, kind=kind, sourceStatus="REQUIRES_APPROVED_DISPOSABLE_115_SOURCE")
            for kind, identities in SOURCE115.items() for identity in sorted(identities)]


def require_source115_receipt(row):
    if not row["id"].startswith("native115/"):
        return
    import json
    if row.get("sourceStatus") != "GENERATED_DISPOSABLE_115_VERIFIED":
        raise AssertionError("Native115 requires an explicit stopped-source receipt; prior destination PASS is not approval")
    path = Path(row["source115Receipt"])
    if digest(path) != row["source115ReceiptSha256"].upper():
        raise AssertionError("Native115 source receipt changed")
    receipt = json.loads(path.read_text())
    source = Path(row["source"])
    if (receipt.get("status") != "PASS" or receipt.get("scope") != "DISPOSABLE_NATIVE115_SOURCE_ONLY"
            or receipt.get("acceptedPower115") != ACCEPTED_POWER115 or receipt.get("stoppedCleanly") is not True
            or receipt.get("sourceRuntime") != str(source.resolve())
            or receipt.get("runtimeFingerprint") != fingerprint(source)
            or receipt.get("worldFingerprint") != fingerprint(source / WORLD)):
        raise AssertionError("Native115 stopped-source identity/fingerprint changed or was never approved")
    manifest = Path(receipt["target115Manifest"])
    if digest(manifest) != receipt["target115ManifestSha256"].upper():
        raise AssertionError("Native115 source manifest changed")
    native115.pinned_profile(manifest, "target115")
    if row["kind"] == "frozen":
        if (receipt.get("blocks"), receipt.get("tiles")) != (355, 332):
            raise AssertionError("Native115 comprehensive source must retain all 355 blocks and 332 tiles")
    elif (receipt.get("preservationStatus") != "PASS"
          or receipt.get("requestedTicks") != [512, 512]
          or receipt.get("advancedTicks") != [512, 512]):
        raise AssertionError("Native115 connected source requires both exact 512-tick boots and preservation")


def _read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def _approved(path, checksum, description):
    path = Path(path).resolve()
    if digest(path) != checksum.upper():
        raise AssertionError("Changed " + description + ": " + str(path))
    return path


def _seed_code(seed):
    result = {str(Path("libraries") / name): checksum for name, checksum in fingerprint(seed / "libraries").items()}
    result.update({path.name: digest(path) for path in seed.glob("*.jar") if "installer" not in path.name})
    return result


def source115_settings(args, recipe, output):
    return Namespace(source=Path(recipe["source"]), source_manifest=Path(recipe["sourceManifest"]),
        source_key=recipe["sourceKey"], source_hashes=Path(recipe["sourceHashes"]),
        source_snapshot=recipe["sourceSnapshot"], source_checkpoint=recipe["sourceSnapshot"],
        source_fixture_key=recipe.get("sourceFixtureKey"), target_manifest=Path(args.target115_manifest).resolve(),
        java8=Path(args.java8).resolve(), output=output, ticks=512, port=args.port)


def preflight_source115(args):
    """Read-only approval checks. No output directory, process, or canonical write."""
    if not 1 <= args.port <= 65535:
        raise ValueError("A leased server port is required")
    output = native116.output_path(args.output)
    manifest = _approved(args.target115_manifest, ARCHIVED115_F_SHA256, "archived F manifest")
    routes = _approved(args.source_routes, ARCHIVED115_ROUTES_SHA256, "archived source plan")
    target = native115.pinned_profile(manifest, "target115")
    for protected in (manifest, routes, target[0], *target[2]):
        native116.require_disjoint(output, protected)
    files = {str(manifest): digest(manifest), str(routes): digest(routes)}
    canonical = getattr(args, "routes", None)
    if canonical is not None:
        canonical = Path(canonical).resolve()
        native116.require_disjoint(output, canonical)
        files[str(canonical)] = digest(canonical)
    protocols = (matrix, connected, native115, native115_connected)
    files.update({str(Path(module.__file__).resolve()): digest(Path(module.__file__)) for module in protocols})
    rows, runtimes = [], {}
    recipes = _read(routes)["routes"]
    selections = [("frozen", "native115/full", "legacy/original110-chained-113")]
    selections += [("connected", "native115/" + name, "legacy/" + name) for name in HISTORICAL]
    for kind, identity, selected in selections:
        found = [row for row in recipes if row["kind"] == kind and row["id"] == selected]
        if len(found) != 1:
            raise AssertionError("Each approved native115 source recipe is required exactly once: " + selected)
        recipe = found[0]
        if canonical is not None:
            matches = [row for row in _read(canonical)["routes"] if row["kind"] == kind and row["id"] == selected]
            keys = ("source", "sourceManifestSha256", "sourceKey", "sourceHashesSha256", "sourceSnapshotSha256")
            if len(matches) != 1 or any(matches[0].get(key) != recipe.get(key) for key in keys):
                raise AssertionError("Canonical native116 recipe differs from the sealed F source lineage: " + selected)
        source = Path(recipe["source"]).resolve()
        native116.require_disjoint(output, source)
        source_manifest = _approved(recipe["sourceManifest"], recipe["sourceManifestSha256"], "source manifest")
        hashes = _approved(recipe["sourceHashes"], recipe["sourceHashesSha256"], "source hashes")
        filename = recipe["sourceSnapshot"] if kind == "frozen" else "migration-" + recipe["sourceSnapshot"] + ".json"
        snapshot = (source / filename).resolve()
        if snapshot.parent != source or not filename.startswith("migration-") or not filename.endswith(".json"):
            raise ValueError("Unsafe retained source snapshot")
        _approved(snapshot, recipe["sourceSnapshotSha256"], "source snapshot")
        for protected in (source_manifest, hashes):
            native116.require_disjoint(output, protected)
        if not _read(hashes) or fingerprint(source / WORLD) != _read(hashes):
            raise AssertionError("Approved source world fingerprint changed: " + str(source))
        native115.pinned_profile(source_manifest, recipe["sourceKey"])
        if kind == "frozen":
            document = _read(snapshot)
            if (len(document), sum("nbtBase64" in row for row in document)) != (355, 332):
                raise AssertionError("Source must retain all 355 blocks and 332 tiles")
        else:
            native115_connected.verify_source(source115_settings(args, recipe, output / identity.replace("/", "-")))
        files.update({str(path): digest(path) for path in (source_manifest, hashes, snapshot)})
        runtimes[str(source)] = fingerprint(source)
        rows.append(dict(id=identity, kind=kind, recipe=recipe))
    return dict(output=str(output), target115Manifest=str(manifest), target115ManifestSha256=digest(manifest),
        sourcePlan=str(routes), sourcePlanSha256=digest(routes), sources=rows, files=files,
        protectedRuntimes=runtimes, seed=str(target[0]), seedCodeFingerprint=_seed_code(target[0]))


def verify_source115_inputs(plan):
    for name, checksum in plan["files"].items():
        _approved(name, checksum, "source-generation input/protocol")
    if _seed_code(Path(plan["seed"])) != plan["seedCodeFingerprint"]:
        raise AssertionError("Protected native115 seed code changed")
    for name, before in plan["protectedRuntimes"].items():
        if fingerprint(Path(name)) != before:
            raise AssertionError("Protected canonical source runtime changed: " + name)


def run_source115(settings, kind):
    """Reuse historical runner code; observe only the newly staged process exits."""
    stops = []
    expected = settings.output if kind == "frozen" else settings.output / "target115"

    def check_runtime(runtime, launcher):
        if runtime.resolve() != expected.resolve() or launcher != native115.LAUNCHER:
            raise AssertionError("Only the NEW disposable native115 copy may launch")

    def stopped(server):
        stops.append(dict(label=Path(server.log.name).stem.removeprefix("console-"),
            returncode=server.process.returncode, commands=list(server.commands)))

    if kind == "frozen":
        class FrozenServer(matrix.Server):
            def __init__(self, runtime, java, launcher, label):
                check_runtime(runtime, launcher)
                self.commands = []
                super().__init__(runtime, java, launcher, label)

            def send(self, command):
                if command not in ("save-all flush", "stop") and not command.startswith("advprobe snapshot "):
                    raise AssertionError("Forbidden source-generation mutation: " + command)
                self.commands.append(command)
                super().send(command)

            def close(self):
                super().close()
                stopped(self)

        snapshot = types.FunctionType(matrix.start_snapshot.__code__,
            dict(matrix.start_snapshot.__globals__, Server=FrozenServer),
            matrix.start_snapshot.__name__, matrix.start_snapshot.__defaults__)
        original_stage = native115.stage

        def stage(runtime, target):
            launcher = original_stage(runtime, target)
            path = runtime / "server.properties"
            lines = [line for line in path.read_text().splitlines() if not line.startswith(("server-port=", "generator-settings="))]
            path.write_text("\n".join(lines) + f"\nserver-port={settings.port}\ngenerator-settings=\n", encoding="utf-8")
            return launcher

        with patch.object(native115, "start_snapshot", snapshot), patch.object(native115, "stage", stage):
            code = native115.run(settings)
    else:
        base = native115_connected.NativeConnectedServer

        class ConnectedServer(base):
            def __init__(self, runtime, java, launcher, label, on_loaded):
                check_runtime(runtime, launcher)
                super().__init__(runtime, java, launcher, label, on_loaded)

            def close(self):
                super().close()
                stopped(self)

        with patch.object(native115_connected, "NativeConnectedServer", ConnectedServer):
            code = native115_connected.run(settings)
    return code, stops


def source115_result(kind, settings, stops):
    if ([row.get("label") for row in stops] != ["converted", "restarted"] or any(
            row.get("returncode") != 0 or not {"save-all flush", "stop"}.issubset(row.get("commands", [])) for row in stops)):
        raise AssertionError("Both native115 boots must be cleanly saved and stopped")
    runtime = settings.output if kind == "frozen" else settings.output / "target115"
    for label in ("converted", "restarted"):
        log = runtime / ("console-" + label + ".log")
        native115_connected.require_native_boot(log)
        text = log.read_text(encoding="utf-8")
        if not all(marker in text for marker in ("Saved the game", "Stopping server", "Saving worlds", "All chunks are saved")):
            raise AssertionError("Missing actual native115 save/shutdown evidence")
    proof = dict(stoppedCleanly=True, processStops=stops)
    if kind == "frozen":
        report = _read(runtime / "state-comparison.json")
        for gate in ("converted", "restarted", "idempotence"):
            selected = report.get(gate, {})
            if selected.get("status") != "PASS" or any(selected.get("witnesses", {}).get(key) != count
                    for key, count in (("blocks", 355), ("tiles", 332))):
                raise AssertionError("Native115 comprehensive semantic comparison/counts failed")
        rows = _read(runtime / "migration-restarted.json")
        if (len(rows), sum("nbtBase64" in row for row in rows)) != (355, 332):
            raise AssertionError("Saved native115 source must retain 355 blocks and 332 tiles")
        proof.update(blocks=355, tiles=332, semanticComparison=report)
        return runtime, "migration-restarted.json", None, proof
    report = _read(settings.output / "native115-connected-results.json")
    required = {label + "_" + gate for label in ("converted", "restarted")
                for gate in ("preservation", "operation", "native_boot")} | {"real_output_across_restarts"}
    if (report.get("status") != "PASS" or report.get("sourcePreservation", {}).get("status") != "PASS"
            or set(report.get("results", {})) != required or any(row.get("status") != "PASS" for row in report["results"].values())):
        raise AssertionError("Native115 connected source failed preservation/operating gates")
    definition = _read(runtime / "connected-fixture.json")
    requested, advanced = [], []
    for label in ("converted", "restarted"):
        loaded = _read(runtime / ("connected-" + label + "-loaded.json"))
        worked = _read(runtime / ("connected-" + label + "-worked.json"))
        if loaded.get("requestedTicks") != 0 or loaded.get("advancedTicks") != 0:
            raise AssertionError("Native115 loaded source must be frozen before operation")
        for document in (loaded, worked):
            state = native115_connected.require_document(document, definition, destination=True)
            if set(state["cases"]) != CONNECTED_CASES:
                raise AssertionError("Native115 source lost a connected lane/drill/battery")
        if connected.operation(loaded, worked, 512)["status"] != "PASS":
            raise AssertionError("Native115 raw source did not operate for an exact 512-tick interval")
        requested.append(worked["requestedTicks"])
        advanced.append(worked["advancedTicks"])
    if requested != [512, 512] or advanced != [512, 512]:
        raise AssertionError("Native115 source requires exact 512+512 measured ticks")
    proof.update(preservationStatus="PASS", requestedTicks=requested, advancedTicks=advanced,
        connectedCases=sorted(CONNECTED_CASES), operatingComparison=report["results"])
    return runtime, "restarted-worked", definition["sourceProfile"], proof
