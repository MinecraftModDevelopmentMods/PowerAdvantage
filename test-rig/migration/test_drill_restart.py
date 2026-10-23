"""Native first-tick checks cannot accept a reset or a newly primed drill."""
import copy
import unittest

from connected import entries
from drill_restart import first_tick
from test_connected import nbt, sample


def checkpoints():
    before = sample()
    for station, value in (("C-SDRILL", 17), ("C-EDRILL", 209)):
        nbt(entries(before)[station + "-drill"], '{progress:%ds,targetX:1,targetY:80,targetZ:42,Items:[]}' % value)
    after = copy.deepcopy(before)
    after.update(requestedTicks=1, advancedTicks=1)
    for station, value in (("C-SDRILL", 18), ("C-EDRILL", 210)):
        nbt(entries(after)[station + "-drill"], '{progress:%ds,targetX:1,targetY:80,targetZ:42,Items:[]}' % value)
    return before, after


class DrillRestartTest(unittest.TestCase):
    def test_both_midcycle_drills_continue(self):
        self.assertEqual(first_tick(*checkpoints())["status"], "PASS")

    def test_each_legacy_reset_is_rejected(self):
        for station, value in (("C-SDRILL", 0), ("C-EDRILL", 1)):
            before, after = checkpoints()
            nbt(entries(after)[station + "-drill"], '{progress:%ds,targetX:1,targetY:80,targetZ:42,Items:[]}' % value)
            self.assertEqual(first_tick(before, after)["status"], "FAIL")

    def test_cold_drill_is_not_saved_work_evidence(self):
        before, after = checkpoints()
        nbt(entries(before)["C-SDRILL-drill"], '{progress:0s,targetX:1,targetY:80,targetZ:42,Items:[]}')
        nbt(entries(after)["C-SDRILL-drill"], '{progress:1s,targetX:1,targetY:80,targetZ:42,Items:[]}')
        self.assertEqual(first_tick(before, after)["status"], "FAIL")

    def test_replacement_and_unmeasured_work_are_rejected(self):
        before, after = checkpoints()
        entries(after)["C-EDRILL-drill"]["objectIdentity"] = -1
        self.assertEqual(first_tick(before, after)["status"], "FAIL")
        before, after = checkpoints()
        after["advancedTicks"] = 0
        self.assertEqual(first_tick(before, after)["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
