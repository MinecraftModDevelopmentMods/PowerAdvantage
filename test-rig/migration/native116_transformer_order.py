"""Observe and reverse real native Forge 36 Base/Power migration transformers."""

import argparse
import json
from pathlib import Path
import shutil
import socket
import subprocess
import time
import types

from native116 import boot as native116_boot
import actors
from native116_actor_io import capture_actors, capture_dimensions
import transformer_order as historical
from matrix import WORLD, compare, copy_fixture, digest, fingerprint, write_json
from native116_code import Server
from native116 import LEGACY_POWER, native_normalized, output_path, pinned_profile, require_clean_log, require_disjoint


COREMODS = Path("libraries/net/minecraftforge/coremods/4.0.6/coremods-4.0.6.jar")
BRIDGE_LIBRARIES = (
    "org/ow2/asm/asm/9.1/asm-9.1.jar", "org/ow2/asm/asm-tree/9.1/asm-tree-9.1.jar",
    "cpw/mods/modlauncher/8.1.3/modlauncher-8.1.3.jar",
)
SUFFIXES = {
    "world_info": ("net/minecraft/world/storage/SaveFormat", ["prepareLegacyWorld"]),
    "chunk_status": ("net/minecraft/world/chunk/storage/ChunkLoader", ["prepareLegacyChunk", "finalizeLegacyChunk"]),
    "worldgen_guard": historical.SUFFIXES["worldgen_guard"],
}
# Execute the historical evidence oracle unchanged, binding only target class names.
native_order_evidence = types.FunctionType(historical.order_evidence.__code__,
    dict(historical.order_evidence.__globals__, SUFFIXES=SUFFIXES), "native_order_evidence",
    historical.order_evidence.__defaults__)


def build_bridge(root, target, java):
    seed = target[0]
    libraries = seed / "libraries"
    dependencies = [libraries / path for path in BRIDGE_LIBRARIES]
    dependencies.append(next((libraries / "com/google/code/gson/gson").rglob("gson-*.jar")))
    classes = root / "probe-classes"
    classes.mkdir()
    source = Path(__file__).parent / "transformer-order-probe/TransformerOrderBridge.java"
    cp = ";".join(str(path) for path in dependencies)
    commands = [[str(java.with_name("javac.exe")), "-source", "8", "-target", "8", "-encoding", "UTF-8",
                 "-cp", cp, "-d", str(classes), str(source)],
                [str(java), "-cp", str(classes) + ";" + cp, "qualification.order.TransformerOrderBridge",
                 str(seed / COREMODS), str(root / "coremods-order-test.jar"), str(classes)]]
    for command in commands:
        result = subprocess.run(command, capture_output=True, text=True,
                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
    proof = historical.validate_patch(seed / COREMODS, root / "coremods-order-test.jar")
    proof.update({"sourceSha256": digest(source), "commands": commands,
                  "dependencies": {str(path): digest(path) for path in dependencies}})
    write_json(root / "probe-build.json", proof)
    return root / "coremods-order-test.jar"


def checkpoint(runtime, java, launcher, label, first):
    server = Server(runtime, java, launcher, label)
    try:
        server.snapshot(label)
        actors.actor_command(server, True, "snapshot" if first else "read", label)
        frozen = actors.freeze_result(json.loads((runtime / f"actors-{label}.json").read_text()))
        state = capture_actors(runtime, label)
        historical.retain_player_evidence(runtime, label)
        if first:
            actors.actor_command(server, True, "read", label + "-adapter")
            adapter = capture_actors(runtime, label + "-adapter")
            adapter_freeze = actors.freeze_result(json.loads((runtime / f"actors-{label}-adapter.json").read_text()))
            historical.retain_player_evidence(runtime, label + "-adapter")
        else:
            adapter, adapter_freeze = state, frozen
        adapter.pop("embedded", None)
        if not first:
            state.pop("embedded", None)
        if frozen["status"] != "PASS" or adapter_freeze["status"] != "PASS":
            raise AssertionError("Transformer controls require genuinely frozen actors")
        remote = 1024 if first else 2048
        server.send(f"forceload add {remote} 1024")
        server.send("say NATIVE115-ORDER-GUARD-SCHEDULED")
        server.wait("NATIVE115-ORDER-GUARD-SCHEDULED", 30)
        event_file = runtime / "transformer-order-events.jsonl"
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if event_file.exists() and event_file.read_text().count('"class":"net/minecraft/world/gen/WorldGenRegion"') == 2:
                break
            time.sleep(0.2)
        else:
            raise AssertionError("Native remote generation did not apply both real guard transformers")
        server.send(f"forceload remove {remote} 1024")
        server.send("save-all flush")
        server.wait("Saved the", 45)
    finally:
        server.close()
    require_clean_log(runtime / f"console-{label}.log")
    proof = native_order_evidence(runtime, (runtime / "transformer-order.mode").read_text().strip())
    for old, new in (("transformer-order-events.jsonl", f"transformer-order-events-{label}.jsonl"),
                     ("transformer-order-bytecode", f"transformer-order-bytecode-{label}")):
        shutil.move(str(runtime / old), str(runtime / new))
    write_json(runtime / f"order-proof-{label}.json", proof)
    write_json(runtime / f"semantic-{label}.json", state)
    write_json(runtime / f"semantic-{label}-adapter.json", adapter)
    return state, adapter, {"status": "PASS", "order": proof, "freeze": frozen, "adapterFreeze": adapter_freeze}


def run(args):
    root = output_path(args.output)
    require_disjoint(root, args.fixtures)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    if not args.probe_only:
        with socket.socket() as port:
            port.bind(("127.0.0.1", args.port))
    root.mkdir(parents=True)
    patched = build_bridge(root, target, args.java8)
    if args.probe_only:
        return 0
    results, cross, fingerprints = {}, {}, {}
    for name in args.sources:
        source = args.fixtures / name
        before = fingerprint(source)
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()):
            raise AssertionError("Protected original actor fixture changed")
        fingerprints[name] = {"source": str(source.resolve()), "fingerprint": before}
        write_json(root / "source-fingerprints.json", fingerprints)
        try:
            mixed = root / (name + "-mixed-input")
            mixed.mkdir()
            expected = historical.prepare_fixture(source, mixed)
            mixed_hashes = fingerprint(mixed)
            blocks = LEGACY_POWER / "run-migration-matrix" / (
                "original-cyano-20261005-r1/original110" if name == "original110" else "full-20261005-r1/maintained112")
            modes = {}
            for mode in args.orders:
                runtime = root / (name + "-" + mode)
                launcher = native116_boot(runtime, target, args.java8, args.port)
                shutil.copy2(patched, runtime / COREMODS)
                (runtime / "transformer-order.mode").write_text(mode)
                copy_fixture(mixed, runtime)
                shutil.copy2(mixed / "actor-fixture.json", runtime / "actor-fixture.json")
                converted, adapter, first = checkpoint(runtime, args.java8, launcher, "converted", True)
                backups = historical.migration_backups(runtime)
                provenance = historical.backup_provenance(mixed, runtime)
                restarted, _, second = checkpoint(runtime, args.java8, launcher, "restarted", False)
                checks = {
                    "conversion": actors.compare({k: v for k, v in expected.items() if k != "adapter"}, converted),
                    "adapter": actors.compare({k: v for k, v in expected.items() if k != "embedded"}, adapter),
                    "restart": actors.compare({k: v for k, v in expected.items() if k != "embedded"}, restarted),
                    "idempotence": historical.strict_equal(adapter, restarted),
                    "backup-stability": historical.strict_equal(backups, historical.migration_backups(runtime)),
                    "backup-provenance": provenance, "converted-controls": first, "restart-controls": second,
                }
                for label in ("converted", "restarted"):
                    entries = json.loads((runtime / f"migration-{label}.json").read_text())
                    stock = runtime / f"migration-stock-{label}.json"
                    write_json(stock, entries[:-len(historical.METAL_BLOCKS)])
                    checks["blocks-" + label] = compare(blocks / "migration-source.json", stock, native_normalized)
                    checks["metals-" + label] = historical.strict_equal(list(historical.METAL_BLOCKS),
                        [entry["actualName"] for entry in entries[-len(historical.METAL_BLOCKS):]])
                historical.assert_staged(runtime)
                modes[mode] = {"actors": adapter, "actorsRestart": restarted,
                               "blocks": historical.block_semantics(runtime, "converted"),
                               "blocksRestart": historical.block_semantics(runtime, "restarted")}
                results[name + "/" + mode] = checks
                write_json(runtime / "order-results.json", checks)
                write_json(root / "results.json", results)
                print(name + "/" + mode + ": " + ", ".join(key + "=" + value["status"] for key, value in checks.items()), flush=True)
            cross[name] = {mode: historical.strict_equal(modes[args.orders[0]], value) for mode, value in modes.items()}
            if fingerprint(mixed) != mixed_hashes:
                raise AssertionError("Disposable mixed input was altered by destination runs")
        finally:
            if fingerprint(source) != before:
                raise AssertionError("Protected transformer source changed")
    failed = any(value["status"] != "PASS" for checks in results.values() for value in checks.values())
    failed |= any(value["status"] != "PASS" for modes in cross.values() for value in modes.values())
    dependent = any(not checks["backup-provenance"]["power_backup_matches_original"] for checks in results.values())
    status = "FAIL" if failed else "PASS_WITH_BACKUP_ORDER_DEPENDENCE" if dependent else "PASS"
    write_json(root / "summary.json", {"status": status, "semanticStatus": "FAIL" if failed else "PASS", "crossOrder": cross,
               "runtimes": len(results), "serverLaunches": len(results) * 2, "sourceFixtures": "UNCHANGED",
               "targetManifestSha256": digest(args.target_manifest), "patchedLibrarySha256": digest(patched),
               "powerBackupOriginOrderDependent": dependent})
    return int(failed)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, choices=(25574, *range(25584, 25588)), required=True)
    parser.add_argument("--sources", nargs="+", choices=("original110", "maintained112"), default=["original110", "maintained112"])
    parser.add_argument("--orders", nargs="+", choices=historical.ORDERS, default=list(historical.ORDERS))
    parser.add_argument("--probe-only", action="store_true")
    raise SystemExit(run(parser.parse_args()))
