"""Native layout safety and ticket-halo geometry, without accepting oracle failures."""

import copy
import json
import unittest
from unittest.mock import patch

import extended_lifecycle
from native116_partial import NativePartialRun
from native116_ticket_networks import (NativeTicketNetworkRun, bridge_fixtures, extended_fixtures,
                                      fill_commands, halo_chunks, linear_fixtures)


class NativeTicketFixtureTests(unittest.TestCase):
    def test_historical_drill_fixtures_are_identical(self):
        self.assertEqual([f for f in extended_lifecycle.fixtures() if "route" in f], extended_fixtures()[:18])

    def test_all_native_bounds_and_paths_are_contained_and_do_not_overlap(self):
        for definition in (linear_fixtures(), bridge_fixtures(), extended_fixtures()):
            for index, f in enumerate(definition):
                for p in f["witnesses"]:
                    self.assertTrue(all(a <= n <= b for a, n, b in zip(f["min"], p, f["max"])))
                for other in definition[index + 1:]:
                    self.assertTrue(any(f["max"][a] < other["min"][a] or other["max"][a] < f["min"][a] for a in range(3)))

    def test_retained_ticket_is_outside_selected_halo(self):
        for f in linear_fixtures():
            for group, other in (("supply", "machine"), ("machine", "supply")):
                self.assertTrue(set(map(tuple, f["groups"][group + "halo"])).isdisjoint(map(tuple, f["groups"][other])))
                core = f["groups"][group][0]
                all_chunks = {(x, z) for x in range(f["min"][0] >> 4, (f["max"][0] >> 4) + 1)
                              for z in range(f["min"][2] >> 4, (f["max"][2] >> 4) + 1)}
                retained = all_chunks - set(map(tuple, f["groups"][group + "halo"]))
                self.assertTrue(all(max(abs(x-core[0]), abs(z-core[1])) > 2 for x, z in retained))

    def test_bridge_halo_does_not_remove_endpoint_ticket(self):
        for f in bridge_fixtures():
            self.assertTrue(set(map(tuple, f["groups"]["bridgehalo"])).isdisjoint(map(tuple, f["groups"]["endpoints"])))
            self.assertEqual(f["groups"]["bridgehalo"], halo_chunks(f, "bridge"))

    def test_linear_path_is_native_neighbor_connected_and_128_blocks_long(self):
        for f in linear_fixtures():
            path = [f["witnesses"][0], *f["pipes"], f["witnesses"][4]]
            self.assertEqual(len(path), 129)
            self.assertTrue(all(sum(abs(x-y) for x, y in zip(a, b)) == 1 for a, b in zip(path, path[1:])))
            self.assertEqual(len(set(map(tuple, f["witnesses"]))), len(f["witnesses"]))

    def test_bridge_is_one_connected_u_without_shortcut(self):
        for f in bridge_fixtures():
            path = [f["witnesses"][0], *f["pipes"], f["witnesses"][1]]
            self.assertTrue(all(sum(abs(x-y) for x, y in zip(a, b)) == 1 for a, b in zip(path, path[1:])))
            self.assertEqual(len(set(map(tuple, path))), len(path))
            self.assertEqual(len(f["pipes"]), 259)

    def test_clearing_is_split_and_exactly_confined_to_declared_bounds(self):
        for f in linear_fixtures() + bridge_fixtures():
            command = "fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace"
            covered = set()
            for piece in fill_commands([f], command):
                p = piece.split()
                low, high = list(map(int, p[1:4])), list(map(int, p[4:7]))
                volume = 1
                for a, b in zip(low, high):
                    volume *= b - a + 1
                self.assertLessEqual(volume, 32768)
                cells = {(x, z) for x in range(low[0], high[0] + 1) for z in range(low[2], high[2] + 1)}
                self.assertTrue(covered.isdisjoint(cells))
                covered |= cells
            self.assertEqual(len(covered), 160 * 32)
            outside = command.replace(str(f["min"][0]), "0", 1)
            self.assertIsNone(fill_commands([f], outside))

    def test_release_observes_before_releasing_halo_then_core(self):
        runner = NativeTicketNetworkRun(None, None, None, linear_fixtures())
        before = {"raw": "before"}
        with patch.object(runner, "_raw_probe", side_effect=[before, {}, {"raw": "after"}]) as raw:
            runner.probe("release-supply", "released")
        self.assertEqual([call.args[0] for call in raw.call_args_list],
                         ["snapshot", "release-supplyhalo", "release-supply"])
        self.assertIs(runner._released_groups["supply"]["before"], before)

    def test_load_restores_only_requested_halo_before_core_readiness(self):
        runner = NativeTicketNetworkRun(None, None, None, linear_fixtures())
        runner._released_groups["supply"] = {}
        runner._released_groups["machine"] = {}
        with patch.object(runner, "_raw_probe") as raw, patch.object(runner, "_load_ready", return_value={}) as ready:
            runner.probe("load-supply", "loaded")
        self.assertEqual(raw.call_args.args[0], "load-supplyhalo")
        ready.assert_called_once_with("load-supply", "loaded")
        self.assertNotIn("supply", runner._released_groups)
        self.assertIn("machine", runner._released_groups)


if __name__ == "__main__":
    unittest.main()
