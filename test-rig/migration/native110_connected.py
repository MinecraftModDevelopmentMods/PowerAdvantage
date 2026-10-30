"""Resume copied legacy networks without rebuilding, refilling or replacing machines."""
import argparse
import base64
import gzip
import io
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import traceback
import nbtlib

import legacy_connected_oracle as oracle
import semantic_machine_audit
from rig_transport import WORLD, digest, fingerprint, write_json
from native110 import output_path, pinned_profile, require_disjoint
from native110_code import INVENTORY, verify_code
from native110_lifecycle import read_json, require_native_boot, verify_staged
from legacy_connected_oracle import saved_tile_nbt

ENGINE = "110"
SOURCE_KEYS = ("original110", "early110", "maintained110")
PROBE_SHA = "B9F756681593B508A8E3222BD592A3DFCB474303532B154045893B3C585EF1D3"


def strict_snapshot(before, after):
    def rows(document):
        result = {}
        for row in document["snapshot"]:
            if row["id"] in result: raise AssertionError("Duplicate connected witness")
            value = {key: row[key] for key in ("station", "kind", "name", "x", "y", "z", "actualName", "properties")}
            if "nbtBase64" in row:
                saved = nbtlib.File.parse(io.BytesIO(gzip.decompress(base64.b64decode(row["nbtBase64"], validate=True))))
                value["nbt"] = saved_tile_nbt(saved)
                for field in ("ForgeData", "ForgeCaps"):
                    if value["nbt"].get(field) == {}: value["nbt"].pop(field)
                if row["actualName"] in ("steamadvantage:steam_drill", "electricadvantage:electric_drill"):
                    facing = row["properties"].get("facing")
                    if "Facing" not in value["nbt"] and facing in ("down", "up", "north", "south", "west", "east"):
                        value["nbt"]["Facing"] = ["Byte", ("down", "up", "north", "south", "west", "east").index(facing)]
                identity = value["nbt"].get("id")
                if identity is not None and identity[0] == "String":
                    canonical = identity[1].replace("minecraft:", "", 1) if "advantage." in identity[1] else identity[1]
                    canonical = canonical.replace("advantage.", "advantage:", 1)
                    value["nbt"]["id"] = ["String", "minecraft:chest" if canonical == "Chest" else canonical]
            result[row["id"]] = value
        return result
    left, right = rows(before), rows(after)
    changed = sorted(key for key in left.keys() | right.keys() if left.get(key) != right.get(key))
    return dict(status="FAIL" if changed else "PASS", changed=changed,
                comparison="Exact block/property/typed-NBT equality, including target-only keys; tile-ID aliases, absent/empty Forge compounds and drill Facing encoded in block state are canonicalized")


class NativeServer(oracle.ConnectedServer):
    def close(self):
        try: return super().close()
        finally: require_native_boot(Path(self.log.name))


def run(args):
    output = output_path(args.output)
    target = pinned_profile(args.target_manifest, "target" + ENGINE)
    pins = read_json(args.target_manifest)["target" + ENGINE]
    previous = read_json(args.previous)
    if previous.get("status") != "PASS": raise AssertionError("Archived source provenance must be complete")
    for path in (args.target_manifest, args.previous, target[0], *target[2]): require_disjoint(output, path)
    selected = [row for row in previous["sources"] if read_json(Path(row["report"]))["sourceKey"] in SOURCE_KEYS]
    if len(selected) != len(SOURCE_KEYS): raise AssertionError("Incomplete native legacy source selection")
    with socket.socket() as listener: listener.bind(("127.0.0.1", args.port))
    output.mkdir(parents=True, exist_ok=False)
    helpers = [Path(__file__), Path(oracle.__file__), Path(semantic_machine_audit.__file__),
               Path(__file__).with_name("native" + ENGINE + "_code.py"),
               Path(__file__).with_name("native" + ENGINE + "_lifecycle.py"),
               Path(__file__).with_name("native" + ENGINE + "_diagnostics.py")]
    hashes = {str(path): digest(path) for path in helpers}
    report = dict(status="RUNNING", startedAtUtc=datetime.now(timezone.utc).isoformat(), routes=[],
                  targetManifestSha256=digest(args.target_manifest), previousSha256=digest(args.previous),
                  protocolSha256=hashes, scope="Imported connected machines; two native 2048-tick intervals and a full process restart; no destination construction")
    receipt = output / ("native" + ENGINE + "-connected-results.json")
    write_json(receipt, report)
    for index, row in enumerate(selected):
        server = None
        source = None
        source_before = None
        outcome = dict(status="RUNNING", phases={})
        report["routes"].append(outcome)
        try:
            history = Path(row["report"])
            if digest(history) != row["sha256"]: raise AssertionError("Archived source provenance changed")
            provenance = read_json(history)
            source = Path(provenance["source"])
            require_disjoint(output, source)
            source_before = fingerprint(source)
            expected = read_json(Path(provenance["sourceHashes"]))
            if digest(Path(provenance["sourceHashes"])) != provenance["sourceHashesSha256"]:
                raise AssertionError("Archived source hashes changed")
            if fingerprint(source / WORLD) != expected: raise AssertionError("Protected source world changed")
            archived = read_json(Path(provenance["sourceManifest"]))[provenance["sourceKey"]]
            source_pins = {Path(jar["path"]).name: jar["sha256"].upper() for jar in archived["jars"]}
            if {jar.name: digest(jar) for jar in (source / "mods").glob("*.jar")} != source_pins:
                raise AssertionError("Protected source mod bytes changed")
            probes = [Path(jar["path"]) for jar in archived["jars"] if jar["sha256"].upper() == PROBE_SHA]
            if len(probes) != 1 or digest(probes[0]) != PROBE_SHA:
                raise AssertionError("Missing exact archived server-only LegacyProbe 1.0.0")
            runtime_jars = list(target[2])
            dependency_overlay = []
            if args.match_source_metals:
                metals = [jar for jar in archived["jars"] if Path(jar["path"]).name.startswith(("BaseMetals-", "BaseMetals_"))]
                if len(metals) != 1: raise AssertionError("Explicit legacy Base Metals overlay requires exactly one source jar")
                old = Path(metals[0]["path"])
                if digest(old) != metals[0]["sha256"].upper(): raise AssertionError("Source Base Metals dependency changed")
                runtime_jars = [jar for jar in runtime_jars if not jar.name.startswith(("BaseMetals-", "BaseMetals_"))]
                runtime_jars.append(old)
                dependency_overlay = [dict(path=str(old), sha256=digest(old),
                    reason="Retain the source Base Metals registry; this route upgrades Advantage, not Base Metals")]
            profile = target[:2] + (runtime_jars + probes,)
            runtime = output / provenance["sourceKey"]
            launcher = oracle.prepare(runtime, profile, None, args.port, source)
            verify_code(runtime, pins["serverCode"])
            write_json(runtime / INVENTORY, pins["serverCode"])
            verify_staged(runtime, profile)
            if (runtime / "connected-build.allow").exists(): raise AssertionError("Destination construction authority leaked")
            definition = read_json(runtime / "connected-fixture.json")
            checkpoint = provenance["sourceCheckpoint"]
            previous_document = oracle.read(source, checkpoint)
            if oracle.state(previous_document)["status"] != "PASS": raise AssertionError("Invalid retained source stocks")
            outcome.update(source=str(source), sourceKey=provenance["sourceKey"], sourceBefore=source_before,
                           legacyProbe=dict(path=str(probes[0]), sha256=PROBE_SHA, modid="advantagemigrationprobe", version="1.0.0"),
                           runtime=str(runtime), stagedInputs=read_json(runtime / "input-jars.json"),
                           explicitDependencyOverlay=dependency_overlay)
            for phase in ("converted", "restarted"):
                server = NativeServer(runtime, args.java8, launcher, phase)
                server.observe("snapshot", phase + "-loaded")
                loaded = oracle.read(runtime, phase + "-loaded")
                if not loaded.get("held") or loaded.get("buildCallsThisProcess") != 0 or loaded.get("advancedTicks") != 0:
                    raise AssertionError("Preservation was not observed before operation")
                preservation = strict_snapshot(previous_document, loaded)
                outcome["phases"][phase + "-preservation"] = preservation
                write_json(receipt, report)
                if preservation["status"] != "PASS": raise AssertionError("Unticked imported state changed: " + str(preservation))
                server.observe("step", phase + "-worked", 2048)
                worked = oracle.read(runtime, phase + "-worked")
                operation = oracle.operation(loaded, worked, 2048)
                completed = semantic_machine_audit.connected_interval(loaded, worked, definition, 2048)
                outcome["phases"][phase + "-operation"] = operation
                outcome["phases"][phase + "-completed-work"] = completed
                if operation["status"] != "PASS" or completed["status"] != "PASS":
                    raise AssertionError("Connected machines failed measured work")
                server.send("save-all flush")
                server.wait("Saved the world", 120)
                server.close(); server = None
                verify_staged(runtime, profile)
                previous_document = worked
                write_json(receipt, report)
            outcome["status"] = "PASS"
        except Exception as error:
            outcome.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        finally:
            if server is not None:
                try: server.close()
                except Exception as error: outcome.update(status="FAIL", shutdownError=str(error))
            if source_before is not None:
                outcome["sourceUnchanged"] = fingerprint(source) == source_before
                if not outcome["sourceUnchanged"]: outcome["status"] = "FAIL"
            write_json(receipt, report)
        print(outcome.get("sourceKey", str(index)) + ": " + outcome["status"], flush=True)
    report["protocolUnchanged"] = hashes == {str(path): digest(path) for path in helpers}
    report["status"] = "PASS" if report["protocolUnchanged"] and all(row["status"] == "PASS" for row in report["routes"]) else "FAIL"
    if digest(args.target_manifest) != report["targetManifestSha256"] or digest(args.previous) != report["previousSha256"]:
        report["status"] = "FAIL"
    report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
    write_json(receipt, report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--match-source-metals", action="store_true",
                        help="Keep each source world's exact Base Metals jar to isolate the Advantage upgrade")
    raise SystemExit(run(parser.parse_args()))
