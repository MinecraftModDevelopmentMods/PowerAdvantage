"""Native 1.16 destinations for retained actor/dimension fixtures; no source boots."""

import argparse
import copy
import json
from pathlib import Path
import shutil
import socket
import time

import actors
import dimensions
from matrix import WORLD, copy_fixture, digest, fingerprint, write_json
from native116_code import Server
from native116 import output_path, pinned_profile, require_clean_log, require_disjoint
from native116 import boot
from native116_actor_io import capture_actors, capture_dimensions


HISTORICAL = ("original110", "early110", "maintained110", "legacy112", "maintained112")
ROUTES = HISTORICAL + ("original110-chained-113", "maintained110-direct-113", "maintained112-direct-113")


def baseline(source, kind):
    prefix = "dimension-semantic-" if kind == "dimensions" else "semantic-"
    label = next((label for label in ("runtime-restart", "restarted", "source")
                  if (source / (prefix + label + ".json")).is_file()), None)
    if label is None:
        raise FileNotFoundError(f"No retained {kind} serializer checkpoint in {source}")
    capture = capture_dimensions if kind == "dimensions" else capture_actors
    return capture(source, label), label


def expected_state(value, kind, survival=False, adapter=True):
    if kind == "dimensions":
        result = dimensions.survival_expected(value) if survival else dimensions.strict_expected(value, adapter)
    else:
        result = copy.deepcopy(value)
        if survival:
            for entity in result["entities"].values():
                for key in ("Pos", "Rotation", "Motion"):
                    entity.pop(key, None)
    if not adapter:
        result.pop("adapter", None)
        result.pop("playerWorldDimension", None)
    # Embedded host data is observed at conversion, not rewritten by the server-only player adapter.
    return result


def retain(runtime, kind, label):
    filename = "dimension-fixture.json" if kind == "dimensions" else "actor-fixture.json"
    definition = json.loads((runtime / filename).read_text())
    folder = runtime / ("dimension-evidence" if kind == "dimensions" else "actor-evidence") / label
    folder.mkdir(parents=True, exist_ok=False)
    for source, target in ((runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat"), "player.dat"),
                           (runtime / WORLD / "level.dat", "level.dat")):
        shutil.copy2(source, folder / target)
    capture = capture_dimensions if kind == "dimensions" else capture_actors
    state = capture(runtime, label, folder)
    if kind == "dimensions":
        state = native_dimension_state(state)
    write_json(runtime / (("dimension-semantic-" if kind == "dimensions" else "semantic-") + label + ".json"), state)
    return state


def native_dimension_state(state):
    """1.14+ MobEntity drops Leashed but retains Leash; verified in native 1.16 NBT."""
    result = copy.deepcopy(state)
    def visit(value):
        if isinstance(value, list):
            for entry in value:
                visit(entry)
        elif isinstance(value, dict):
            if value.get("id") in ("minecraft:horse", "minecraft:wolf") and "Leashed" not in value:
                value["Leashed"] = int(isinstance(value.get("Leash"), dict))
            for entry in list(value.values()):
                visit(entry)
    visit(result.get("entities", {}))
    return result


def command(server, runtime, kind, operation, label):
    root, complete = ("advdimensions", "ADVDIMENSIONS") if kind == "dimensions" else ("advactors", "ADVACTORS")
    server.send(f"{root} {operation} {label}")
    server.wait(f"{complete} COMPLETE {label}", 120)
    server.send("save-all flush")
    server.wait("Saved the", 45)
    return retain(runtime, kind, label)


def release(server, runtime, kind, label):
    if kind == "dimensions":
        # Native leash resolution must happen before the first serializer/save.
        server.send(f"advdimensions operate {label}")
        server.wait(f"ADVDIMENSIONS COMPLETE {label}", 120)
        return None
    if kind == "actors":
        # Native getChunk() loads FULL terrain but does not grant entity-ticking
        # tickets. Ticket only the unchanged historical actor fixture rectangle.
        server.send("forceload add 96 -32 223 47")
        server.send("say NATIVE115-ACTOR-TICKETS-REQUESTED")
        server.wait("NATIVE115-ACTOR-TICKETS-REQUESTED", 30)
    return command(server, runtime, kind, "operate", label)


def raw(runtime, kind, label):
    return json.loads((runtime / (("dimensions-" if kind == "dimensions" else "actors-") + label + ".json")).read_text())


def run_route(source, runtime, target, args):
    from native116 import boot
    before = fingerprint(source)
    approved = source / "fixture-hashes.json"
    if approved.is_file() and fingerprint(source / WORLD) != json.loads(approved.read_text()):
        raise AssertionError(f"Protected fixture differs from approved hashes: {source}")
    expected, source_label = baseline(source, args.kind)
    launcher = boot(runtime, target, args.java8, args.port)
    copy_fixture(source, runtime)
    fixture_name = "dimension-fixture.json" if args.kind == "dimensions" else "actor-fixture.json"
    shutil.copy2(source / fixture_name, runtime / fixture_name)
    if args.kind == "dimensions":
        definition = json.loads((runtime / fixture_name).read_text())
        definition["nativeOnLoad"] = True
        write_json(runtime / fixture_name, definition)
        properties = runtime / "server.properties"
        properties.write_text(properties.read_text().replace("allow-nether=false", "allow-nether=true"))
    write_json(runtime / "native116-source-provenance.json", {
        "source": str(source.resolve()), "sourceSnapshot": source_label,
        "sourceFingerprint": before, "sourceWorldFingerprint": fingerprint(source / WORLD),
        "targetManifest": str(args.target_manifest.resolve()), "targetManifestSha256": digest(args.target_manifest),
        "historicalOracle": "Unchanged actor/dimension semantic, exact item, freeze and tick assertions",
    })
    verdict = dimensions.verdict if args.kind == "dimensions" else actors.compare
    tick = dimensions.tick_result if args.kind == "dimensions" else actors.tick_result
    checks = {}
    try:
        server = Server(runtime, args.java8, launcher, "conversion")
        try:
            if args.kind == "dimensions":
                release(server, runtime, args.kind, "conversion-release")
                time.sleep(4)
            converted = command(server, runtime, args.kind, "snapshot", "converted")
            live = args.kind == "dimensions"
            checks["conversion"] = verdict(expected_state(expected, args.kind, survival=live, adapter=False), converted)
            checks["conversion-ticks" if live else "conversion-frozen"] = (tick(raw(runtime, args.kind, "conversion-release"), raw(runtime, args.kind, "converted"))
                    if live else actors.freeze_result(raw(runtime, args.kind, "converted")))
            adapter = command(server, runtime, args.kind, "read", "adapter")
            checks["adapter"] = verdict(expected_state(expected, args.kind, survival=live), adapter)
            if not live:
                checks["adapter-frozen"] = actors.freeze_result(raw(runtime, args.kind, "adapter"))
            backups = actors.backup_state(runtime)
        finally:
            server.close()
        require_clean_log(runtime / "console-conversion.log")
        server = Server(runtime, args.java8, launcher, "restart")
        try:
            if args.kind == "dimensions":
                release(server, runtime, args.kind, "restart-load-release")
                time.sleep(4)
            restarted = command(server, runtime, args.kind, "read", "restarted")
            checks["restart"] = verdict(expected_state(expected, args.kind, survival=live), restarted)
            if not live:
                checks["restart-frozen"] = actors.freeze_result(raw(runtime, args.kind, "restarted"))
            checks["idempotence"] = verdict(expected_state(adapter, args.kind, survival=live), restarted)
            release(server, runtime, args.kind, "released")
            time.sleep(4)
            operated = command(server, runtime, args.kind, "read", "runtime")
            checks["runtime"] = verdict(expected_state(expected, args.kind, survival=True), operated)
            checks["runtime-ticks"] = tick(raw(runtime, args.kind, "released"), raw(runtime, args.kind, "runtime"))
        finally:
            server.close()
        require_clean_log(runtime / "console-restart.log")
        server = Server(runtime, args.java8, launcher, "runtime-restart")
        try:
            release(server, runtime, args.kind, "restart-released")
            time.sleep(4)
            operated_again = command(server, runtime, args.kind, "read", "runtime-restart")
            checks["runtime-restart"] = verdict(expected_state(expected, args.kind, survival=True), operated_again)
            checks["restart-ticks"] = tick(raw(runtime, args.kind, "restart-released"), raw(runtime, args.kind, "runtime-restart"))
        finally:
            server.close()
        require_clean_log(runtime / "console-runtime-restart.log")
        checks["backup-stability"] = {"status": "PASS" if actors.backup_state(runtime) == backups else "FAIL", "backups": backups}
        return checks
    finally:
        write_json(runtime / "native116-comparison.json", checks)
        unchanged = fingerprint(source) == before
        write_json(runtime / "native116-source-integrity.json", {"status": "PASS" if unchanged else "FAIL", "unchanged": unchanged})
        if not unchanged:
            raise AssertionError(f"Protected source changed: {source}")


def run(args):
    root = output_path(args.output)
    require_disjoint(root, args.fixtures)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    require_disjoint(root, target[0])
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    root.mkdir(parents=True)
    results = {}
    for route in args.routes:
        if route not in ROUTES:
            raise ValueError(f"Unknown retained route: {route}")
        source = args.fixtures / route
        try:
            results[route] = run_route(source, root / route, target, args)
        except Exception as error:
            results[route] = {"campaign-error": {"status": "FAIL", "error": repr(error)}}
            write_json(root / "results.json", results)
            raise
        write_json(root / "results.json", results)
        print(route + ": " + ", ".join(k + "=" + v["status"] for k, v in results[route].items()), flush=True)
    status = "FAIL" if any(check["status"] != "PASS" for route in results.values() for check in route.values()) else "PASS"
    write_json(root / "summary.json", {"status": status, "kind": args.kind, "routes": args.routes,
               "realClientLogin": "NOT_TESTED", "sourceFixtures": "UNCHANGED", "results": results})
    return int(status != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("actors", "dimensions"), required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, choices=(25574, *range(25584, 25588)), required=True)
    parser.add_argument("--routes", nargs="+", default=list(ROUTES))
    raise SystemExit(run(parser.parse_args()))
