"""Negative controls for partial unload and extended drill acceptance."""
import copy
import unittest
from extended_lifecycle import fixtures, output_results, partial_result, qualification, reserve_results, redstone_results, restart_results


class ExtendedOracleTests(unittest.TestCase):
    def test_geometry_and_coverage(self):
        definitions = fixtures()
        self.assertEqual(len(definitions), 21)
        for f in definitions:
            for p in f["witnesses"]:
                self.assertTrue(all(a <= n <= b for a, n, b in zip(f["min"], p, f["max"])))
            if "route" not in f: continue
            self.assertEqual(len(f["route"]), 5)
            for a, b in zip(f["route"], f["route"][1:]): self.assertEqual(sum(abs(x-y) for x,y in zip(a,b)), 1)
            self.assertTrue(all(sum(abs(x-y) for x,y in zip(a,b)) == 1 for a,b in zip(f["route"], f["controls"])))
        self.assertEqual(sum(f.get("junction", False) for f in definitions), 2)
        self.assertEqual(sum(f.get("track", [0,0,0])[1] != 0 for f in definitions), 6)

    def partial(self):
        f = next(f for f in fixtures() if f["id"] == "C-FLUID")
        source, receiver = f["groups"]["supply"][0], f["groups"]["machine"][0]
        chunks = [{"pos": source, "loaded": True, "unloads": 0, "loads": 1},
                  {"pos": receiver, "loaded": True, "unloads": 0, "loads": 1}]
        entries = [{"pos": p, "block": "pipe", "state": "pipe"} for p in f["witnesses"]]
        entries[-1].update(tileIdentity=42, nbt='{Tank:{FluidName:"minecraft:water",Amount:1000}}')
        before = {"worldTime": 0, "chunks": chunks, "cases": {f["id"]: entries}}
        absent = copy.deepcopy(before); absent["chunks"][0].update(loaded=False, unloads=1)
        loaded = copy.deepcopy(before); loaded["chunks"][0].update(unloads=1, loads=2)
        confirmed = copy.deepcopy(absent); confirmed.update(confirmed=True, worldTime=40)
        return f, before, [absent, confirmed], loaded

    def test_true_partial_unload_passes(self):
        f, before, samples, loaded = self.partial()
        self.assertEqual(partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"], "PASS")

    def test_automatic_reload_is_not_partial_pass(self):
        f, before, samples, loaded = self.partial()
        for s in samples: s["chunks"][0]["loaded"] = True
        r = partial_result([f], before, samples, loaded, "supply")[f["id"]]
        self.assertEqual(r["status"], "FAIL"); self.assertTrue(r["real_unload_event"])

    def test_snapshot_without_unload_event_fails(self):
        f, before, samples, loaded = self.partial()
        for s in samples: s["chunks"][0]["unloads"] = 0
        self.assertEqual(partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"], "FAIL")

    def test_retained_endpoint_replacement_fails(self):
        f, before, samples, loaded = self.partial(); samples[0]["cases"][f["id"]][-1]["tileIdentity"] = 43
        self.assertEqual(partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"], "FAIL")

    def test_one_instant_absence_is_insufficient(self):
        f, before, samples, loaded = self.partial(); samples[0]["chunks"][0]["loaded"] = True
        self.assertEqual(partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"], "FAIL")

    def test_short_absence_is_not_two_seconds(self):
        f, before, samples, loaded = self.partial(); samples[-1]["worldTime"] = 39
        self.assertEqual(partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"], "FAIL")

    def test_redstone_needs_progress_cancel_and_resume(self):
        f = fixtures()[0]
        def snap(progress): return {"cases": {f["id"]: [{"pos": f["to"], "block": f["block"], "state": "drill[facing="+f["facing"]+"]", "nbt": "{progress:%ds,Items:[]}" % progress}]}}
        self.assertEqual(redstone_results([f], snap(80), snap(0), snap(60))[f["id"]]["status"], "PASS")
        self.assertEqual(redstone_results([f], snap(80), snap(0), snap(0))[f["id"]]["status"], "FAIL")

    def test_failure_propagates_to_overall_qualification(self):
        result = {k: {"case": {"status": "PASS"}} for k in ("insufficient_energy", "moves", "blocked_output", "restart", "blocked_after_restart", "resumed_output", "redstone")}
        result.update(partial_networks=[], reload_orders=[], network_processing="PASS")
        self.assertEqual(qualification(result), "PASS")
        result["partial_networks"].append({"cases": {"case": {"status": "FAIL"}}})
        self.assertEqual(qualification(result), "FAIL")

    def output(self):
        f = fixtures()[0]
        tagged = '{Slot:0b,id:"minecraft:dirt",Count:7b,tag:{AdvWorksWitness:"' + f["id"] + '"}}'
        drill = {"pos": f["to"], "block": f["block"], "state": "drill[facing=down]",
                 "nbt": '{Items:['+tagged+',{Slot:1b,id:"minecraft:apple",Count:13b},{Slot:2b,id:"minecraft:gravel",Count:1b}]}'}
        stone = ','.join('{Slot:%db,id:"minecraft:stone",Count:64b}' % n for n in range(27))
        chest = {"pos": f["rear"], "block": "minecraft:chest", "state": "chest", "nbt": '{Items:['+stone+']}'}
        return f, {"cases": {f["id"]: [drill, chest]}}

    def test_blocked_output_conserves_items(self):
        f, snapshot = self.output()
        self.assertEqual(output_results([f], snapshot, True)[f["id"]]["status"], "PASS")
        snapshot["cases"][f["id"]][0]["nbt"] = snapshot["cases"][f["id"]][0]["nbt"].replace("Count:7b", "Count:6b")
        self.assertEqual(output_results([f], snapshot, True)[f["id"]]["status"], "FAIL")

    def test_unchanged_output_cannot_pass_resume(self):
        f, snapshot = self.output()
        self.assertEqual(output_results([f], snapshot, False)[f["id"]]["status"], "FAIL")

    def test_custom_tag_loss_fails(self):
        f, snapshot = self.output()
        snapshot["cases"][f["id"]][0]["nbt"] = snapshot["cases"][f["id"]][0]["nbt"].replace(f["id"], "lost")
        self.assertIn("custom item tag lost", output_results([f], snapshot, True)[f["id"]]["failures"])

    def test_insufficient_reserve_is_measured(self):
        f = fixtures()[0]
        entry = {"pos": f["from"], "block": f["block"], "energy": {"steam": 40}, "nbt": "{Items:[]}"}
        tracks = [{"pos": pos, "block": "steamadvantage:steam_track"} for pos in f["route"][1:]]
        snap = {"movements": [], "cases": {f["id"]: [entry]+tracks}}
        self.assertEqual(reserve_results([f], snap, snap)[f["id"]]["status"], "FAIL")


if __name__ == "__main__": unittest.main()
