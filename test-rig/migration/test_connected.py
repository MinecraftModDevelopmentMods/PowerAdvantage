"""Connected tests must reject idle, rebuilt, emptied or duplicated machinery."""
import base64
import copy
import gzip
import io
import unittest

import nbtlib
from connected import cold_state, entries, fixture, operation, state


def nbt(entry, value):
    stream = io.BytesIO()
    nbtlib.File(nbtlib.parse_nbt(value)).write(stream)
    entry["nbtBase64"] = base64.b64encode(gzip.compress(stream.getvalue())).decode()
    entry["nbt"] = value


def sample():
    document = dict(held=True, requestedTicks=0, advancedTicks=0, buildCallsThisProcess=0,
                    snapshot=copy.deepcopy(fixture("test")["blocks"]))
    for e in document["snapshot"]:
        e["actualName"] = e["name"]
        if "seed" in e or e["kind"] in ("drill", "rear", "output"):
            nbt(e, e.get("seed", "{Items:[]}"))
            e.update(witness=e["id"], objectIdentity=len(e["id"]))
    return document


def advanced(before):
    after = copy.deepcopy(before)
    after.update(requestedTicks=64, advancedTicks=64)
    rows = entries(after)
    nbt(rows["C-CONVEYOR-input"], '{Items:[{id:"minecraft:dirt",Count:1727}]}')
    nbt(rows["C-CONVEYOR-output"], '{Items:[{id:"minecraft:dirt",Count:1b}]}')
    for station, count in (("C-STEAM", 191), ("C-ELECTRIC", 63)):
        nbt(rows[station + "-furnace"], '{Items:[{id:"minecraft:iron_ore",Count:%d},{id:"minecraft:iron_ingot",Count:1b}]}' % count)
    nbt(rows["C-STEAM-crusher"], '{Items:[{id:"minecraft:cobblestone",Count:63b},{id:"minecraft:gravel",Count:1b}]}')
    for station, count, item in (("C-SDRILL", 1, "gravel"), ("C-EDRILL", 9, "cobblestone")):
        targets = [e for e in after["snapshot"] if e["station"] == station and e["kind"].startswith("target-")]
        for target in targets[:count]:
            target["actualName"] = "minecraft:air"
        nbt(rows[station + "-drill"], '{Items:[{id:"minecraft:%s",Count:%db}]}' % (item, count))
    return after


class ConnectedTest(unittest.TestCase):
    def test_definition_has_unique_bounded_placements_and_no_infinite_sources(self):
        definition = fixture("test")
        positions = {(e["x"], e["y"], e["z"]) for e in definition["blocks"]}
        self.assertEqual(len(positions), len(definition["blocks"]))
        self.assertTrue(all("infinite_" not in e["name"] for e in definition["blocks"]))
        self.assertTrue(all("Energy" not in e.get("seed", "") for e in definition["blocks"]))

    def test_valid_cold_fixture_is_finite_and_uncharged(self):
        self.assertEqual(cold_state(sample())["status"], "PASS")

    def test_empty_tank_setup_and_precharged_battery_fail(self):
        doc = sample()
        nbt(entries(doc)["C-STEAM-water-0"], '{Tank:{Empty:""}}')
        self.assertEqual(cold_state(doc)["status"], "FAIL")
        doc = sample()
        nbt(entries(doc)["C-ELECTRIC-battery"], '{Items:[{id:"electricadvantage:lithium_battery",Count:1b,tag:{energy:10.0f}}]}')
        self.assertEqual(cold_state(doc)["status"], "FAIL")

    def test_live_work_with_exact_material_accounting_passes(self):
        doc = sample()
        self.assertEqual(operation(doc, advanced(doc), 64)["status"], "PASS")

    def test_idle_equipment_is_not_an_operating_pass(self):
        doc = sample()
        idle = copy.deepcopy(doc)
        idle.update(requestedTicks=64, advancedTicks=64)
        self.assertEqual(operation(doc, idle, 64)["status"], "FAIL")

    def test_inventory_loss_and_duplication_fail(self):
        for count in (62, 64):
            doc = advanced(sample())
            nbt(entries(doc)["C-ELECTRIC-furnace"], '{Items:[{id:"minecraft:iron_ore",Count:%d},{id:"minecraft:iron_ingot",Count:1b}]}' % count)
            self.assertEqual(state(doc)["status"], "FAIL")

    def test_mined_blocks_without_retained_drops_fail(self):
        doc = advanced(sample())
        nbt(entries(doc)["C-EDRILL-drill"], '{Items:[]}')
        self.assertEqual(state(doc)["status"], "FAIL")

    def test_drills_preserve_their_distinct_products(self):
        for station, wrong in (("C-SDRILL", "cobblestone"), ("C-EDRILL", "gravel")):
            doc = advanced(sample())
            count = 1 if station == "C-SDRILL" else 9
            nbt(entries(doc)[station + "-drill"], '{Items:[{id:"minecraft:%s",Count:%db}]}' % (wrong, count))
            self.assertEqual(state(doc)["status"], "FAIL")

    def test_replacement_lost_witness_and_zero_ticks_fail(self):
        before = sample()
        for field, value in (("objectIdentity", -1), ("witness", "replaced")):
            after = advanced(before)
            entries(after)["C-ELECTRIC-furnace"][field] = value
            self.assertEqual(operation(before, after, 64)["status"], "FAIL")
        after = advanced(before)
        after["advancedTicks"] = 0
        self.assertEqual(operation(before, after, 64)["status"], "FAIL")

    def test_fluid_creation_and_invalid_energy_fail(self):
        before = sample()
        after = advanced(before)
        nbt(entries(after)["C-STEAM-water-0"], '{Tank:{FluidName:"water",Amount:10001}}')
        self.assertEqual(operation(before, after, 64)["status"], "FAIL")
        after = advanced(before)
        nbt(entries(after)["C-ELECTRIC-furnace"], '{Energy:[I;2143289344]}')
        self.assertEqual(state(after)["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
