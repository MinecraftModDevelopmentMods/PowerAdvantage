"""Force real packaged Forge 25 migration transformer order on disposable copies."""

import argparse
from collections import defaultdict
import io
import json
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import time
import zipfile
import zlib

import nbtlib

from actors import (PLAYER_KEYS, actor_command, capture, compare as compare_actors,
                    enchantments, freeze_result, qualified_baselines, semantic)
from matrix import (POWER, WORLD, Server, compare as compare_blocks, copy_fixture, digest,
                    fingerprint, normalized, parse_nbt, plain, stage, write_json)


COREMODS = Path("libraries/net/minecraftforge/coremods/0.5.0/coremods-0.5.0.jar")
ENGINE = "net/minecraftforge/coremod/CoreModEngine.class"
SUFFIXES = {
    "world_info": ("net/minecraft/world/storage/SaveFormatOld", ["prepareLegacyWorld"]),
    "chunk_status": ("net/minecraft/world/chunk/storage/AnvilChunkLoader",
                     ["prepareLegacyChunk", "finalizeLegacyChunk"]),
    "worldgen_guard": ("net/minecraft/world/gen/WorldGenRegion", ["shouldBlockWorldgenWrite"]),
}
ORDERS = ("natural", "bm-first", "pa-first")
METAL_BLOCKS = ("basemetals:steel_block", "basemetals:copper_block", "basemetals:tin_block")


def strict_equal(expected, actual):
    return {"status": "PASS" if expected == actual else "FAIL",
            "exact_semantic_equality": expected == actual}


def target_profile(manifest):
    entry = json.loads(manifest.read_text(encoding="utf-8-sig"))["target113"]
    jars = []
    for jar in entry["jars"]:
        path = Path(jar["path"]).resolve()
        if digest(path) != jar["sha256"].upper():
            raise AssertionError(f"Qualified target jar changed: {path}")
        jars.append(path)
    return Path(entry["seed"]).resolve(), entry["launcher"], jars


def validate_patch(original, patched):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(patched) as after:
        old, new = set(before.namelist()), set(after.namelist())
        expected_added = {"qualification/order/TransformerOrderBridge.class",
                          "qualification/order/TransformerOrderBridge$Observed.class"}
        changed = [name for name in sorted(old & new) if before.read(name) != after.read(name)]
        if old - new or new - old != expected_added or changed != [ENGINE]:
            raise AssertionError(f"Unexpected coremods patch: removed={old-new}, added={new-old}, changed={changed}")
    return {"original_sha256": digest(original), "patched_sha256": digest(patched),
            "changed_entries": changed, "added_entries": sorted(expected_added)}


def build_bridge(root, profile, java):
    seed = profile[0]
    dependencies = [next((seed / "libraries").rglob(pattern)) for pattern in
                    ("asm-6*.jar", "asm-tree-6*.jar", "modlauncher-2.1.5.jar", "gson-*.jar")]
    classes = root / "probe-classes"
    classes.mkdir()
    cp = ";".join(str(path) for path in dependencies)
    source = Path(__file__).parent / "transformer-order-probe/TransformerOrderBridge.java"
    javac = java.with_name("javac.exe")
    commands = [[str(javac), "-source", "8", "-target", "8", "-encoding", "UTF-8", "-cp", cp,
                 "-d", str(classes), str(source)],
                [str(java), "-cp", str(classes) + ";" + cp, "qualification.order.TransformerOrderBridge",
                 str(seed / COREMODS), str(root / "coremods-order-test.jar"), str(classes)]]
    for command in commands:
        completed = subprocess.run(command, capture_output=True, text=True,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if completed.returncode:
            raise RuntimeError(completed.stdout + completed.stderr)
        print(completed.stdout.strip(), flush=True)
    result = validate_patch(seed / COREMODS, root / "coremods-order-test.jar")
    result.update({"source_sha256": digest(source), "commands": commands,
                   "compiler": str(javac), "dependencies": {str(p): digest(p) for p in dependencies}})
    write_json(root / "probe-build.json", result)
    return root / "coremods-order-test.jar"


def read_region(path):
    data = path.read_bytes()
    if len(data) < 8192 or len(data) % 4096:
        raise AssertionError("Invalid source region length")
    chunks = {}
    for slot in range(1024):
        location = int.from_bytes(data[slot * 4:slot * 4 + 4], "big")
        offset, sectors = location >> 8, location & 255
        if not offset:
            continue
        start = offset * 4096
        size = struct.unpack_from(">I", data, start)[0]
        if sectors == 0 or size < 1 or size + 4 > sectors * 4096 or start + size + 4 > len(data):
            raise AssertionError("Invalid source region allocation")
        chunks[slot] = data[start:start + 4 + size]
    return data[4096:8192], chunks


def add_metal_blocks(world, definition):
    level = nbtlib.load(world / "level.dat")
    registry = {str(e["K"]): int(e["V"]) for e in level["FML"]["Registries"]["minecraft:blocks"]["ids"]}
    path = world / "region/r.0.0.mca"
    timestamps, chunks = read_region(path)
    slot = 11  # x=180,z=8, already loaded by the source actor builder.
    payload = chunks[slot]
    if payload[4] != 2:
        raise AssertionError("Expected zlib legacy chunk")
    chunk = nbtlib.File.parse(io.BytesIO(zlib.decompress(payload[5:])))
    sections = chunk["Level"]["Sections"]
    section = next((s for s in sections if int(s["Y"]) == 5), None)
    if section is None:
        section = nbtlib.Compound({"Y": nbtlib.Byte(5), "Blocks": nbtlib.ByteArray([0] * 4096),
                                  "Data": nbtlib.ByteArray([0] * 2048),
                                  "BlockLight": nbtlib.ByteArray([0] * 2048),
                                  "SkyLight": nbtlib.ByteArray([-1] * 2048)})
        sections.append(section)
    # Parsed ByteArrays can be backed by immutable bytes; use independent writable arrays.
    for key in ("Blocks", "Data", "Add"):
        if key in section:
            section[key] = nbtlib.ByteArray(list(section[key]))
    if "Add" not in section:
        section["Add"] = nbtlib.ByteArray([0] * 2048)
    witnesses = []
    for number, name in enumerate(METAL_BLOCKS):
        y = 81 + number
        index = ((y & 15) << 8) | (8 << 4) | 4
        low = int(section["Blocks"][index]) & 255
        high = (int(section["Add"][index // 2]) & 255) >> ((index & 1) * 4) & 15
        if low or high:
            raise AssertionError(f"Metal witness position is occupied: 180,{y},8")
        numeric = registry[name]
        section["Blocks"][index] = (numeric & 255) - (256 if numeric & 128 else 0)
        for key, value in (("Add", numeric >> 8), ("Data", 0)):
            shift = (index & 1) * 4
            old = int(section[key][index // 2]) & 255
            byte = (old & ~(15 << shift)) | (value << shift)
            section[key][index // 2] = byte - (256 if byte & 128 else 0)
        witness = {"x": 180, "y": y, "z": 8, "name": name, "metadata": 0}
        definition.append(witness)
        witnesses.append({**witness, "legacy_numeric_id": numeric})
    stream = io.BytesIO()
    chunk.write(stream)
    compressed = b"\x02" + zlib.compress(stream.getvalue())
    chunks[slot] = struct.pack(">I", len(compressed)) + compressed
    header, body = bytearray(4096), bytearray()
    offset = 2
    for index, payload in sorted(chunks.items()):
        sectors = (len(payload) + 4095) // 4096
        if sectors > 255:
            raise AssertionError("Oversize region chunk")
        header[index * 4:index * 4 + 4] = ((offset << 8) | sectors).to_bytes(4, "big")
        body.extend(payload)
        body.extend(b"\x00" * (sectors * 4096 - len(payload)))
        offset += sectors
    path.write_bytes(bytes(header) + timestamps + bytes(body))
    # All unrelated compressed chunk payloads must be byte-for-byte unchanged.
    _, reread = read_region(path)
    if reread != chunks:
        raise AssertionError("Region round-trip changed chunk payloads")
    return witnesses


def metal_item(slot, count=1):
    return nbtlib.Compound({"id": nbtlib.String("basemetals:steel_pickaxe"), "Count": nbtlib.Byte(count),
                           "Damage": nbtlib.Short(17), "Slot": nbtlib.Byte(slot),
                           "tag": nbtlib.Compound({"OrderingWitness": nbtlib.String("keep-exactly"),
                               "Nested": nbtlib.Compound({"note": nbtlib.String("both-migrations")})})})


def prepare_fixture(source, destination):
    copy_fixture(source, destination)
    shutil.copy2(source / "actor-fixture.json", destination / "actor-fixture.json")
    definition = json.loads((destination / "actor-fixture.json").read_text())
    player_path = destination / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
    player = nbtlib.load(player_path)
    for container, slot in (("Inventory", 10), ("EnderItems", 26)):
        if any(int(e["Slot"]) == slot for e in player[container]):
            raise AssertionError("Synthetic metal witness slot is occupied")
        player[container].append(metal_item(slot))
    player["Inventory"][0]["tag"]["OrderingItems"] = nbtlib.List[nbtlib.Compound]([metal_item(0)])
    player.save()
    level = nbtlib.load(destination / WORLD / "level.dat")
    level["Data"]["Player"] = nbtlib.Compound(dict(player))
    level.save()
    blocks = json.loads((destination / "migration-fixture.json").read_text())
    added = add_metal_blocks(destination / WORLD, blocks)
    write_json(destination / "migration-fixture.json", blocks)
    baseline = capture(source, "source")
    baseline["file"] = semantic(player.unpack(), PLAYER_KEYS, enchantments(destination))
    baseline["embedded"] = baseline["file"]
    baseline["adapter"] = baseline["file"]
    write_json(destination / "semantic-source.json", baseline)
    write_json(destination / "mixed-witnesses.json", {"blocks": added, "items":
               "Damaged steel pickaxe in Inventory 10, EnderItems 26, and a nested musket tag"})
    write_json(destination / "fixture-hashes.json", fingerprint(destination / WORLD))
    return baseline


def hook_mod(hook):
    if "/basemetals/" in hook["owner"]:
        return "bm"
    if "/poweradvantage/" in hook["owner"]:
        return "pa"
    raise AssertionError("Unexpected hook owner")


def order_evidence(runtime, mode, label=None):
    event_file = f"transformer-order-events-{label}.jsonl" if label else "transformer-order-events.jsonl"
    events = [json.loads(line) for line in (runtime / event_file).read_text().splitlines()]
    registrations = [e for e in events if e["event"] == "registration"]
    if len(registrations) != 1 or registrations[0]["mode"] != mode:
        raise AssertionError("Missing or duplicated Forge registration evidence")
    registration = registrations[0]
    groups = defaultdict(list)
    for event in events:
        if event["event"] != "application":
            continue
        dump = Path(event["dump"])
        if label:
            dump = Path(f"transformer-order-bytecode-{label}", *dump.parts[1:])
        if digest(runtime / dump) != event["sha256"]:
            raise AssertionError("Bytecode dump hash changed")
        groups[event["class"]].append(event)
    proof = {}
    for suffix, (class_name, methods) in SUFFIXES.items():
        applications = groups[class_name]
        bm, pa = "basemetals_legacy_" + suffix, "poweradvantage_legacy_" + suffix
        expected = [n for n in registration["forced"] if n in (bm, pa)]
        requested = [bm, pa] if mode == "bm-first" else [pa, bm] if mode == "pa-first" else expected
        if expected != requested or [e["transformer"] for e in applications] != expected:
            raise AssertionError(f"Wrong actual application order for {class_name}")
        for event in applications:
            own = "bm" if event["transformer"].startswith("basemetals") else "pa"
            for method in methods:
                before = sum(h["name"] == method and hook_mod(h) == own for h in event["before"])
                after = sum(h["name"] == method and hook_mod(h) == own for h in event["after"])
                if before != 0 or after == 0 or (method != "finalizeLegacyChunk" and after != 1):
                    raise AssertionError(f"Missing or duplicate {own}/{method} injection")
            if event is applications[1] and event["before"] != applications[0]["after"]:
                raise AssertionError("Transformers did not receive the same evolving class node")
        execution = {}
        for method in methods:
            grouped = defaultdict(list)
            for hook in applications[-1]["after"]:
                if hook["name"] == method:
                    grouped[hook["method"]].append(hook_mod(hook))
            mods = ["bm" if n.startswith("basemetals") else "pa" for n in expected]
            pair = mods if method == "finalizeLegacyChunk" else list(reversed(mods))
            if not grouped or any(values != pair * (len(values) // 2) or len(values) % 2 for values in grouped.values()):
                raise AssertionError(f"Wrong or missing emitted hook order: {method}")
            execution[method] = dict(grouped)
        proof[suffix] = {"application_order": expected, "emitted_hook_order": execution,
                         "bytecode_sha256": applications[-1]["sha256"]}
    return {"status": "PASS", "mode": mode, "classes": proof, "registration": registration}


def block_semantics(runtime, label):
    registry = enchantments(runtime)
    return [{"position": [e["x"], e["y"], e["z"]], "name": e["actualName"], "properties": e["properties"],
             **({"nbt": normalized(plain(parse_nbt(e)), registry)} if "nbtBase64" in e else {})}
            for e in json.loads((runtime / f"migration-{label}.json").read_text())]


def checkpoint(runtime, java, launcher, label, first):
    # Each launch gets separate application logs and bytecode; no prior evidence is overwritten.
    events = runtime / "transformer-order-events.jsonl"
    dumps = runtime / "transformer-order-bytecode"
    server = Server(runtime, java, launcher, label)
    try:
        server.snapshot(label)
        actor_command(server, True, "snapshot" if first else "read", label)
        frozen = freeze_result(json.loads((runtime / f"actors-{label}.json").read_text()))
        if frozen["status"] != "PASS":
            raise AssertionError("Entity witnesses were not frozen")
        state = capture(runtime, label)
        retain_player_evidence(runtime, label)
        if first:
            actor_command(server, True, "read", label + "-adapter")
            adapter_freeze = freeze_result(json.loads((runtime / f"actors-{label}-adapter.json").read_text()))
            if adapter_freeze["status"] != "PASS":
                raise AssertionError("Player adapter witnesses were not frozen")
            adapter = capture(runtime, label + "-adapter")
            retain_player_evidence(runtime, label + "-adapter")
            adapter.pop("embedded", None)
        else:
            state.pop("embedded", None)
            adapter_freeze, adapter = frozen, state
        # A preserved legacy world can start without loading WorldGenRegion. Generate a
        # distinct remote chunk per launch so the real guard transformers are applied.
        remote = 1024 if first else 2048
        server.send(f"forceload add {remote} 1024")
        server.send("say ORDER-FORCELOAD-SCHEDULED")
        server.wait("ORDER-FORCELOAD-SCHEDULED", 30)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if events.exists() and events.read_text().count('"class":"net/minecraft/world/gen/WorldGenRegion"') == 2:
                break
            time.sleep(0.2)
        else:
            raise AssertionError("Remote generation did not apply both WorldGenRegion transformers")
        server.send(f"forceload remove {remote} 1024")
        server.send("save-all flush")
        server.wait("Saved the", 30)
    finally:
        server.close()
    proof = order_evidence(runtime, (runtime / "transformer-order.mode").read_text().strip())
    shutil.move(str(events), str(runtime / f"transformer-order-events-{label}.jsonl"))
    shutil.move(str(dumps), str(runtime / f"transformer-order-bytecode-{label}"))
    write_json(runtime / f"order-proof-{label}.json", proof)
    write_json(runtime / f"semantic-{label}.json", state)
    write_json(runtime / f"semantic-{label}-adapter.json", adapter)
    return state, adapter, {"freeze": frozen, "adapter_freeze": adapter_freeze, "order": proof}


def retain_player_evidence(runtime, label):
    folder = runtime / "ordering-evidence" / label
    folder.mkdir(parents=True, exist_ok=False)
    definition = json.loads((runtime / "actor-fixture.json").read_text())
    shutil.copy2(runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat"), folder / "player.dat")
    shutil.copy2(runtime / WORLD / "level.dat", folder / "level.dat")


def assert_staged(runtime):
    for jar in json.loads((runtime / "input-jars.json").read_text()):
        if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
            raise AssertionError("Staged production jar changed")


def migration_backups(runtime):
    return {str(path.relative_to(runtime / WORLD)): digest(path) for path in (runtime / WORLD).rglob("*")
            if path.is_file() and path.name.endswith((".poweradvantage-legacy-backup", ".basemetals-legacy-backup"))}


def backup_provenance(fixture, runtime):
    definition = json.loads((runtime / "actor-fixture.json").read_text())
    files = {}
    for relative in (Path("level.dat"), Path("playerdata") / (definition["playerUuid"] + ".dat")):
        original = digest(fixture / WORLD / relative)
        backups = {}
        for mod in ("basemetals", "poweradvantage"):
            backup = runtime / WORLD / relative.with_name(relative.name + f".{mod}-legacy-backup")
            if backup.is_file():
                backups[mod] = {"sha256": digest(backup), "matches_input": digest(backup) == original}
        files[str(relative)] = {"input_sha256": original, "backups": backups,
                                "original_retained": any(v["matches_input"] for v in backups.values())}
    return {"status": "PASS" if all(f["original_retained"] for f in files.values()) else "FAIL",
            "power_backup_matches_original": all(f["backups"].get("poweradvantage", {}).get("matches_input", False)
                                                  for f in files.values()), "files": files}


def run(args):
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    root = args.output.resolve()
    root.mkdir(parents=True, exist_ok=False)
    profile = target_profile(args.profile_manifest)
    write_json(root / "scope.json", {"profile_manifest": str(args.profile_manifest.resolve()),
               "profile_manifest_sha256": digest(args.profile_manifest), "port": args.port,
               "production_code_changes": False, "production_mod_jars": "Original pinned bytes",
               "test_library_patch": "CoreModEngine registration return-site and observer wrappers only",
               "fixture_scope": "Overworld actors/blocks/tiles plus synthetic registry-backed BaseMetals blocks/items",
               "not_qualified": ["Client/login/GUI", "Other dimensions", "General worldgen behavior",
                                  "Whole modpacks", "Loader discovery order without test forcing"]})
    patched = build_bridge(root, profile, args.java8)
    results, cross_order, source_hashes = {}, {}, {}
    try:
        for name in args.sources:
            source = args.fixtures.resolve() / name
            before = fingerprint(source / WORLD)
            if before != json.loads((source / "fixture-hashes.json").read_text()):
                raise AssertionError(f"Immutable actor fixture changed: {source}")
            source_hashes[name] = (source, before)
            write_json(root / "immutable-source-hashes.json", {
                key: {"path": str(path), "hashes": hashes} for key, (path, hashes) in source_hashes.items()})
            fixture = root / (name + "-mixed-input")
            fixture.mkdir()
            expected = prepare_fixture(source, fixture)
            blocks_source = qualified_baselines()[name]
            all_modes = {}
            for mode in args.orders:
                runtime = root / (name + "-" + mode)
                print(f"START {name}/{mode}: packaged Forge 25, port {args.port}", flush=True)
                launcher = stage(runtime, profile)
                properties = runtime / "server.properties"
                properties.write_text(properties.read_text().replace("server-port=25584", f"server-port={args.port}"))
                shutil.copy2(patched, runtime / COREMODS)
                (runtime / "transformer-order.mode").write_text(mode)
                copy_fixture(fixture, runtime)
                shutil.copy2(fixture / "actor-fixture.json", runtime / "actor-fixture.json")
                converted, adapter, first = checkpoint(runtime, args.java8, launcher, "converted", True)
                backups = migration_backups(runtime)
                write_json(runtime / "ordering-backups.json", backups)
                provenance = backup_provenance(fixture, runtime)
                restarted, _, second = checkpoint(runtime, args.java8, launcher, "restarted", False)
                verdict = {"conversion": compare_actors({k: v for k, v in expected.items() if k != "adapter"}, converted),
                           "adapter": compare_actors({k: v for k, v in expected.items() if k != "embedded"}, adapter),
                           "restart": compare_actors({k: v for k, v in expected.items() if k != "embedded"}, restarted),
                           "idempotence": strict_equal(adapter, restarted),
                           "backup_stability": strict_equal(backups, migration_backups(runtime)),
                           "backup_provenance": provenance,
                           "converted_controls": first, "restart_controls": second}
                # The stock 355 witnesses precede the three test-only metal blocks.
                for label in ("converted", "restarted"):
                    entries = json.loads((runtime / f"migration-{label}.json").read_text())
                    stock = runtime / f"migration-stock-{label}.json"
                    write_json(stock, entries[:-len(METAL_BLOCKS)])
                    verdict["blocks_" + label] = compare_blocks(blocks_source / "migration-source.json", stock)
                    metals = entries[-len(METAL_BLOCKS):]
                    verdict["metals_" + label] = strict_equal(list(METAL_BLOCKS), [e["actualName"] for e in metals])
                assert_staged(runtime)
                all_modes[mode] = {"actors": adapter, "actors_restart": restarted,
                                   "blocks": block_semantics(runtime, "converted"),
                                   "blocks_restart": block_semantics(runtime, "restarted")}
                diagnostics = []
                for log in runtime.glob("console-*.log"):
                    diagnostics.extend({"log": log.name, "line": line} for line in log.read_text(errors="replace").splitlines()
                                       if "/ERROR]" in line or "/FATAL]" in line or "Exception loading entity" in line)
                verdict["diagnostics"] = diagnostics
                results[name + "/" + mode] = verdict
                write_json(runtime / "order-results.json", verdict)
                write_json(root / "results.json", results)
                print(f"END {name}/{mode}: " + ", ".join(f"{k}={v['status']}" for k, v in verdict.items()
                                                        if isinstance(v, dict) and "status" in v), flush=True)
            reference = all_modes[args.orders[0]]
            cross_order[name] = {mode: strict_equal(reference, value) for mode, value in all_modes.items()}
            if fingerprint(fixture / WORLD) != json.loads((fixture / "fixture-hashes.json").read_text()):
                raise AssertionError("Mixed input fixture changed")
    finally:
        unchanged = {name: fingerprint(source / WORLD) == hashes for name, (source, hashes) in source_hashes.items()}
        write_json(root / "source-preservation.json", unchanged)
        if not all(unchanged.values()):
            raise AssertionError("Source world changed")
    write_json(root / "cross-order.json", cross_order)
    failed = any(value.get("status") == "FAIL" for verdict in results.values() for value in verdict.values()
                 if isinstance(value, dict)) or any(v["status"] != "PASS" for modes in cross_order.values() for v in modes.values())
    backup_difference = any(not v["backup_provenance"]["power_backup_matches_original"] for v in results.values())
    write_json(root / "summary.json", {"status": "FAIL" if failed else "PASS_WITH_BACKUP_ORDER_DEPENDENCE" if backup_difference else "PASS",
               "semantic_status": "FAIL" if failed else "PASS", "power_backup_origin_order_dependent": backup_difference,
               "runtimes": len(results),
               "server_launches": len(results) * 2, "cross_order": cross_order, "source_preservation": unchanged,
               "patched_library_sha256": digest(patched)})
    return int(failed)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile-manifest", type=Path, required=True, help="Use archived qualified target113 bytes")
    parser.add_argument("--fixtures", type=Path, required=True, help="Completed frozen actor campaign with immutable sources")
    parser.add_argument("--java8", type=Path, required=True, help="Java 8 JDK bin/java.exe; javac must be adjacent")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25621)
    parser.add_argument("--sources", nargs="+", choices=list(qualified_baselines()), default=["original110", "maintained112"])
    parser.add_argument("--orders", nargs="+", choices=ORDERS, default=list(ORDERS))
    raise SystemExit(run(parser.parse_args()))
