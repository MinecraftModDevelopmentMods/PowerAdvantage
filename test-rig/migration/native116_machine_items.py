"""Fresh old-machine item witnesses; source seeding and native destination are separate leases.

Only this new module owns the six explicit vanilla item expectations. Historical
helpers/oracles are imported unchanged. No destination construction or refill API
is exposed. A measured idle interval proves preservation, not recipe throughput.
"""

import argparse
import copy
from collections import Counter
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import socket
import uuid
import zipfile
import zlib

import nbtlib

import matrix
import native116
from native116_code import Server as NativeServer, code_files
from native116_connected import require_native_boot
from transformer_order import read_region


SOURCES = ("original110", "early110", "maintained110", "legacy112", "maintained112")
CASES = ("wool14", "coal1", "dye4", "enchanted-pick", "enchanted-book", "nested-chest")
MACHINES = ("poweradvantage:item_conveyor", "poweradvantage:item_filter_inventory",
            "electricadvantage:electric_furnace", "steamadvantage:steam_furnace")
OLD_POWER_C = "DBE2226C4A1FE505A94A411EA68540CAEC40186598847BAB936CE16EC8E2FA01"
FROZEN_SOURCE_PROBE = "38565B6671E249B6E12D5B238355FD7D48218B261789E7E92316F9D9DC29F94A"
ENCHANTMENTS = {32: "minecraft:efficiency", 34: "minecraft:unbreaking"}
HISTORY = Path(__file__).with_name("native116-historical-inputs.json")
PLAYER_UUID = uuid.UUID("a614e658-6270-33c3-9297-7ce80908964f")
SOURCE_CONSOLE_ROOT = native116.LEGACY_POWER / "run-actor-migration/20261007-bridge112-strict-r2"
SOURCE_CONSOLE_PINS = {
    "original110": "B64C0E7C0845F00AA2843DD68810E74294F85C51891AFB2AFE7F0DC6881CD2D6",
    "legacy112": "FD6415C1B73609EF2B18394158B1F0DFEF3FC29F9388545C010F03CEE476175E",
}
KNOWN_SOURCE_LINES = (
    "[main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing",
    "[Server thread/ERROR] [FML]: Trying to activate the universal filled bucket too late. Call it statically in your Mods class. Mod: Base Metals",
)
LEGACY112_INTEGRATION_LINES = (
    "[Server thread/FATAL] [orespawn]: Loading Integration For orespawn",
    "[Server thread/FATAL] [orespawn]: Loading Integration For basemetals",
)
SOURCE_DIAGNOSTIC_POLICY = {"original110": KNOWN_SOURCE_LINES, "legacy112": LEGACY112_INTEGRATION_LINES}
SOURCE_WITNESS = "SOURCE_WITNESS"
SOURCE_WITH_DIAGNOSTICS = "SOURCE_WITNESS_WITH_KNOWN_DIAGNOSTICS"
SOURCE_SUCCESS = (SOURCE_WITNESS, SOURCE_WITH_DIAGNOSTICS)
SOURCE_FAILURE = re.compile(r"\b(?:ERROR|FATAL|NoSuch[\w.$]*|[\w.$]*Exception[\w.$]*|[\w.$]+Error)\b", re.IGNORECASE)


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def checked(path, checksum):
    path = Path(path).resolve()
    if not re.fullmatch(r"[0-9A-Fa-f]{64}", checksum) or matrix.digest(path) != checksum.upper():
        raise AssertionError("Pinned file changed: " + str(path))
    return path


def history_custody():
    hashes = read(HISTORY)["hashes"]
    for name, checksum in hashes.items():
        checked(native116.RIG / name, checksum)
    return {"receiptSha256": matrix.digest(HISTORY), "files": hashes}


def source_probe(profile):
    probes = [jar for jar in profile[2] if jar.name.startswith("AdvantageMigrationProbe-")]
    if len(probes) != 1 or matrix.digest(probes[0]) != FROZEN_SOURCE_PROBE:
        raise AssertionError("Source requires the inspected frozen advprobe serializer/freeze jar; no replacement inferred")
    with zipfile.ZipFile(probes[0]) as archive:
        probe = archive.read("zone/moddev/mc/advantageworks/migration/LegacyProbe.class")
        if any(token not in probe for token in (b"migration-operate.allow", b"field_175730_i", b"nbtBase64")):
            raise AssertionError("Frozen source serializer/freeze channel missing")
        actors = archive.read("zone/moddev/mc/advantageworks/migration/LegacyActors.class")
        if b"StoredEnchantments" in actors:
            raise AssertionError("Uninspected source actor adapter changed")
        if b"playerAdapter" not in actors or b"AdvWorksPlayer" not in actors:
            raise AssertionError("Frozen source native player read/snapshot adapter missing")
    return dict(jar=str(probes[0]), sha256=matrix.digest(probes[0]), command="advprobe snapshot",
                nativeSourceHeldFlag="NOT_EXPOSED_BY_ARCHIVED_PROBE",
                freeze="Pinned WorldTickEvent.START clears tickable tiles while migration-operate.allow is absent",
                proof="Actual old tile.writeToNBT ItemStacks plus stopped raw chunk NBT; no synthetic native fields",
                playerCommand="advprobe actors-read")


def items(case, old_chest="Chest", stored_enchantments=None):
    def stack(identity, count, damage=0, tag=None):
        result = dict(id="minecraft:" + identity, Count=count, Damage=damage)
        if tag is not None:
            result["tag"] = tag
        return result
    ench = [dict(id=32, lvl=3), dict(id=34, lvl=2)]
    values = {
        "wool14": stack("wool", 7, 14),
        "coal1": stack("coal", 5, 1),
        "dye4": stack("dye", 9, 4),
        "enchanted-pick": stack("diamond_pickaxe", 1, 17, {
            "ench": ench, "RepairCost": 4, "AdvMachineItems": {"note": "unchanged", "number": 19}}),
        "enchanted-book": stack("enchanted_book", 1, tag={"StoredEnchantments": copy.deepcopy(stored_enchantments or ench)}),
    }
    nested = [dict(Slot=index, **copy.deepcopy(values[name])) for index, name in enumerate(CASES[:-1])]
    values["nested-chest"] = stack("chest", 1, tag={"BlockEntityTag": {
        "id": old_chest, "Items": nested}, "AdvMachineItems": "nested-vanilla-inventory"})
    if case not in values:
        raise ValueError("Unknown machine item case: " + case)
    return copy.deepcopy(values[case])


def modern_item(value, registry=None):
    """Exact fixed expectation for these witnesses, never a general DFU substitute."""
    result = copy.deepcopy(value)
    identity, damage = result["id"], result.pop("Damage", 0)
    variants = {("minecraft:wool", 14): "minecraft:red_wool",
                ("minecraft:coal", 1): "minecraft:charcoal",
                ("minecraft:dye", 4): "minecraft:lapis_lazuli"}
    if (identity, damage) in variants:
        result["id"] = variants[(identity, damage)]
    elif identity == "minecraft:diamond_pickaxe":
        result.setdefault("tag", {})["Damage"] = damage
    elif damage != 0:
        raise ValueError("Unexpected metadata in a fixed witness")
    tag = result.get("tag", {})
    for old, new in (("ench", "Enchantments"), ("StoredEnchantments", "StoredEnchantments")):
        if old in tag:
            tag[new] = [dict(entry, id=(registry or ENCHANTMENTS)[entry["id"]]) for entry in tag.pop(old)]
    if "BlockEntityTag" in tag:
        chest = tag["BlockEntityTag"]
        if chest["id"] not in ("Chest", "minecraft:chest"):
            raise ValueError("Unexpected nested block entity")
        chest["id"] = "minecraft:chest"
        chest["Items"] = [modern_item(entry, registry) for entry in chest["Items"]]
    return result


def snbt(value, key=None):
    if isinstance(value, dict):
        return "{" + ",".join(name + ":" + snbt(entry, name) for name, entry in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ",".join(snbt(entry) for entry in value) + "]"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=True)
    if type(value) is int:
        return str(value) + ("b" if key in ("Count", "Slot") else "s" if key in ("Damage", "lvl") or key == "id" else "")
    raise TypeError("Unsupported witness NBT value")


def inventory(case, machine, old_chest, stored_enchantments=None):
    result = [dict(Slot=0, **items(case, old_chest, stored_enchantments))]
    if machine == "poweradvantage:item_filter_inventory":
        template = dict(Slot=1, **items(case, old_chest, stored_enchantments))
        template["Count"] = 1
        result.append(template)
    return result


def enchantment_registry(level_file):
    level = nbtlib.load(level_file)
    entries = level["FML"]["Registries"]["minecraft:enchantments"]["ids"]
    pairs = [(int(entry["V"]), str(entry["K"])) for entry in entries]
    if len({number for number, _ in pairs}) != len(pairs) or len({name for _, name in pairs}) != len(pairs):
        raise AssertionError("Ambiguous saved source enchantment registry")
    by_name = {name: number for number, name in pairs}
    steam = sorted((number, name) for number, name in pairs if name.startswith("steamadvantage:"))
    if not steam or by_name.get("minecraft:efficiency") != 32 or by_name.get("minecraft:unbreaking") != 34:
        raise AssertionError("Saved registry cannot support the exact numeric book/pick witnesses")
    return dict(names={str(number): name for number, name in pairs},
                stored=[dict(id=by_name["minecraft:efficiency"], lvl=3)] + [dict(id=number, lvl=1) for number, _ in steam])


def registry_sha256(registry):
    return hashlib.sha256(json.dumps(registry, sort_keys=True, separators=(",", ":")).encode("ascii")).hexdigest().upper()


def names(definition):
    return {int(number): name for number, name in definition.get("enchantmentNames", ENCHANTMENTS).items()}


def select_definition(snapshot, case, source_key):
    selected = []
    for index, name in enumerate(MACHINES):
        available = sorted((entry for entry in snapshot if entry["actualName"] == name),
                           key=lambda entry: (entry["x"], entry["y"], entry["z"]))
        if not available:
            raise AssertionError("Approved source lacks an existing machine: " + name)
        entry = available[0]
        if "nbtBase64" not in entry:
            raise AssertionError("Existing machine has no serializer evidence")
        selected.append(dict(id="MI-" + str(index), station="machine-items", kind=name.split(":")[1],
                             name=name, **{key: entry[key] for key in ("x", "y", "z")}))
    positions = {(entry["x"], entry["y"], entry["z"]) for entry in selected}
    if len(positions) != len(MACHINES):
        raise AssertionError("Duplicate existing machine position")
    low = [min(entry[key] for entry in selected) for key in ("x", "y", "z")]
    high = [max(entry[key] for entry in selected) for key in ("x", "y", "z")]
    if low[1] < 64 or high[1] > 160 or any(b - a > 512 for a, b in zip(low, high)):
        raise ValueError("Existing witnesses exceed unchanged ConnectedSession bounds")
    return dict(format=1, sourceProfile=source_key, case=case, bounds=low + high, blocks=selected)


def seed_commands(definition):
    chest = "Chest" if definition["sourceProfile"].endswith("110") else "minecraft:chest"
    return ["blockdata %d %d %d %s" % (entry["x"], entry["y"], entry["z"], snbt({
        "Items": inventory(definition["case"], entry["name"], chest, definition.get("storedEnchantments")),
        "ForgeData": {"AdvConnectedWitness": entry["id"]}})) for entry in definition["blocks"]]


def require_command(command, *, source_seeds=(), operated=False):
    if command in ("stop", "save-all flush") or command in source_seeds:
        return
    if source_seeds:
        if re.fullmatch(r"advprobe (snapshot|actors-read) [A-Za-z0-9_-]+", command):
            return
        raise AssertionError("Forbidden old source command: " + command)
    if re.fullmatch(r"advconnected snapshot [A-Za-z0-9_-]+", command):
        return
    if re.fullmatch(r"advactors read [A-Za-z0-9_-]+", command):
        return
    match = re.fullmatch(r"advconnected step ([0-9]+) [A-Za-z0-9_-]+", command)
    if operated and match and 1 <= int(match[1]) <= 4000:
        return
    raise AssertionError("Forbidden machine-items command: " + command)


class ObservedServer(matrix.Server):
    def __init__(self, *args, source_seeds=(), **kwargs):
        self.source_seeds = tuple(source_seeds)
        self.operated = False
        self.commands = []
        super().__init__(*args, **kwargs)

    def send(self, command):
        require_command(command, source_seeds=self.source_seeds, operated=self.operated)
        self.commands.append(command)
        super().send(command)

    def close(self):
        try:
            super().close()
        finally:
            matrix.write_json(Path(self.log.name).with_suffix(".commands.json"), self.commands)


class DestinationServer(ObservedServer, NativeServer):
    pass


def document_items(document, definition, *, modern, ticks=0, previous=None):
    if document.get("held") is not True or document.get("buildCallsThisProcess") != 0:
        raise AssertionError("Construction or unfrozen observation is not preservation")
    if (document.get("requestedTicks"), document.get("advancedTicks")) != (ticks, ticks):
        raise AssertionError("Missing exact native tick/freeze interval")
    return serialized_items(document["snapshot"], definition, modern=modern, previous=previous)


def source_items(rows, definition):
    if not isinstance(rows, list):
        raise AssertionError("Old advprobe proof must be its actual snapshot array, not synthesized native protocol flags")
    return serialized_items(rows, definition, modern=False, native_fields=False)


def serialized_items(rows, definition, *, modern, previous=None, native_fields=True):
    expected = {entry["id"]: entry for entry in definition["blocks"]}
    if len(rows) != len(expected) or {row["id"] for row in rows} != expected.keys():
        raise AssertionError("Missing, duplicate or unexpected machine witness")
    result = {}
    chest = "Chest" if definition["sourceProfile"].endswith("110") else "minecraft:chest"
    prior = {row["id"]: row for row in previous["snapshot"]} if previous else {}
    for row in rows:
        spec = expected[row["id"]]
        if any(row[key] != spec[key] for key in ("name", "kind", "station", "x", "y", "z")):
            raise AssertionError("Machine witness definition moved/changed")
        if row["actualName"] != spec["name"] or (native_fields and row.get("witness") != spec["id"]):
            raise AssertionError("Machine/witness lost or replaced")
        tile = matrix.plain(matrix.parse_nbt(row))
        if tile.get("ForgeData", {}).get("AdvConnectedWitness") != spec["id"]:
            raise AssertionError("Actual tile NBT source witness lost")
        if [tile.get(key) for key in ("x", "y", "z")] != [spec[key] for key in ("x", "y", "z")]:
            raise AssertionError("Tile coordinates changed")
        expected_items = inventory(definition["case"], spec["name"], chest, definition.get("storedEnchantments"))
        if modern:
            expected_items = [modern_item(stack, names(definition)) for stack in expected_items]
        observed = tile.get("Items")
        if not isinstance(observed, list) or observed != expected_items:
            raise AssertionError("Exact item mismatch at " + spec["id"] + ": " + json.dumps({
                "expected": expected_items, "actual": observed}, sort_keys=True))
        if spec["id"] in prior and row.get("objectIdentity") != prior[spec["id"]].get("objectIdentity"):
            raise AssertionError("Tile object replaced during the same measured interval")
        if native_fields and not isinstance(row.get("objectIdentity"), int):
            raise AssertionError("Missing actual tile identity")
        result[spec["id"]] = dict(items=observed, properties=row["properties"], tileId=tile["id"])
    return result


def observe_source(server, runtime, label):
    server.send("advprobe snapshot " + label)
    server.wait("ADVPROBE COMPLETE snapshot", 120)
    return read(runtime / ("migration-" + label + ".json"))


def seed_player(runtime, definition):
    """A NEW disposable file only, before the old native player-file read."""
    path = runtime / matrix.WORLD / "playerdata" / (str(PLAYER_UUID) + ".dat")
    if path.exists():
        raise FileExistsError("Never overwrite an inherited player file, even on a disposable copy: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    book = dict(Slot=0, **items("enchanted-book", stored_enchantments=definition["storedEnchantments"]))
    most, least = PLAYER_UUID.int >> 64, PLAYER_UUID.int & ((1 << 64) - 1)
    signed = lambda number: number - (1 << 64) if number >= 1 << 63 else number
    data = nbtlib.parse_nbt('{DataVersion:%d,UUIDMost:%dL,UUIDLeast:%dL,Inventory:%s,EnderItems:[],'
        'Pos:[144.5d,80.0d,8.5d],Motion:[0.0d,0.0d,0.0d],Rotation:[0.0f,0.0f],'
        'Dimension:0,Health:17.0f,foodLevel:16,SelectedItemSlot:0}' % (
            512 if definition["sourceProfile"].endswith("110") else 1343, signed(most), signed(least), snbt([book])))
    nbtlib.File(data, gzipped=True).save(path)
    matrix.write_json(runtime / "actor-fixture.json", dict(playerName="AdvWorksPlayer", playerUuid=str(PLAYER_UUID),
                                                         minX=128, maxX=191, universalBucket=False))
    return matrix.digest(path)


def player_items(data, definition, *, modern):
    expected = dict(Slot=0, **items("enchanted-book", stored_enchantments=definition["storedEnchantments"]))
    if modern:
        expected = modern_item(expected, names(definition))
    if data.get("Inventory") != [expected] or data.get("EnderItems", []) != []:
        raise AssertionError("Exact player StoredEnchantments/inventory mismatch: " + json.dumps({
            "expected": [expected], "actual": data.get("Inventory")}, sort_keys=True))
    if "UUID" in data:
        actual_uuid = uuid.UUID(int=sum((int(word) & 0xffffffff) << (96 - index * 32)
                                       for index, word in enumerate(data["UUID"])))
    else:
        actual_uuid = uuid.UUID(int=((int(data["UUIDMost"]) & ((1 << 64) - 1)) << 64)
                                   | (int(data["UUIDLeast"]) & ((1 << 64) - 1)))
    if actual_uuid != PLAYER_UUID:
        raise AssertionError("Player UUID changed")
    return expected


def observe_player(server, runtime, label, definition, *, modern):
    server.send(("advactors read " if modern else "advprobe actors-read ") + label)
    server.wait("ADVACTORS COMPLETE " + label, 120)
    record = read(runtime / ("actors-" + label + ".json"))
    if record.get("playerAdapter") is not True or record.get("frozen") is not True:
        raise AssertionError("No actual native player-file read was performed")
    return player_items(matrix.plain(matrix.parse_nbt(record["player"])), definition, modern=modern)


def stopped_player(runtime, label, definition, *, modern):
    original = runtime / matrix.WORLD / "playerdata" / (str(PLAYER_UUID) + ".dat")
    target = runtime / "machine-items-evidence" / label / "player.dat"
    shutil.copy2(original, target)
    player_items(matrix.plain(nbtlib.load(target).unpack()), definition, modern=modern)
    return matrix.digest(target)


def observe(server, runtime, label, ticks=0):
    server.send(f"advconnected step {ticks} {label}" if ticks else f"advconnected snapshot {label}")
    server.wait("ADVCONNECTED COMPLETE " + label, max(120, ticks / 20 + 90))
    document = read(runtime / ("connected-" + label + ".json"))
    if document["snapshot"] != read(runtime / ("migration-" + label + ".json")):
        raise AssertionError("Probe snapshot channels disagree")
    return document


def decode_chunk(payload):
    if len(payload) < 5 or int.from_bytes(payload[:4], "big") != len(payload) - 4:
        raise ValueError("Malformed saved chunk length")
    decoder = {1: gzip.decompress, 2: zlib.decompress, 3: lambda raw: raw}[payload[4]]
    return matrix.plain(nbtlib.File.parse(io.BytesIO(decoder(payload[5:]))).unpack())


def stopped_evidence(runtime, label, definition, document, *, modern):
    """Call only after close: retain exact raw chunks and independently read tile items."""
    folder = runtime / "machine-items-evidence" / label
    folder.mkdir(parents=True, exist_ok=False)
    matrix.write_json(folder / "snapshot.json", document)
    cache = {}
    for spec in definition["blocks"]:
        cx, cz = spec["x"] // 16, spec["z"] // 16
        key = (cx, cz)
        if key not in cache:
            region = runtime / matrix.WORLD / "region" / ("r.%d.%d.mca" % (cx // 32, cz // 32))
            _, chunks = read_region(region)
            payload = chunks[(cx & 31) + (cz & 31) * 32]
            (folder / ("%d_%d.chunk" % key)).write_bytes(payload)
            cache[key] = decode_chunk(payload)
    audit_raw(folder, definition, document, modern=modern)
    if definition.get("playerBook"):
        stopped_player(runtime, label, definition, modern=modern)
    hashes = matrix.fingerprint(folder)
    matrix.write_json(folder / "receipt.json", dict(status="PASS", files=hashes, closedBeforeCapture=True))
    return hashes


def audit_raw(folder, definition, document, *, modern):
    rows = document["snapshot"] if modern else document
    live = {row["id"]: matrix.plain(matrix.parse_nbt(row)) for row in rows}
    for spec in definition["blocks"]:
        key = (spec["x"] // 16, spec["z"] // 16)
        chunk = decode_chunk((folder / ("%d_%d.chunk" % key)).read_bytes())
        expected_version = 2586 if modern else 512 if definition["sourceProfile"].endswith("110") else 1343
        if chunk.get("DataVersion") != expected_version:
            raise AssertionError("Wrong native saved chunk DataVersion")
        level = chunk["Level"]
        if (level["xPos"], level["zPos"]) != key:
            raise AssertionError("Saved chunk coordinates changed")
        matches = [tile for tile in level["TileEntities"] if all(tile.get(k) == spec[k] for k in ("x", "y", "z"))]
        if len(matches) != 1 or matches[0].get("Items") != live[spec["id"]].get("Items"):
            raise AssertionError("Stopped raw tile differs from live old/native ItemStacks")
        if matches[0].get("id") != live[spec["id"]].get("id"):
            raise AssertionError("Stopped raw tile identity differs")


def plan(routes_path, routes_sha256, source_keys):
    routes_path = checked(routes_path, routes_sha256)
    custody = history_custody()
    rows = read(routes_path)["routes"]
    if not source_keys or len(source_keys) != len(set(source_keys)) or set(source_keys) - set(SOURCES):
        raise ValueError("Explicit unique approved old110/112 profiles required")
    result = dict(format=1, scope="NEW_DISPOSABLE_MACHINE_ITEMS_NOT_HISTORICAL_ORACLE",
                  historical=custody, routePlan=str(routes_path), routePlanSha256=matrix.digest(routes_path),
                  runnerSha256=matrix.digest(Path(__file__)), sources=[], status="PLAN_ONLY_NO_MC")
    for key in source_keys:
        selected = [row for row in rows if row["kind"] == "frozen" and row["id"] == "legacy/" + key]
        if len(selected) != 1 or selected[0]["sourceKey"] != key:
            raise AssertionError("Missing unique approved frozen source route")
        row = selected[0]
        source = Path(row["source"]).resolve()
        before = matrix.fingerprint(source)
        for name in ("sourceManifest", "sourceHashes"):
            checked(row[name], row[name + "Sha256"])
        checked(source / row["sourceSnapshot"], row["sourceSnapshotSha256"])
        if matrix.fingerprint(source / matrix.WORLD) != read(row["sourceHashes"]):
            raise AssertionError("Canonical world differs from approved frozen receipt")
        profile = native116.pinned_profile(Path(row["sourceManifest"]), key)
        channel = source_probe(profile)
        expected = sorted((jar.name, matrix.digest(jar)) for jar in profile[2])
        # Approved frozen fixtures may contain only worlds/snapshots, not server code.
        # The separately sealed archived profile supplies the exact old runtime.
        if (source / "mods").exists() and sorted((jar.name, matrix.digest(jar)) for jar in (source / "mods").glob("*.jar")) != expected:
            raise AssertionError("Canonical old jar set differs from approved source profile")
        registry = enchantment_registry(source / matrix.WORLD / "level.dat")
        console = canonical_source_console(key)
        definitions = {case: select_definition(read(source / row["sourceSnapshot"]), case, key) for case in CASES}
        for case, definition in definitions.items():
            definition.update(enchantmentNames=registry["names"], storedEnchantments=registry["stored"],
                              playerBook=case == "enchanted-book")
        if matrix.fingerprint(source) != before:
            raise AssertionError("Canonical source changed during plan")
        result["sources"].append(dict(route=row, runtimeFingerprint=before, definitions=definitions,
                                      seedCode=code_files(profile[0]), launcher=profile[1], jars=expected,
                                      sourceChannel=channel,
                                      canonicalConsole=console, sourceRegistrySha256=registry_sha256(registry),
                                      canonicalLayout="world-and-snapshots" if not (source / "mods").exists() else "runtime"))
    result["sourceBoots"] = len(source_keys) * len(CASES)
    result["destinationBoots"] = 3 * result["sourceBoots"]
    result["manualGaps"] = ["Source blockdata acceptance needs leased old native boot proof",
        "No recipe throughput assertion: exact item preservation across measured 512 ticks only",
        "Destination requires separately frozen PowerD manifest and fresh explicit lease"]
    result["playerBook"] = "One NEW disposable player file in each source enchanted-book case; actual old read/save before fingerprint; native read/save on all destination boots"
    return result


def load_plan(path, checksum):
    value = read(checked(path, checksum))
    if value.get("format") != 1 or value.get("runnerSha256") != matrix.digest(Path(__file__)):
        raise AssertionError("Plan format/runner changed; prepare a new plan")
    if value["historical"] != history_custody():
        raise AssertionError("Historical source helpers/oracles changed")
    checked(value["routePlan"], value["routePlanSha256"])
    for source in value["sources"]:
        if source["canonicalConsole"] != canonical_source_console(source["route"]["sourceKey"]):
            raise AssertionError("Pinned canonical source console changed")
    return value


def new_root(path, protected):
    root = Path(path).resolve()
    for value in protected:
        native116.require_disjoint(root, Path(value))
    if root.exists():
        raise FileExistsError(root)
    return root


def configure(runtime, definition, port, *, destination=True):
    matrix.write_json(runtime / "connected-fixture.json", definition)
    matrix.write_json(runtime / "migration-fixture.json", definition["blocks"])
    if destination:
        for marker in ("advantage-connected.allow", "migration-operate.allow"):
            (runtime / marker).touch()
    elif any((runtime / marker).exists() for marker in ("advantage-connected.allow", "migration-operate.allow")):
        raise AssertionError("Old source must retain the archived probe's frozen marker state")
    if not destination:
        # Old dedicated servers log FileNotFoundException for absent admin lists.
        for filename in ("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json"):
            path = runtime / filename
            if not path.exists():
                matrix.write_json(path, [])
            elif read(path) != []:
                raise AssertionError("Disposable old source admin list is not empty: " + filename)
    props = runtime / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={port}"))
    if any((runtime / name).exists() for name in ("connected-build.allow", "connected-built.json")):
        raise AssertionError("Source construction authority must never be staged")


def clean_log(path, *, modern):
    if modern:
        require_native_boot(path)
        failures = [line for line in path.read_text(encoding="utf-8", errors="strict").splitlines() if SOURCE_FAILURE.search(line)]
        if failures:
            raise AssertionError("Target log has no source-diagnostic allowance: " + json.dumps(failures))
    else:
        native116.require_clean_log(path)
        if "Done (" not in path.read_text(encoding="utf-8", errors="replace"):
            raise AssertionError("Missing old source startup proof")


def source_line_identity(line):
    # The clock changes on every boot; all remaining bytes stay exact.
    return re.sub(r"^\[(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d\] ", "", line, count=1)


def canonical_source_console(profile):
    if profile not in SOURCE_CONSOLE_PINS:
        raise ValueError("No approved retained source-console pin for profile: " + profile)
    path = checked(SOURCE_CONSOLE_ROOT / profile / "console-source.log", SOURCE_CONSOLE_PINS[profile])
    lines = path.read_text(encoding="utf-8", errors="strict").splitlines()
    return dict(profile=profile, path=str(path), sha256=matrix.digest(path),
                knownRawLines=[line for line in lines if source_line_identity(line) in SOURCE_DIAGNOSTIC_POLICY[profile]])


def source_log_report(path, profile, authority):
    """Only hash-pinned, profile-specific historical diagnostics may be witnessed."""
    if authority != canonical_source_console(profile):
        raise AssertionError("Source diagnostic authority/profile changed")
    raw = path.read_text(encoding="utf-8", errors="strict").splitlines()
    if not any("Done (" in line for line in raw):
        raise AssertionError("Missing old source startup proof")
    historical = Counter(source_line_identity(line) for line in authority["knownRawLines"])
    actual = [line for line in raw if SOURCE_FAILURE.search(line)]
    counts = Counter(source_line_identity(line) for line in actual)
    failures = [line for line in actual if source_line_identity(line) not in historical]
    repeated = {line: count for line, count in counts.items() if count > historical[line]}
    if failures or repeated:
        raise AssertionError("Unapproved source ERROR/exception or diagnostic count: " + json.dumps({
            "rawLines": failures, "excessCounts": repeated}, sort_keys=True))
    diagnostics = [dict(status="KNOWN_SOURCE_DIAGNOSTIC", identity=identity,
        canonicalSourceLog=authority["path"], canonicalSourceLogSha256=authority["sha256"],
        canonicalRawLines=[line for line in authority["knownRawLines"] if source_line_identity(line) == identity],
        canonicalCount=historical[identity], actualRawLines=[line for line in actual if source_line_identity(line) == identity],
        actualCount=count) for identity, count in sorted(counts.items())]
    return dict(status=SOURCE_WITH_DIAGNOSTICS if diagnostics else SOURCE_WITNESS, profile=profile,
        canonicalSourceLog=authority["path"], canonicalSourceLogSha256=authority["sha256"],
        actualLogSha256=matrix.digest(path), diagnostics=diagnostics, actualDiagnosticCount=len(actual),
        canonicalDiagnosticCount=sum(historical.values()), timestampPolicy="Clock prefix only; all other text and count limits exact")


def hold_gate(root):
    if (root / "HOLD").exists():
        raise InterruptedError("HOLD requested; previous server closed, no further boots")


def staged_inputs(runtime, profile):
    if code_files(runtime) != staging_code(profile[0]):
        raise AssertionError("Staged runtime code changed")
    expected = sorted((jar.name, matrix.digest(jar)) for jar in profile[2])
    if sorted((jar.name, matrix.digest(jar)) for jar in (runtime / "mods").glob("*.jar")) != expected:
        raise AssertionError("Staged runtime mods changed")


def staging_code(seed):
    return {name: value for name, value in code_files(seed).items() if "/" in name or "installer" not in name}


def require_lease(port):
    if port != 25574:
        raise ValueError("Only an explicitly granted machine-items port25574 lease is accepted")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", port))


def run_source(args):
    approval = load_plan(args.plan, args.plan_sha256)
    protected = [args.plan, approval["routePlan"]]
    for source in approval["sources"]:
        protected += [source["route"]["source"], source["route"]["sourceManifest"], source["route"]["sourceHashes"], source["canonicalConsole"]["path"]]
    root = new_root(args.output, protected)
    require_lease(args.lease_port)
    receipt = dict(status="RUNNING", scope="NEW_STOPPED_OLD_MACHINE_ITEMS_SOURCE_ONLY",
                   plan=str(args.plan.resolve()), planSha256=args.plan_sha256.upper(), rows=[])
    root.mkdir(parents=True)
    try:
        for source in approval["sources"]:
            row = source["route"]
            canonical = Path(row["source"])
            profile = native116.pinned_profile(Path(row["sourceManifest"]), row["sourceKey"])
            if source_probe(profile) != source["sourceChannel"]:
                raise AssertionError("Old source command channel changed")
            for case, definition in source["definitions"].items():
                hold_gate(root)
                if matrix.fingerprint(canonical) != source["runtimeFingerprint"] or code_files(profile[0]) != source["seedCode"]:
                    raise AssertionError("Approved canonical source/code changed")
                runtime = root / row["sourceKey"] / case
                launcher = matrix.stage(runtime, profile)
                staged_jars = sorted((jar.name, matrix.digest(jar)) for jar in (runtime / "mods").glob("*.jar"))
                expected_code = {name: value for name, value in source["seedCode"].items() if "/" in name or "installer" not in name}
                if staged_jars != [tuple(entry) for entry in source["jars"]] or code_files(runtime) != expected_code:
                    raise AssertionError("Fresh staged old runtime differs from sealed plan")
                matrix.copy_fixture(canonical, runtime)
                configure(runtime, definition, args.lease_port, destination=False)
                player_seed = seed_player(runtime, definition) if definition.get("playerBook") else None
                commands = seed_commands(definition)
                try:
                    hold_gate(root)
                    server = ObservedServer(runtime, args.java8, launcher, "source", source_seeds=commands)
                    try:
                        observe_source(server, runtime, "before-seed")
                        for command in commands:
                            server.send(command)
                        captured = observe_source(server, runtime, "source")
                        source_items(captured, definition)
                        if definition.get("playerBook"):
                            observe_player(server, runtime, "source-book", definition, modern=False)
                        server.send("save-all flush")
                    finally:
                        server.close()
                    staged_inputs(runtime, profile)
                    source_log = source_log_report(runtime / "console-source.log", row["sourceKey"], source["canonicalConsole"])
                    matrix.write_json(runtime / "source-log-diagnostics.json", source_log)
                    registry = enchantment_registry(runtime / matrix.WORLD / "level.dat")
                    if registry_sha256(registry) != source["sourceRegistrySha256"]:
                        raise AssertionError("Stopped source saved Forge enchantment registry changed")
                    version = "1.10.2" if row["sourceKey"].endswith("110") else "1.12.2"
                    if "Starting minecraft server version " + version not in (runtime / "console-source.log").read_text(encoding="utf-8", errors="replace"):
                        raise AssertionError("Missing exact old native Minecraft boot proof")
                    evidence = stopped_evidence(runtime, "source", definition, captured, modern=False)
                    receipt["rows"].append(dict(runtime=str(runtime), sourceKey=row["sourceKey"], case=case,
                        definition=definition, status=source_log["status"], sourceLog=source_log,
                        sourceRegistrySha256=source["sourceRegistrySha256"], witnessChecks="PASS",
                        stoppedCleanly=True, rawEvidence=evidence,
                        playerSeedSha256=player_seed,
                        worldFingerprint=matrix.fingerprint(runtime / matrix.WORLD),
                        runtimeFingerprint=matrix.fingerprint(runtime)))
                finally:
                    if matrix.fingerprint(canonical) != source["runtimeFingerprint"]:
                        raise AssertionError("Protected canonical source changed")
                matrix.write_json(root / "source-receipt.json", receipt)
                print(row["sourceKey"] + "/" + case + ": " + source_log["status"], flush=True)
        receipt["status"] = SOURCE_WITH_DIAGNOSTICS if any(row["status"] == SOURCE_WITH_DIAGNOSTICS for row in receipt["rows"]) else SOURCE_WITNESS
    except BaseException as error:
        receipt.update(status="FAIL", error=repr(error))
        raise
    finally:
        matrix.write_json(root / "source-receipt.json", receipt)
    return receipt


def source_receipt(path, checksum):
    receipt = read(checked(path, checksum))
    approval = load_plan(Path(receipt["plan"]), receipt["planSha256"])
    expected = {(source["route"]["sourceKey"], case): definition for source in approval["sources"]
                for case, definition in source["definitions"].items()}
    if receipt.get("status") not in SOURCE_SUCCESS or receipt.get("scope") != "NEW_STOPPED_OLD_MACHINE_ITEMS_SOURCE_ONLY":
        raise AssertionError("Source preparation incomplete or failed")
    for source in approval["sources"]:
        if matrix.fingerprint(Path(source["route"]["source"])) != source["runtimeFingerprint"]:
            raise AssertionError("Protected canonical source changed after generation")
    rows = receipt["rows"]
    if len(rows) != len(expected) or {(row["sourceKey"], row["case"]) for row in rows} != expected.keys():
        raise AssertionError("Fresh source receipt case coverage changed")
    for row in rows:
        runtime = Path(row["runtime"])
        if row.get("status") not in SOURCE_SUCCESS or row.get("witnessChecks") != "PASS" or row.get("stoppedCleanly") is not True:
            raise AssertionError("Unclosed/unverified source")
        if row["definition"] != expected[(row["sourceKey"], row["case"])]:
            raise AssertionError("Fresh source definition differs from approved plan")
        if matrix.fingerprint(runtime) != row["runtimeFingerprint"] or matrix.fingerprint(runtime / matrix.WORLD) != row["worldFingerprint"]:
            raise AssertionError("Fresh stopped source changed")
        authority = next(source for source in approval["sources"] if source["route"]["sourceKey"] == row["sourceKey"])
        log_report = source_log_report(runtime / "console-source.log", row["sourceKey"], authority["canonicalConsole"])
        if log_report != row["sourceLog"] or read(runtime / "source-log-diagnostics.json") != log_report or row["status"] != log_report["status"]:
            raise AssertionError("Source diagnostic custody/status changed")
        if registry_sha256(enchantment_registry(runtime / matrix.WORLD / "level.dat")) != row["sourceRegistrySha256"] or row["sourceRegistrySha256"] != authority["sourceRegistrySha256"]:
            raise AssertionError("Saved Forge enchantment registry SHA changed")
        document = read(runtime / "migration-source.json")
        source_items(document, row["definition"])
        if any((runtime / marker).exists() for marker in ("advantage-connected.allow", "migration-operate.allow")):
            raise AssertionError("Stopped old source no longer has its frozen marker state")
        expected_commands = ["advprobe snapshot before-seed", *seed_commands(row["definition"]),
                             "advprobe snapshot source"]
        if row["definition"].get("playerBook"):
            expected_commands.append("advprobe actors-read source-book")
            record = read(runtime / "actors-source-book.json")
            if record.get("playerAdapter") is not True or record.get("frozen") is not True:
                raise AssertionError("Missing frozen actual old player read")
            player_items(matrix.plain(matrix.parse_nbt(record["player"])), row["definition"], modern=False)
            player_items(matrix.plain(nbtlib.load(runtime / "machine-items-evidence/source/player.dat").unpack()), row["definition"], modern=False)
        expected_commands += ["save-all flush", "stop"]
        if read(runtime / "console-source.commands.json") != expected_commands:
            raise AssertionError("Source command custody differs from permitted source seeding")
        audit_raw(runtime / "machine-items-evidence/source", row["definition"], document, modern=False)
    aggregate = SOURCE_WITH_DIAGNOSTICS if any(row["status"] == SOURCE_WITH_DIAGNOSTICS for row in rows) else SOURCE_WITNESS
    if receipt["status"] != aggregate:
        raise AssertionError("Source aggregate diagnostics status does not match its witnesses")
    return receipt


def require_power(manifest, power_source, power_sha256):
    if not re.fullmatch(r"[0-9a-f]{40}", power_source) or not re.fullmatch(r"[0-9A-Fa-f]{64}", power_sha256):
        raise ValueError("Explicit final frozen PowerD source and raw release hash required")
    power = [jar for jar in read(manifest)["target116"]["jars"] if jar["id"] == "poweradvantage"]
    if (len(power) != 1 or power[0].get("sourceRef") != power_source
            or power[0]["sha256"].upper() != power_sha256.upper() or power_sha256.upper() == OLD_POWER_C):
        raise AssertionError("Destination is not the explicitly approved replacement for PowerC")


def run_destination(args):
    fresh = source_receipt(args.source_receipt, args.source_receipt_sha256)
    manifest = checked(args.target_manifest, args.target_manifest_sha256)
    require_power(manifest, args.power_source, args.power_sha256)
    target = native116.pinned_profile(manifest, "target116")
    protected = [manifest, args.source_receipt, fresh["plan"], target[0], *target[2]]
    protected += [row["runtime"] for row in fresh["rows"]]
    root = new_root(args.output, protected)
    require_lease(args.lease_port)
    root.mkdir(parents=True)
    report = dict(status="RUNNING", sourceReceipt=str(args.source_receipt.resolve()),
                  sourceReceiptSha256=args.source_receipt_sha256.upper(), targetManifest=str(manifest),
                  targetManifestSha256=matrix.digest(manifest), rows=[], ticks=512,
                  runnerSha256=matrix.digest(Path(__file__)),
                  scope="ITEM_PRESERVATION_UNDER_TICKS_NOT_RECIPE_THROUGHPUT")
    try:
        for row in fresh["rows"]:
            source, definition = Path(row["runtime"]), row["definition"]
            runtime = root / row["sourceKey"] / row["case"]
            launcher = native116.boot(runtime, target, args.java8, args.lease_port)
            matrix.copy_fixture(source, runtime)
            configure(runtime, definition, args.lease_port)
            if definition.get("playerBook"):
                shutil.copy2(source / "actor-fixture.json", runtime / "actor-fixture.json")
            checks, previous = {}, None
            try:
                for label in ("converted", "restarted", "runtime-restart"):
                    hold_gate(root)
                    staged_inputs(runtime, target)
                    server = DestinationServer(runtime, args.java8, launcher, label)
                    try:
                        current = observe(server, runtime, label)
                        actual = document_items(current, definition, modern=True)
                        if definition.get("playerBook"):
                            observe_player(server, runtime, label + "-book", definition, modern=True)
                        if previous is not None and actual != previous:
                            raise AssertionError("Frozen machine item/state idempotence changed")
                        if label == "restarted":
                            server.operated = True
                            worked = observe(server, runtime, "operated", ticks=512)
                            actual = document_items(worked, definition, modern=True, ticks=512, previous=current)
                            current = worked
                        server.send("save-all flush")
                    finally:
                        server.close()
                    staged_inputs(runtime, target)
                    clean_log(runtime / ("console-" + label + ".log"), modern=True)
                    stopped_evidence(runtime, label, definition, current, modern=True)
                    previous = actual
                    checks[label] = dict(status="PASS", items=actual)
                    matrix.write_json(runtime / "checks.json", checks)
            finally:
                if matrix.fingerprint(source) != row["runtimeFingerprint"]:
                    raise AssertionError("Fresh stopped source mutated during destination")
            report["rows"].append(dict(sourceKey=row["sourceKey"], case=row["case"], checks=checks))
            matrix.write_json(root / "results.json", report)
        report["status"] = "PASS"
    except BaseException as error:
        report.update(status="FAIL", error=repr(error))
        raise
    finally:
        matrix.write_json(root / "results.json", report)
    return report


def audit(root):
    """Recompute from sealed source receipts, retained native snapshots and raw chunks."""
    report = read(root / "results.json")
    if report.get("status") != "PASS" or report.get("runnerSha256") != matrix.digest(Path(__file__)):
        raise AssertionError("Destination run is incomplete/failed or helper changed")
    fresh = source_receipt(Path(report["sourceReceipt"]), report["sourceReceiptSha256"])
    manifest = checked(report["targetManifest"], report["targetManifestSha256"])
    target = native116.pinned_profile(manifest, "target116")
    if len(report["rows"]) != len(fresh["rows"]):
        raise AssertionError("Destination/source coverage differs")
    expected = {(row["sourceKey"], row["case"]) for row in fresh["rows"]}
    if {(row["sourceKey"], row["case"]) for row in report["rows"]} != expected:
        raise AssertionError("Destination source/case coverage differs")
    for row in fresh["rows"]:
        runtime = root / row["sourceKey"] / row["case"]
        definition = row["definition"]
        staged_inputs(runtime, target)
        previous = None
        for label in ("converted", "restarted", "runtime-restart"):
            clean_log(runtime / ("console-" + label + ".log"), modern=True)
            current = read(runtime / ("connected-" + label + ".json"))
            actual = document_items(current, definition, modern=True)
            if definition.get("playerBook"):
                record = read(runtime / ("actors-" + label + "-book.json"))
                if record.get("playerAdapter") is not True or record.get("frozen") is not True:
                    raise AssertionError("Missing frozen actual native player read")
                player_items(matrix.plain(matrix.parse_nbt(record["player"])), definition, modern=True)
            if previous is not None and actual != previous:
                raise AssertionError("Raw snapshot restart idempotence failed")
            if label == "restarted":
                worked = read(runtime / "connected-operated.json")
                actual = document_items(worked, definition, modern=True, ticks=512, previous=current)
                current = worked
            commands = ["advconnected snapshot " + label]
            if definition.get("playerBook"):
                commands.append("advactors read " + label + "-book")
            if label == "restarted":
                commands.append("advconnected step 512 operated")
            commands += ["save-all flush", "stop"]
            if read(runtime / ("console-" + label + ".commands.json")) != commands:
                raise AssertionError("Destination command custody contains missing/extra/mutating commands")
            folder = runtime / "machine-items-evidence" / label
            receipt = read(folder / "receipt.json")
            if receipt.get("closedBeforeCapture") is not True or receipt.get("status") != "PASS":
                raise AssertionError("Unclosed raw evidence")
            for name, checksum in receipt["files"].items():
                if Path(name).is_absolute() or ".." in Path(name).parts:
                    raise ValueError("Raw evidence path escaped retained root")
                checked(folder / name, checksum)
            if read(folder / "snapshot.json") != current:
                raise AssertionError("Retained snapshot differs from raw probe channel")
            audit_raw(folder, definition, current, modern=True)
            if definition.get("playerBook"):
                player_items(matrix.plain(nbtlib.load(folder / "player.dat").unpack()), definition, modern=True)
            previous = actual
    return dict(status="PASS", routes=len(fresh["rows"]), boots=len(fresh["rows"]) * 3,
                sourceFixtures="UNCHANGED", scope="EXACT_MACHINE_ITEMS_NOT_RECIPE_THROUGHPUT")


def parser():
    root = argparse.ArgumentParser(description=__doc__)
    modes = root.add_subparsers(dest="mode", required=True)
    prepare = modes.add_parser("plan", help="Read-only plan; no process or world staging")
    prepare.add_argument("--routes", type=Path, required=True)
    prepare.add_argument("--routes-sha256", required=True)
    prepare.add_argument("--sources", nargs="+", choices=SOURCES, default=["original110", "legacy112"])
    prepare.add_argument("--output", type=Path, required=True)
    replay = modes.add_parser("audit", help="Replay completed native evidence without MC")
    replay.add_argument("--run", type=Path, required=True)
    replay.add_argument("--output", type=Path, required=True)
    for name in ("source", "destination"):
        command = modes.add_parser(name)
        command.add_argument("--output", type=Path, required=True)
        command.add_argument("--java8", type=Path, required=True)
        command.add_argument("--lease-port", type=int, choices=(25574,), required=True)
        if name == "source":
            command.add_argument("--plan", type=Path, required=True)
            command.add_argument("--plan-sha256", required=True)
        else:
            command.add_argument("--source-receipt", type=Path, required=True)
            command.add_argument("--source-receipt-sha256", required=True)
            command.add_argument("--target-manifest", type=Path, required=True)
            command.add_argument("--target-manifest-sha256", required=True)
            command.add_argument("--power-source", required=True)
            command.add_argument("--power-sha256", required=True)
    return root


def main(argv=None):
    args = parser().parse_args(argv)
    if args.mode == "plan":
        value = plan(args.routes, args.routes_sha256, args.sources)
        protected = [args.routes] + [source["route"]["source"] for source in value["sources"]]
        destination = new_root(args.output, protected)
        matrix.write_json(destination, value)
        print("PLAN ONLY: %d old source boots; %d native destination boots; none launched" % (
            value["sourceBoots"], value["destinationBoots"]))
    elif args.mode == "audit":
        destination = new_root(args.output, [args.run])
        matrix.write_json(destination, audit(args.run.resolve()))
    elif args.mode == "source":
        run_source(args)
    else:
        run_destination(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
