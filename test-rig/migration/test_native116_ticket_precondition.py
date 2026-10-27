"""Pure, worldless ticket-model controls; no synthetic records are live evidence."""

import copy
import json
from pathlib import Path
import tempfile
import unittest

from native116_ticket_precondition import (FORCED_LEVEL, FULL_LEVEL, HOLDER_RADIUS,
    LAST_HOLDER_LEVEL, isolation_precondition, read_ticket_snapshot, require_isolation)


DIMENSION = "minecraft:overworld"


def ticket(pos, level=31, kind="forced", active=True, **extra):
    return dict(pos=list(pos), type=kind, level=level, active=active, **extra)


def snapshot(records):
    return dict(format=1, dimension=DIMENSION, ticketMapComplete=True,
        ticketGeometry=dict(fullLevel=33, entityTickingLevel=31, maxDistance=11,
            lastHolderLevel=44, holderRadius=13,
            observedForcedLevels=sorted({r["level"] for r in records
                                        if r["active"] and r["type"] == "forced"})),
        tickets=records)


def planned_release(definition, groups, halo=False):
    """Model the source fixture's native load/release operations, not a live map."""
    all_chunks = {(x, z) for f in definition
        for x in range(f["min"][0] >> 4, (f["max"][0] >> 4) + 1)
        for z in range(f["min"][2] >> 4, (f["max"][2] >> 4) + 1)}
    removed, witnesses = set(), set()
    for f in definition:
        for group in groups:
            if group not in f.get("groups", {}):
                continue
            cores = set(map(tuple, f["groups"][group]))
            witnesses.update(cores)
            removed.update(map(tuple, f["groups"][group + "halo"]) if halo else cores)
    return snapshot([ticket(p) for p in sorted(all_chunks - removed)]), sorted(witnesses)


class TicketPreconditionTests(unittest.TestCase):
    def test_verified_native_thresholds(self):
        self.assertEqual((FORCED_LEVEL, FULL_LEVEL, LAST_HOLDER_LEVEL, HOLDER_RADIUS), (31, 33, 44, 13))

    def test_original_adjacent_z_layout_is_red_for_all_24_partial_and_12_order_cases(self):
        from extended_lifecycle import fixtures
        partial_failures, order_failures = 0, 0
        for profile in ("fresh116", "original110-chained113-native116"):
            for group in ("machine", "supply", "supply", "machine"):
                value, witnesses = planned_release(fixtures(), [group])
                result = isolation_precondition(value, witnesses, DIMENSION)
                with self.subTest(profile=profile, partial=group):
                    self.assertEqual(result["status"], "BLOCKED")
                    self.assertEqual({tuple(r["witness"]) for r in result["blockers"]}, set(witnesses))
                    with self.assertRaisesRegex(AssertionError, "isolation precondition failed"):
                        require_isolation(value, witnesses, DIMENSION)
                partial_failures += len(witnesses)
            for groups in (("machine", "supply"), ("supply", "machine")):
                value, witnesses = planned_release(fixtures(), groups)
                result = isolation_precondition(value, witnesses, DIMENSION)
                with self.subTest(profile=profile, order=groups):
                    self.assertEqual(result["status"], "BLOCKED")
                    # The unremoved z=129 row alone is sufficient to block every endpoint.
                    self.assertEqual({tuple(r["witness"]) for r in result["blockers"]
                                      if r["ticketPos"][1] == 129 and r["distance"] == 1}, set(witnesses))
                order_failures += len(witnesses) // 2
        self.assertEqual((partial_failures, order_failures), (24, 12))

    def test_holder_separated_layout_is_green_globally_for_partial_and_both_orders(self):
        from native116_holder_networks import extended_fixtures
        for groups in (("supply",), ("machine",), ("supply", "machine"), ("machine", "supply")):
            with self.subTest(groups=groups):
                value, witnesses = planned_release(extended_fixtures(), groups, halo=True)
                result = require_isolation(value, witnesses, DIMENSION)
                self.assertEqual(result["status"], "ISOLATED")
                self.assertEqual(result["scope"], "STATIC_TICKET_PRECONDITION_ONLY_NOT_RUNTIME_PASS")

    def test_holder_bridge_geometry_is_green(self):
        from native116_holder_networks import bridge_fixtures
        value, witnesses = planned_release(bridge_fixtures(), ["bridge"], halo=True)
        self.assertEqual(require_isolation(value, witnesses, DIMENSION)["status"], "ISOLATED")

    def test_full_halo_only_is_not_sufficient_for_physical_holder_unload(self):
        from native116_ticket_networks import linear_fixtures
        value, witnesses = planned_release(linear_fixtures(), ["supply"], halo=True)
        result = isolation_precondition(value, witnesses, DIMENSION)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertTrue(any(r["effectiveLevel"] > 33 for r in result["blockers"]))

    def test_holder_boundary_13_inclusive_14_exclusive(self):
        for distance, expected in ((12, "BLOCKED"), (13, "BLOCKED"), (14, "ISOLATED")):
            with self.subTest(distance=distance):
                self.assertEqual(isolation_precondition(snapshot([ticket([distance, 0])]), [[0, 0]], DIMENSION)["status"], expected)

    def test_full_boundary_is_distinct_from_holder_boundary(self):
        for distance, expected in ((2, True), (3, False), (13, False)):
            result = isolation_precondition(snapshot([ticket([distance, 0])]), [[0, 0]], DIMENSION)
            self.assertEqual(result["blockers"][0]["fullDependency"], expected)

    def test_diagonal_distance_is_chebyshev_not_manhattan(self):
        result = isolation_precondition(snapshot([ticket([13, -13])]), [[0, 0]], DIMENSION)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertEqual(result["blockers"][0]["distance"], 13)
        self.assertEqual(result["blockers"][0]["effectiveLevel"], 44)

    def test_negative_chunks_and_cross_origin_distance(self):
        self.assertEqual(isolation_precondition(snapshot([ticket([-14, -14])]), [[-1, -1]], DIMENSION)["status"], "BLOCKED")
        self.assertEqual(isolation_precondition(snapshot([ticket([-15, -15])]), [[-1, -1]], DIMENSION)["status"], "ISOLATED")
        self.assertEqual(isolation_precondition(snapshot([ticket([-7, -7])]), [[6, 6]], DIMENSION)["status"], "BLOCKED")

    def test_other_active_ticket_types_and_levels_are_never_filtered(self):
        for kind in ("player", "start", "portal", "unknown", "othermod:loader"):
            for level in (0, 22, 31, 33, 44):
                radius = 44 - level
                with self.subTest(kind=kind, level=level):
                    self.assertEqual(isolation_precondition(snapshot([ticket([radius, -radius], level, kind)]), [[0, 0]], DIMENSION)["status"], "BLOCKED")
                    self.assertEqual(isolation_precondition(snapshot([ticket([radius + 1, 0], level, kind)]), [[0, 0]], DIMENSION)["status"], "ISOLATED")

    def test_foreign_fixture_ticket_can_block_an_otherwise_green_holder_layout(self):
        from native116_holder_networks import linear_fixtures
        value, witnesses = planned_release(linear_fixtures(), ["machine"], halo=True)
        self.assertEqual(require_isolation(value, witnesses, DIMENSION)["status"], "ISOLATED")
        value["tickets"].append(ticket(witnesses[0], 33, "unrelated_mod:loader"))
        result = isolation_precondition(value, witnesses, DIMENSION)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertTrue(any(r["ticketType"] == "unrelated_mod:loader" for r in result["blockers"]))

    def test_explicitly_inactive_tickets_do_not_influence_but_no_timeout_is_inferred(self):
        self.assertEqual(isolation_precondition(snapshot([ticket([0, 0], active=False)]), [[0, 0]], DIMENSION)["status"], "ISOLATED")
        self.assertEqual(isolation_precondition(snapshot([ticket([0, 0], 33, "unknown")]), [[0, 0]], DIMENSION)["status"], "BLOCKED")

    def test_force_ticks_false_does_not_disable_chunk_influence(self):
        for force in (False, True):
            self.assertEqual(isolation_precondition(snapshot([ticket([0, 0], forceTicks=force)]), [[0, 0]], DIMENSION)["status"], "BLOCKED")

    def test_all_blockers_are_reported_even_multiple_records_at_one_chunk(self):
        records = [ticket([0, 0]), ticket([0, 0], 33, "portal"), ticket([100, 100])]
        result = isolation_precondition(snapshot(records), [[0, 0]], DIMENSION)
        self.assertEqual([r["ticketIndex"] for r in result["blockers"]], [0, 1])

    def test_weaker_than_last_holder_ticket_is_not_a_blocker(self):
        self.assertEqual(isolation_precondition(snapshot([ticket([0, 0], 45, "light")]), [[0, 0]], DIMENSION)["status"], "ISOLATED")

    def test_inputs_and_returned_positions_do_not_alias(self):
        value, witnesses = snapshot([ticket([0, 0])]), [[1, 1]]
        before = copy.deepcopy((value, witnesses))
        result = isolation_precondition(value, witnesses, DIMENSION)
        result["blockers"][0]["ticketPos"][0] = 123
        result["witnesses"][0][0] = 456
        self.assertEqual((value, witnesses), before)

    def test_complete_empty_map_is_only_a_static_clear_precondition(self):
        result = require_isolation(snapshot([]), [[0, 0]], DIMENSION)
        self.assertEqual(result["status"], "ISOLATED")
        self.assertNotIn("qualification", result)

    def test_incomplete_g_level_summary_is_rejected_not_invented_into_records(self):
        value = snapshot([])
        del value["tickets"]
        with self.assertRaises(ValueError):
            isolation_precondition(value, [[0, 0]], DIMENSION)

    def test_malformed_and_incomplete_snapshot_metadata_fail_closed(self):
        for field, bad in (("format", True), ("format", 2), ("ticketMapComplete", False),
                           ("ticketMapComplete", 1), ("dimension", "overworld"),
                           ("tickets", {}), ("ticketGeometry", {})):
            value = snapshot([])
            value[field] = bad
            with self.subTest(field=field, bad=bad), self.assertRaises(ValueError):
                isolation_precondition(value, [[0, 0]], DIMENSION)
        for field, bad in (("fullLevel", 32), ("lastHolderLevel", 43), ("holderRadius", 2),
                           ("entityTickingLevel", True), ("observedForcedLevels", [31])):
            value = snapshot([])
            value["ticketGeometry"][field] = bad
            with self.subTest(geometry=field), self.assertRaises(ValueError):
                isolation_precondition(value, [[0, 0]], DIMENSION)

    def test_malformed_ticket_records_fail_closed_instead_of_being_skipped(self):
        good = ticket([0, 0])
        malformed = [None, {}, {**good, "unexpected": 1}]
        malformed += [{k: v for k, v in good.items() if k != missing} for missing in good]
        malformed += [{**good, field: bad} for field, bad in (
            ("pos", [0]), ("pos", [False, 0]), ("pos", [0.0, 0]),
            ("pos", [2 ** 31, 0]), ("pos", [0, -(2 ** 31) - 1]),
            ("type", ""), ("type", None), ("level", -1), ("level", True),
            ("level", 31.0), ("level", 2 ** 31), ("level", 32),
            ("active", 1), ("forceTicks", 0))]
        for record in malformed:
            value = snapshot([])
            value["tickets"] = [record]
            with self.subTest(record=record), self.assertRaises(ValueError):
                isolation_precondition(value, [[0, 0]], DIMENSION)

    def test_forced_observation_must_match_active_ticket_records(self):
        value = snapshot([ticket([20, 20])])
        for observed in ([], [True], [31, 31], [32]):
            value["ticketGeometry"]["observedForcedLevels"] = observed
            with self.subTest(observed=observed), self.assertRaises(ValueError):
                isolation_precondition(value, [[0, 0]], DIMENSION)

    def test_invalid_witnesses_and_wrong_dimension_fail_closed(self):
        for witnesses in ([], None, "0,0", [[0]], [[True, 0]], [[0, 0], [0, 0]]):
            with self.subTest(witnesses=witnesses), self.assertRaises(ValueError):
                isolation_precondition(snapshot([]), witnesses, DIMENSION)
        with self.assertRaises(ValueError):
            isolation_precondition(snapshot([]), [[0, 0]], "minecraft:the_nether")
        for dimension in ("", "minecraft:", ":overworld", "Minecraft:overworld", "minecraft:a b", "a:b:c"):
            with self.subTest(dimension=dimension), self.assertRaises(ValueError):
                isolation_precondition(snapshot([]), [[0, 0]], dimension)

    def test_reader_preserves_explicit_global_ticket_records(self):
        value = snapshot([ticket([-7, 3]), ticket([100, -100], 33, "foreign:loader")])
        with tempfile.TemporaryDirectory(prefix="native116-ticket-precondition-") as directory:
            path = Path(directory) / "native-ticket-map.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual(read_ticket_snapshot(path), value)

    def test_reader_rejects_duplicate_json_fields_and_nonfinite_numbers(self):
        encoded = json.dumps(snapshot([ticket([0, 0])]))
        invalid = [encoded.replace('"format": 1', '"format": 1, "format": 1'),
                   encoded.replace('"level": 31', '"level": 31, "level": 45'),
                   encoded.replace('"level": 31', '"level": NaN'),
                   encoded.replace('"level": 31', '"level": Infinity'), '{']
        with tempfile.TemporaryDirectory(prefix="native116-ticket-precondition-") as directory:
            path = Path(directory) / "malformed.json"
            for text in invalid:
                path.write_text(text, encoding="utf-8")
                with self.subTest(text=text), self.assertRaises(ValueError):
                    read_ticket_snapshot(path)


if __name__ == "__main__":
    unittest.main()
