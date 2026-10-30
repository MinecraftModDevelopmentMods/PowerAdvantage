"""Native-tick continuity controls; these fixtures do not launch Minecraft."""
from copy import deepcopy
import unittest

from extended_lifecycle import fixtures, partial_result


class ContinuousAbsenceTests(unittest.TestCase):
    def fixture(self, ticks=(10, 50)):
        f = deepcopy(next(f for f in fixtures() if f["id"] == "C-FLUID"))
        supply, machine = f["groups"]["supply"][0], f["groups"]["machine"][0]
        entries = [dict(pos=p, block="pipe", state="pipe", nbt="{}") for p in f["witnesses"]]
        entries[-1].update(tileIdentity=42, nbt='{Tank:{FluidName:"minecraft:water",Amount:1000}}')
        before = dict(worldTime=0, chunks=[dict(pos=supply, loaded=True, loads=1, unloads=0),
                                           dict(pos=machine, loaded=True, loads=1, unloads=0)], cases={f["id"]: entries})
        samples = []
        for tick in ticks:
            sample = deepcopy(before)
            sample["worldTime"] = tick
            sample["chunks"][0].update(loaded=False, unloads=1)
            samples.append(sample)
        samples[-1]["confirmed"] = True
        loaded = deepcopy(before)
        loaded["worldTime"] = ticks[-1] + 1
        loaded["chunks"][0].update(loads=2, unloads=1)
        return f, before, samples, loaded

    def status(self, fixture):
        f, before, samples, loaded = fixture
        return partial_result([f], before, samples, loaded, "supply")[f["id"]]["status"]

    def test_dense_189_tick_absence_with_final_gap_38_passes(self):
        self.assertEqual("PASS", self.status(self.fixture((1315, 1336, 1357, 1378, 1399, 1422, 1444, 1466, 1504))))

    def test_exactly_40_native_ticks_passes(self):
        self.assertEqual("PASS", self.status(self.fixture()))

    def test_39_ticks_never_becomes_40(self):
        self.assertEqual("FAIL", self.status(self.fixture((10, 30, 49))))

    def test_single_absent_instant_is_insufficient(self):
        self.assertEqual("FAIL", self.status(self.fixture((50,))))

    def test_hidden_reload_between_absent_observations_fails(self):
        f = self.fixture()
        f[2][-1]["chunks"][0].update(loads=2, unloads=2)
        self.assertEqual("FAIL", self.status(f))

    def test_long_suffix_must_not_hide_a_reload_in_the_observed_segment(self):
        f = self.fixture((10, 30, 90))
        for sample in f[2][1:]:
            sample["chunks"][0].update(loads=2, unloads=2)
        self.assertEqual("FAIL", self.status(f))

    def test_observed_reload_fails(self):
        f = self.fixture((10, 30, 90))
        f[2][1]["chunks"][0].update(loaded=True, loads=2)
        f[2][-1]["chunks"][0].update(loads=2, unloads=2)
        self.assertEqual("FAIL", self.status(f))

    def test_initial_unload_latency_does_not_count_as_absence(self):
        f = self.fixture((1, 10, 50))
        f[2][0]["chunks"][0].update(loaded=True, unloads=0)
        self.assertEqual("PASS", self.status(f))

    def test_real_unload_callback_required(self):
        f = self.fixture()
        for sample in f[2]: sample["chunks"][0]["unloads"] = 0
        self.assertEqual("FAIL", self.status(f))

    def test_confirmation_required_at_terminal_observation(self):
        f = self.fixture((10, 50, 90))
        f[2][1]["confirmed"] = True
        f[2][-1].pop("confirmed")
        self.assertEqual("FAIL", self.status(f))

    def test_missing_native_clock_is_not_a_zero_tick_default(self):
        f = self.fixture()
        f[2][0].pop("worldTime")
        self.assertEqual("FAIL", self.status(f))

    def test_backwards_clock_does_not_create_a_longer_interval(self):
        self.assertEqual("FAIL", self.status(self.fixture((10, 5, 50))))

    def test_non_integer_clock_fails_closed(self):
        f = self.fixture()
        f[2][0]["worldTime"] = 10.0
        self.assertEqual("FAIL", self.status(f))

    def test_retained_endpoint_must_remain_loaded(self):
        f = self.fixture()
        f[2][0]["chunks"][1]["loaded"] = False
        self.assertEqual("FAIL", self.status(f))

    def test_retained_event_counts_must_not_change(self):
        f = self.fixture()
        f[2][0]["chunks"][1]["loads"] = 2
        self.assertEqual("FAIL", self.status(f))

    def test_retained_tile_identity_must_not_change(self):
        f = self.fixture()
        f[2][0]["cases"][f[0]["id"]][-1]["tileIdentity"] = 43
        self.assertEqual("FAIL", self.status(f))

    def test_reload_requires_a_real_load_callback(self):
        f = self.fixture()
        f[3]["chunks"][0]["loads"] = 1
        self.assertEqual("FAIL", self.status(f))

    def test_negative_event_counts_rejected(self):
        f = self.fixture()
        for sample in f[2]: sample["chunks"][0]["loads"] = -1
        self.assertEqual("FAIL", self.status(f))

    def test_missing_selected_chunk_fails_closed(self):
        f = self.fixture()
        f[2][0]["chunks"].pop(0)
        self.assertEqual("FAIL", self.status(f))

    def test_inputs_are_not_rewritten(self):
        f = self.fixture((10, 30, 70))
        expected = deepcopy(f)
        self.assertEqual("PASS", self.status(f))
        self.assertEqual(expected, f)


if __name__ == "__main__":
    unittest.main()
