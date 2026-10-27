"""Retained dimension/player fixtures through native 1.15 death and respawn."""

import argparse
import copy
import itertools
import json
import os
from pathlib import Path
import shutil
import socket
import tempfile

import nbtlib
from actors import enchantments, semantic
from dimensions import PLAYER_STATE, exact_item_differences
from native115_actors import ROUTES, baseline
from matrix import WORLD, Server, copy_fixture, differences, digest, fingerprint, parse_nbt, write_json
from native115 import output_path, pinned_profile, require_clean_log, require_disjoint
from respawn import respawn_verdict, spawn_reference


def native_respawn_verdict(record, registry, world_spawn):
    # Only translate the exact renamed platform class. Unknown handlers still fail.
    value = copy.deepcopy(record)
    if value.get("respawn", {}).get("handler") == "ServerPlayNetHandler.PERFORM_RESPAWN":
        value["respawn"]["handler"] = "NetHandlerPlayServer.PERFORM_RESPAWN"
    return respawn_verdict(value, registry, world_spawn)


def publish_definition(path, value):
    # The tick probe reads this file concurrently; publish only complete JSON.
    descriptor, name = tempfile.mkstemp(dir=path.parent, prefix=path.name + ".", suffix=".tmp")
    os.close(descriptor)
    temporary = Path(name)
    try:
        write_json(temporary, value)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def run(args):
    from actors import boot
    root = output_path(args.output)
    require_disjoint(root, args.fixtures)
    target = pinned_profile(args.target_manifest.resolve(), "target115")
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    root.mkdir(parents=True)
    results = {}
    sources = {}
    for route in args.routes:
        source = args.fixtures / route
        before = fingerprint(source)
        sources[route] = {"path": str(source.resolve()), "fingerprint": before}
        write_json(root / "source-fingerprints.json", sources)
        runtime = root / route
        launcher = boot(runtime, target, args.java8, args.port)
        copy_fixture(source, runtime)
        shutil.copy2(source / "dimension-fixture.json", runtime / "dimension-fixture.json")
        props = runtime / "server.properties"
        props.write_text(props.read_text().replace("allow-nether=false", "allow-nether=true"))
        definition = json.loads((runtime / "dimension-fixture.json").read_text())
        player_file = runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
        expected_player = baseline(source, "dimensions")[0]["file"]
        expected_items = {key: expected_player[key] for key in ("Inventory", "EnderItems")}
        migrated_player = None
        try:
            for boot_label in ("respawn", "respawn-restart"):
                server = Server(runtime, args.java8, launcher, boot_label)
                try:
                    if migrated_player is None:
                        # Let the real pre-datafix world hook migrate the copy once.
                        # Independent deaths restore that native player, not raw old bytes
                        # after the hook has already completed for this process.
                        migrated_player = player_file.read_bytes()
                        bootstrap = semantic(nbtlib.load(player_file).unpack(), PLAYER_STATE, enchantments(runtime))
                        bootstrap_issues = differences(expected_items, bootstrap, "initial-migration")
                        bootstrap_issues.extend(exact_item_differences(expected_items, bootstrap, "initial-migration"))
                        if bootstrap_issues:
                            write_json(runtime / "bootstrap-failure.json", bootstrap_issues)
                            raise AssertionError("Native player bootstrap lost or changed source inventory")
                        folder = runtime / "bootstrap-evidence"
                        folder.mkdir()
                        shutil.copy2(player_file, folder / "player.dat")
                        shutil.copy2(runtime / WORLD / "level.dat", folder / "level.dat")
                    for keep, mode in itertools.product((True, False), ("forced", "bed", "missing")):
                        label = boot_label + "-" + ("keep" if keep else "drop") + "-" + mode
                        player_file.write_bytes(migrated_player)
                        definition["respawn"] = {"keepInventory": keep, "bedMode": mode}
                        publish_definition(runtime / "dimension-fixture.json", definition)
                        server.send("advdimensions respawn " + label)
                        server.wait("ADVDIMENSIONS COMPLETE " + label, 120)
                        server.send("save-all flush")
                        server.wait("Saved the", 45)
                        record = json.loads((runtime / f"dimensions-{label}.json").read_text())
                        registry = enchantments(runtime)
                        verdict = native_respawn_verdict(record, registry, spawn_reference(runtime / WORLD / "level.dat"))
                        observed_before = semantic(parse_nbt(record["respawn"]["before"]), PLAYER_STATE, registry)
                        source_issues = differences(expected_items, observed_before, "source-inventory")
                        source_issues.extend(exact_item_differences(expected_items, observed_before, "source-inventory"))
                        verdict["differences"].extend(source_issues)
                        if source_issues:
                            verdict["status"] = "FAIL"
                        saved = semantic(nbtlib.load(player_file).unpack(), PLAYER_STATE, registry)
                        observed = semantic(parse_nbt(record["respawn"]["after"]), PLAYER_STATE, registry)
                        issues = differences(observed, saved, "saved-player") + exact_item_differences(observed, saved, "saved-player")
                        verdict["differences"].extend(issues)
                        if issues:
                            verdict["status"] = "FAIL"
                        folder = runtime / "respawn-evidence" / label
                        folder.mkdir(parents=True)
                        shutil.copy2(player_file, folder / "player.dat")
                        shutil.copy2(runtime / WORLD / "level.dat", folder / "level.dat")
                        results[route + "/" + label] = verdict
                        write_json(root / "results.json", results)
                        print(route + "/" + label + "=" + verdict["status"], flush=True)
                finally:
                    server.close()
                require_clean_log(runtime / ("console-" + boot_label + ".log"))
        finally:
            unchanged = fingerprint(source) == before
            write_json(runtime / "source-integrity.json", {"unchanged": unchanged})
            if not unchanged:
                raise AssertionError("Protected respawn source changed")
    status = "FAIL" if any(result["status"] != "PASS" for result in results.values()) else "PASS"
    write_json(root / "summary.json", {"status": status, "cases": len(results), "results": results,
               "targetManifest": str(args.target_manifest.resolve()), "targetManifestSha256": digest(args.target_manifest),
               "sourceFixtures": "UNCHANGED", "realClientLoginAndDeathScreen": "NOT_TESTED",
               "oracleAdapter": "Exact ServerPlayNetHandler to NetHandlerPlayServer class rename only"})
    return int(status != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, choices=range(25584, 25588), required=True)
    parser.add_argument("--routes", nargs="+", choices=ROUTES,
                        default=["original110", "maintained112", "original110-chained-113"])
    raise SystemExit(run(parser.parse_args()))
