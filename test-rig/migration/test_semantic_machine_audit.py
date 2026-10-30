"""Deliberately corrupt evidence in ways the frozen operating oracle accepted."""

import base64
import copy
import gzip
import io
import json
import unittest

import nbtlib
from legacy_connected_oracle import entries, operation as historical_operation
from semantic_machine_audit import BATCH_MACHINES, connected_interval, endurance_batch


def fixture(source):
    """Pure JSON-compatible connected layout; never stages jars or constructs a world."""
    blocks = []

    def put(station, role, x, z, name, seed=None, meta=0, y=80):
        entry = dict(id=f"{station}-{role}", station=station, kind=role, x=x, y=y, z=z,
                     name=name, metadata=meta)
        if seed is not None:
            entry["seed"] = seed
        blocks.append(entry)

    def items(values):
        return "{Items:[" + ",".join('{Slot:%db,id:"%s",Count:%db,Damage:0s}' % e for e in values) + "]}"

    def supply(station, x, count):
        for i in range(count):
            bx = x + 1 + i * 4
            put(station, f"water-{i}", bx, 50, "poweradvantage:fluid_metal_tank",
                '{Tank:{FluidName:"water",Amount:10000}}')
            put(station, f"water-pipe-{i}", bx, 49, "poweradvantage:fluid_pipe")
            put(station, f"boiler-{i}", bx, 48, "steamadvantage:steam_boiler_coal",
                items([(0, "minecraft:coal", 32)]))
        for dx in range(1, 13):
            put(station, f"steam-pipe-{dx}", x + dx, 47, "steamadvantage:steam_pipe")

    station = "C-CONVEYOR"
    put(station, "input", 1, 40, "minecraft:chest", items([(i, "minecraft:dirt", 64) for i in range(27)]))
    put(station, "belt-1", 2, 40, "poweradvantage:item_conveyor", meta=5)
    put(station, "belt-2", 3, 40, "poweradvantage:item_conveyor", meta=5)
    put(station, "output", 4, 40, "minecraft:chest")
    station = "C-STEAM"
    supply(station, 64, 3)
    put(station, "storage", 76, 46, "steamadvantage:steam_tank")
    put(station, "furnace", 68, 46, "steamadvantage:steam_furnace",
        items([(0, "minecraft:coal", 32)] + [(i, "minecraft:iron_ore", 64) for i in (1, 2, 3)]))
    put(station, "crusher", 74, 46, "steamadvantage:steam_crusher", items([(0, "minecraft:cobblestone", 64)]))
    station = "C-ELECTRIC"
    supply(station, 128, 2)
    put(station, "generator", 141, 47, "electricadvantage:steam_powered_generator")
    for x in (142, 144):
        put(station, f"wire-{x}", x, 47, "electricadvantage:electric_conduit")
    put(station, "battery", 143, 47, "electricadvantage:electric_battery_array",
        '{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:1b,Damage:0s,tag:{energy:0.0f}}]}')
    put(station, "furnace", 145, 47, "electricadvantage:electric_furnace", items([(0, "minecraft:iron_ore", 64)]))
    for station, origin, electric in (("C-SDRILL", 192, False), ("C-EDRILL", 256, True)):
        supply(station, origin, 2)
        if electric:
            put(station, "generator", origin + 13, 47, "electricadvantage:steam_powered_generator")
            pipe = "electricadvantage:electric_conduit"
        else:
            put(station, "steam-link", origin + 13, 47, "steamadvantage:steam_pipe")
            pipe = "steamadvantage:steam_pipe"
        for dx in (14, 15):
            put(station, f"power-link-{dx}", origin + dx, 47, pipe)
        for z in range(43, 47):
            put(station, f"side-link-{z}", origin + 15, z, pipe)
        put(station, "drill", origin + 14, 43,
            "electricadvantage:electric_drill" if electric else "steamadvantage:steam_drill", meta=2)
        put(station, "rear", origin + 14, 44, "minecraft:chest")
        for layer in range(32 if electric else 63):
            for dx in (-1, 0, 1) if electric else (0,):
                for dy in (-1, 0, 1) if electric else (0,):
                    put(station, f"target-{layer}-{dx + 1}-{dy + 1}", origin + 14 + dx, 42 - layer,
                        "minecraft:cobblestone", y=80 + dy)
    return dict(format=1, sourceProfile=source, bounds=[0, 78, -32, 280, 84, 64], blocks=blocks)


def nbt(entry, value):
    stream = io.BytesIO()
    nbtlib.File(nbtlib.parse_nbt(value)).write(stream)
    entry["nbtBase64"] = base64.b64encode(gzip.compress(stream.getvalue())).decode("ascii")
    entry["nbt"] = value


def sample():
    document = dict(held=True, requestedTicks=0, advancedTicks=0, buildCallsThisProcess=0,
                    snapshot=copy.deepcopy(fixture("test")["blocks"]))
    for entry in document["snapshot"]:
        entry["actualName"] = entry["name"]
        if "seed" in entry or entry["kind"] in ("drill", "rear", "output"):
            nbt(entry, entry.get("seed", "{Items:[]}"))
            entry.update(witness=entry["id"], objectIdentity=len(entry["id"]))
    return document


class BatchFixture:
    """In-memory endpoint evidence only; no server, commands or filesystem writer."""
    def __init__(self):
        self.definitions = {name: {"origin": [0, 0, 0]} for name in ("S-04", "E-03", "W-04", "W-03")}
        self.snapshots = {name: {} for name in self.definitions}
        for station, pos, neighbours, product in (
                ("S-04", (3, 1, 7), ((2, 1, 7), (4, 1, 7)), "iron_ingot"),
                ("S-04", (3, 1, 13), ((2, 1, 13), (4, 1, 13)), "gravel"),
                ("E-03", (6, 1, 6), (), "iron_ingot"),
                ("E-03", (3, 1, 6), (), "gravel"),
                ("E-03", (9, 1, 6), (), "cooked_beef")):
            self.put(station, pos, [("minecraft:" + product, 4)])
            for neighbour in neighbours:
                self.put(station, neighbour)
        for x in (2, 3, 4):
            self.put("W-04", (x, 1, 6))
        self.put("W-04", (5, 1, 6), [("minecraft:dirt", 16)])
        for z in (9, 15, 18):
            self.put("W-04", (2, 1, z))
            self.put("W-04", (3, 1, z))
            forward = [("minecraft:apple", 8)]
            if z == 15:
                forward.append(("minecraft:dirt", 8))
            self.put("W-04", (4, 1, z), forward)
            self.put("W-04", (3, 0, z), [] if z == 15 else [("minecraft:dirt", 8)])
        for x in (4, 6, 8):
            self.put("W-03", (x, 1, 8))
        self.entry("W-03", (8, 1, 8))["data"]["Tank"] = {
            "FluidName": "poweradvantage:refined_oil", "Amount": 1000}

    def put(self, station, pos, items=()):
        self.snapshots[station][pos] = {"pos": pos, "data": {"Items": [
            {"Slot": slot, "id": name, "Count": count} for slot, (name, count) in enumerate(items)]}}

    def entry(self, station, pos):
        return self.snapshots[station][pos]


def pair():
    before = sample()
    before["gameTime"] = 100
    after = copy.deepcopy(before)
    after.update(gameTime=164, requestedTicks=64, advancedTicks=64)
    rows = entries(after)
    source = ','.join('{Slot:%db,id:"minecraft:dirt",Count:%db}' % (slot, 63 if slot == 26 else 64) for slot in range(27))
    nbt(rows["C-CONVEYOR-input"], '{Items:[' + source + ']}')
    nbt(rows["C-CONVEYOR-output"], '{Items:[{Slot:0b,id:"minecraft:dirt",Count:1b}]}')
    nbt(rows["C-STEAM-furnace"], '{Items:[{Slot:0b,id:"minecraft:coal",Count:32b},'
        '{Slot:1b,id:"minecraft:iron_ore",Count:63b},{Slot:2b,id:"minecraft:iron_ore",Count:64b},'
        '{Slot:3b,id:"minecraft:iron_ore",Count:64b},{Slot:4b,id:"minecraft:iron_ingot",Count:1b}]}')
    for key, raw, product in (("C-ELECTRIC-furnace", "iron_ore", "iron_ingot"), ("C-STEAM-crusher", "cobblestone", "gravel")):
        nbt(rows[key], '{Items:[{Slot:0b,id:"minecraft:%s",Count:63b},{Slot:1b,id:"minecraft:%s",Count:1b}]}' % (raw, product))
    for station, product, count in (("C-SDRILL", "gravel", 1), ("C-EDRILL", "cobblestone", 9)):
        targets = [row for row in after["snapshot"] if row["station"] == station and row["kind"].startswith("target-")]
        for row in targets[:count]: row["actualName"] = "minecraft:air"
        nbt(rows[station + "-drill"], '{Items:[{Slot:0b,id:"minecraft:%s",Count:%db}]}' % (product, count))
    return before, after


class SemanticMachineAuditTest(unittest.TestCase):
    def check(self, before, after):
        return connected_interval(before, after, fixture("test"), 64)

    def assertPreviouslyAccepted(self, before, after):
        self.assertEqual("PASS", historical_operation(before, after, 64)["status"])
        self.assertEqual("FAIL", self.check(before, after)["status"])

    def test_real_material_transformation_and_finite_fixture_pass(self):
        before, after = pair()
        self.assertEqual("PASS", self.check(before, after)["status"])

    def test_unexpected_items_no_longer_hide_behind_expected_counts(self):
        before, after = pair()
        nbt(entries(after)["C-CONVEYOR-output"], '{Items:[{Slot:0b,id:"minecraft:dirt",Count:1b},{Slot:1b,id:"minecraft:diamond",Count:1b}]}')
        self.assertPreviouslyAccepted(before, after)

    def test_replacement_is_not_mining(self):
        before, after = pair()
        target = next(row for row in after["snapshot"] if row["station"] == "C-EDRILL" and row["actualName"] == "minecraft:air")
        target["actualName"] = "minecraft:diamond_block"
        self.assertPreviouslyAccepted(before, after)

    def test_negative_water_is_not_consumed_water(self):
        before, after = pair()
        nbt(entries(after)["C-STEAM-water-0"], '{Tank:{FluidName:"water",Amount:-1}}')
        self.assertPreviouslyAccepted(before, after)

    def test_battery_charge_is_bounded_and_required(self):
        for charge in (-1, 8001):
            with self.subTest(charge=charge):
                before, after = pair()
                nbt(entries(after)["C-ELECTRIC-battery"], '{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:1b,tag:{energy:%df}}]}' % charge)
                self.assertPreviouslyAccepted(before, after)

    def test_self_reported_tick_count_cannot_replace_advancing_world_time(self):
        before, after = pair()
        after["gameTime"] = before["gameTime"]
        self.assertPreviouslyAccepted(before, after)

    def test_invalid_start_state_cannot_be_ignored(self):
        before, after = pair()
        nbt(entries(before)["C-ELECTRIC-furnace"], '{Items:[{Slot:0b,id:"minecraft:iron_ore",Count:63b}]}')
        self.assertPreviouslyAccepted(before, after)

    def test_counter_only_drill_progress_is_incomplete_not_completed_work(self):
        before, after = pair()
        for row in after["snapshot"]:
            if row["station"] == "C-EDRILL" and row["kind"].startswith("target-"): row["actualName"] = row["name"]
        nbt(entries(after)["C-EDRILL-drill"], '{progress:10s,Items:[]}')
        self.assertEqual("PASS", historical_operation(before, after, 64)["status"])
        self.assertEqual("INCOMPLETE", self.check(before, after)["status"])

    def test_no_work_wrong_yield_and_missing_witness_fail(self):
        for mutation in ("idle", "lost", "missing", "duplicate", "fuel", "zero-ticks"):
            with self.subTest(mutation=mutation):
                before, after = pair()
                if mutation == "idle":
                    after = copy.deepcopy(before)
                    after.update(gameTime=164, requestedTicks=64, advancedTicks=64)
                elif mutation == "lost": nbt(entries(after)["C-ELECTRIC-furnace"], '{Items:[]}')
                elif mutation == "missing": after["snapshot"].pop()
                elif mutation == "duplicate": after["snapshot"].append(copy.deepcopy(after["snapshot"][0]))
                elif mutation == "fuel": nbt(entries(after)["C-STEAM-boiler-0"], '{Items:[{Slot:0b,id:"minecraft:coal",Count:33b}]}')
                else: after["advancedTicks"] = 0
                self.assertEqual("FAIL", self.check(before, after)["status"])


class IndependentEnduranceTest(unittest.TestCase):
    def setUp(self):
        self.rig = BatchFixture()
        # Complete the legacy self-test's deliberately partial fake-world data.
        for pos in ((4, 1, 8), (6, 1, 8)):
            self.rig.entry("W-03", pos)["data"]["Tank"] = {"Empty": 1}
        self.rig.entry("W-03", (6, 1, 8))["data"]["InputTank"] = {"Empty": 1}
        for z, ghosts in ((9, [(1, "dirt"), (2, "cobblestone"), (3, "coal")]), (15, []), (18, [(1, "dirt")])):
            self.rig.entry("W-04", (3, 1, z))["data"]["Items"] = [
                {"Slot": slot, "id": "minecraft:" + name, "Count": 1} for slot, name in ghosts]

    def documents(self):
        docs = {}
        for station in ("S-04", "E-03", "W-04", "W-03"):
            rows = []
            for pos, entry in self.rig.snapshots[station].items():
                block = next((block for s, p, _, block, _, _ in BATCH_MACHINES if s == station and p == pos), "minecraft:chest")
                rows.append(dict(pos=pos, block=block, nbt=json.dumps(entry["data"])))
            docs[station] = dict(station=station, snapshot=rows)
        return docs

    def strict(self):
        return endurance_batch(self.documents(), self.rig.definitions)

    def test_complete_measured_batch_passes(self):
        self.assertEqual("PASS", self.strict()["status"])

    def test_extra_hidden_filter_items_are_not_ignored(self):
        self.rig.entry("W-04", (3, 1, 18))["data"]["Items"].append({"Slot": 4, "id": "minecraft:diamond", "Count": 1})
        self.assertEqual("FAIL", self.strict()["status"])

    def test_lost_reference_filter_does_not_pass_as_throughput(self):
        self.rig.entry("W-04", (3, 1, 9))["data"]["Items"] = []
        self.assertEqual("FAIL", self.strict()["status"])

    def test_missing_input_tank_is_not_an_empty_input_tank(self):
        del self.rig.entry("W-03", (6, 1, 8))["data"]["InputTank"]
        self.assertEqual("FAIL", self.strict()["status"])

    def test_machine_identity_is_required_not_only_seeded_output(self):
        docs = self.documents()
        docs["E-03"]["snapshot"][0]["block"] = "minecraft:chest"
        self.assertEqual("FAIL", endurance_batch(docs, self.rig.definitions)["status"])


if __name__ == "__main__": unittest.main()
