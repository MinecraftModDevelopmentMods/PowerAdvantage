"""Negative controls for exact oil identity, conservation, and restart-refill oracles."""
import copy
import unittest

from oil_transfer import fixtures, inspect, require_same_machines


class OilTransferTests(unittest.TestCase):
    def sample(self):
        definition = fixtures()
        snapshot = {"cases": {}}
        for f in definition:
            rows = [{"pos": f["source"], "block": "minecraft:air"}]
            rows.append({"pos": f["drain"], "block": "poweradvantage:fluid_drain", "tileIdentity": 1, "nbt": "{Tank:{Empty:1b}}"})
            rows.extend({"pos": p, "block": "poweradvantage:fluid_pipe"} for p in f["pipes"])
            rows.append({"pos": f["output"], "block": f["tank"], "tileIdentity": 2,
                         "nbt": '{Tank:{Amount:1000,FluidName:"' + f["fluid"] + '"}}'})
            snapshot["cases"][f["id"]] = rows
        return definition, snapshot

    def test_exact_lanes_pass(self):
        definition, snapshot = self.sample()
        self.assertEqual(len(inspect(snapshot, definition, 1000)), 4)

    def test_changed_fluid_fails(self):
        definition, snapshot = self.sample()
        snapshot["cases"][definition[0]["id"]][-1]["nbt"] = '{Tank:{Amount:1000,FluidName:"minecraft:water"}}'
        with self.assertRaises(AssertionError): inspect(snapshot, definition, 1000)

    def test_loss_duplication_and_stuck_fluid_fail(self):
        definition, original = self.sample()
        for amount in (0, 999, 1001, 2000):
            snapshot = copy.deepcopy(original)
            snapshot["cases"][definition[0]["id"]][-1]["nbt"] = '{Tank:{Amount:' + str(amount) + ',FluidName:"poweradvantage:crude_oil"}}'
            with self.assertRaises(AssertionError): inspect(snapshot, definition, 1000)
        original["cases"][definition[0]["id"]][1]["nbt"] = '{Tank:{Amount:1}}'
        with self.assertRaises(AssertionError): inspect(original, definition, 1000)

    def test_refill_cannot_replace_endpoint(self):
        definition, before = self.sample()
        require_same_machines(before, copy.deepcopy(before), definition)
        after = copy.deepcopy(before)
        after["cases"][definition[0]["id"]][-1]["tileIdentity"] = 3
        with self.assertRaises(AssertionError): require_same_machines(before, after, definition)

    def test_lanes_are_bounded_separated_and_connected(self):
        definition = fixtures()
        self.assertEqual(len({f["bucket"] for f in definition}), 2)
        self.assertEqual(len({f["tank"] for f in definition}), 2)
        for f in definition:
            for p in f["witnesses"]:
                self.assertTrue(all(low <= value <= high for low, value, high in zip(f["min"], p, f["max"])))
            route = [f["source"], f["drain"], *f["pipes"], f["output"]]
            for a, b in zip(route, route[1:]): self.assertEqual(sum(abs(x-y) for x,y in zip(a,b)), 1)
