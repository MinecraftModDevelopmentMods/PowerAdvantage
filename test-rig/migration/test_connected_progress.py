"""First-tick progress loss must remain visible even when later work succeeds."""
import copy
import unittest

from audit_connected_progress import first_tick
from connected import entries
from test_connected import nbt, sample


def checkpoints(progress):
    loaded = sample()
    nbt(entries(loaded)["C-EDRILL-drill"], '{progress:512s,targetX:270,targetY:80,targetZ:41,Items:[]}')
    first = copy.deepcopy(loaded)
    first.update(requestedTicks=1, advancedTicks=1)
    nbt(entries(first)["C-EDRILL-drill"], '{progress:%ds,targetX:270,targetY:80,targetZ:41,Items:[]}' % progress)
    return loaded, first


class ConnectedProgressTest(unittest.TestCase):
    def test_continuing_saved_work_passes(self):
        self.assertEqual(first_tick(*checkpoints(513))["status"], "PASS")

    def test_resetting_saved_work_fails(self):
        self.assertEqual(first_tick(*checkpoints(1))["status"], "FAIL")

    def test_replacement_is_rejected(self):
        loaded, first = checkpoints(513)
        entries(first)["C-EDRILL-drill"]["objectIdentity"] = -1
        with self.assertRaises(AssertionError):
            first_tick(loaded, first)

    def test_unmeasured_observation_is_rejected(self):
        loaded, first = checkpoints(513)
        first["advancedTicks"] = 0
        with self.assertRaises(AssertionError):
            first_tick(loaded, first)


if __name__ == "__main__":
    unittest.main()
