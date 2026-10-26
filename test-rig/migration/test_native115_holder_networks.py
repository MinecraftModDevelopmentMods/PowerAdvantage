"""The full native holder halo is wider than the FULL-status halo."""

import unittest
from native115_holder_networks import (FORCED_LEVEL, HOLDER_RADIUS, LAST_HOLDER_LEVEL,
                                       bridge_fixtures, extended_fixtures, linear_fixtures)
from native115_ticket_networks import fill_commands
import extended_lifecycle


class HolderNetworkTests(unittest.TestCase):
    def test_exact_native_holder_threshold_is_not_confused_with_full_threshold(self):
        self.assertEqual(FORCED_LEVEL, 31)
        self.assertEqual(LAST_HOLDER_LEVEL, 33 + 11)
        self.assertEqual(HOLDER_RADIUS, 13)
        self.assertGreater(FORCED_LEVEL + HOLDER_RADIUS + 1, LAST_HOLDER_LEVEL)

    def test_every_retained_ticket_is_outside_full_holder_dependency_radius(self):
        for definition in (linear_fixtures(), bridge_fixtures()):
            for f in definition:
                all_chunks = {(x, z) for x in range(f["min"][0] >> 4, (f["max"][0] >> 4) + 1)
                              for z in range(f["min"][2] >> 4, (f["max"][2] >> 4) + 1)}
                for group in ("bridge",) if "bridge" in f["groups"] else ("supply", "machine"):
                    retained = all_chunks - set(map(tuple, f["groups"][group + "halo"]))
                    self.assertTrue(retained)
                    self.assertTrue(all(min(max(abs(x-a), abs(z-b)) for a, b in f["groups"][group]) > 13 for x, z in retained))

    def test_long_paths_and_all_witnesses_stay_inside_separate_bays(self):
        for definition in (linear_fixtures(), bridge_fixtures(), extended_fixtures()):
            for index, f in enumerate(definition):
                for p in f["witnesses"]:
                    self.assertTrue(all(a <= n <= b for a, n, b in zip(f["min"], p, f["max"])))
                for other in definition[index + 1:]:
                    self.assertTrue(any(f["max"][a] < other["min"][a] or other["max"][a] < f["min"][a] for a in range(3)))

    def test_paths_are_connected(self):
        for f in linear_fixtures() + bridge_fixtures():
            sink = f["witnesses"][1] if "bridge" in f["groups"] else f["witnesses"][4]
            path = [f["witnesses"][0], *f["pipes"], sink]
            self.assertTrue(all(sum(abs(x-y) for x, y in zip(a,b)) == 1 for a,b in zip(path,path[1:])))
            self.assertEqual(len(path), len(set(map(tuple,path))))

    def test_drill_geometry_is_not_changed_to_accept_network_results(self):
        self.assertEqual(extended_fixtures()[:18], [f for f in extended_lifecycle.fixtures() if "route" in f])

    def test_native_fill_is_still_bounded_below_vanilla_volume_limit(self):
        for f in linear_fixtures() + bridge_fixtures():
            commands = fill_commands([f], "fill " + " ".join(map(str,f["min"]+f["max"])) + " minecraft:air replace")
            self.assertEqual(len(commands), 68)
            for command in commands:
                values = list(map(int, command.split()[1:7]))
                volume = 1
                for a,b in zip(values[:3],values[3:]):
                    volume *= b-a+1
                self.assertLessEqual(volume,32768)


if __name__ == "__main__":
    unittest.main()
