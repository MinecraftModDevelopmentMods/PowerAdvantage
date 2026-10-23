"""Check bridge geometry and the independent pipe-only oracle's negative controls."""
import copy
import unittest

from audit_pipe_only import bridge_result
from pipe_only_lifecycle import fixtures


class PipeOnlyTests(unittest.TestCase):
    def test_bridge_contains_only_pipes_and_breaks_the_only_path(self):
        for f in fixtures():
            bridge = tuple(f["groups"]["bridge"][0])
            self.assertTrue(all((p[0] >> 4, p[2] >> 4) != bridge for p in f["witnesses"][:2]))
            remaining = {tuple(p) for p in f["witnesses"] if (p[0] >> 4, p[2] >> 4) != bridge}
            visited = {tuple(f["witnesses"][0])}
            while True:
                added = {p for p in remaining if any(sum(abs(a-b) for a,b in zip(p,q)) == 1 for q in visited)} - visited
                if not added: break
                visited |= added
            self.assertNotIn(tuple(f["witnesses"][1]), visited)

    def samples(self):
        f = fixtures()[2]
        endpoint = f["groups"]["endpoints"][0]
        bridge = f["groups"]["bridge"][0]
        before = {"worldTime": 0, "chunks": [{"pos": endpoint, "loaded": True, "unloads": 0, "loads": 1},
                                               {"pos": bridge, "loaded": True, "unloads": 0, "loads": 1}],
                  "cases": {f["id"]: [{"pos": p, "block": "pipe", "state": "pipe"} for p in f["witnesses"]]}}
        before["cases"][f["id"]][1].update(tileIdentity=1, nbt='{Tank:{FluidName:"minecraft:water",Amount:0}}')
        absent = copy.deepcopy(before); absent["chunks"][1].update(loaded=False, unloads=1)
        confirmed = copy.deepcopy(absent); confirmed["worldTime"] = 60
        loaded = copy.deepcopy(before); loaded["chunks"][1].update(unloads=1, loads=2)
        resumed = copy.deepcopy(loaded); resumed["cases"][f["id"]][1]["nbt"] = '{Tank:{FluidName:"minecraft:water",Amount:1000}}'
        return f, [before, absent, confirmed, loaded, resumed]

    def test_valid_bridge_passes(self):
        f, samples = self.samples()
        self.assertEqual(bridge_result([f], *samples, 0)["status"], "PASS")

    def test_automatic_loading_fails(self):
        f, samples = self.samples(); samples[2]["chunks"][1]["loaded"] = True
        self.assertEqual(bridge_result([f], *samples, 0)["status"], "FAIL")

    def test_missing_event_fails(self):
        f, samples = self.samples(); samples[1]["chunks"][1]["unloads"] = 0
        self.assertEqual(bridge_result([f], *samples, 0)["status"], "FAIL")

    def test_endpoint_replacement_fails(self):
        f, samples = self.samples(); samples[4]["cases"][f["id"]][1]["tileIdentity"] = 2
        self.assertEqual(bridge_result([f], *samples, 0)["status"], "FAIL")

    def test_fluid_duplication_fails(self):
        f, samples = self.samples(); samples[4]["cases"][f["id"]][1]["nbt"] = '{Tank:{Amount:2000}}'
        self.assertEqual(bridge_result([f], *samples, 0)["status"], "FAIL")
