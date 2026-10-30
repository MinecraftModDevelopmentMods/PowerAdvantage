"""The new native suite must reject incomplete jobs and extra/lost restarted state."""

import copy
import unittest

from native115_machine_operations import REQUIRED, validate_operation, validate_restart


def stored(nbt='{Items:[{Slot:0b,id:"minecraft:dirt",Count:16b}],Energy:[I;1065353216]}'):
    return dict(id="power/test", positions=[dict(x=50000, y=80, z=50000, block="minecraft:chest", state="chest", nbt=nbt)])


class FullProcessMachineOracleTests(unittest.TestCase):
    def pair(self):
        return {"cases": [stored()]}, dict(held=True, constructionCallsThisProcess=0, cases=[stored()])

    def test_actual_same_loaded_state_passes(self):
        before, after = self.pair()
        self.assertEqual("PASS", validate_restart(before, after)["status"])

    def test_target_only_extra_resource_is_not_ignored(self):
        before, after = self.pair()
        after["cases"][0]["positions"][0]["nbt"] = '{Items:[{Slot:0b,id:"minecraft:dirt",Count:16b}],Energy:[I;1065353216],HiddenTank:{Amount:1000}}'
        self.assertEqual("FAIL", validate_restart(before, after)["status"])

    def test_changed_quantity_or_nbt_type_fails(self):
        for bad in ('{Items:[{Slot:0b,id:"minecraft:dirt",Count:15b}],Energy:[I;1065353216]}',
                    '{Items:[{Slot:0b,id:"minecraft:dirt",Count:16}],Energy:[I;1065353216]}'):
            before, after = self.pair()
            after["cases"][0]["positions"][0]["nbt"] = bad
            self.assertEqual("FAIL", validate_restart(before, after)["status"])

    def test_missing_or_rebuilt_or_unfrozen_cases_fail(self):
        for bad in (dict(held=True, constructionCallsThisProcess=0, cases=[]),
                    dict(held=True, constructionCallsThisProcess=1, cases=[stored()]),
                    dict(held=False, constructionCallsThisProcess=0, cases=[stored()])):
            with self.assertRaises(AssertionError): validate_restart({"cases": [stored()]}, bad)

    def test_extra_position_fails(self):
        before, after = self.pair()
        extra = copy.deepcopy(after["cases"][0]["positions"][0]); extra["x"] += 1
        after["cases"][0]["positions"].append(extra)
        self.assertEqual("FAIL", validate_restart(before, after)["status"])

    def test_operating_catalog_cannot_be_empty(self):
        with self.assertRaises(AssertionError): validate_operation({"cases": []}, {"cases": []}, "all")

    def test_duplicate_entity_uuid_is_not_collapsed(self):
        before, after = self.pair()
        actor = dict(uuid="example", nbt='{Health:20.0f}')
        before["cases"][0]["entities"] = [actor]
        after["cases"][0]["entities"] = [actor, actor]
        with self.assertRaises(AssertionError): validate_restart(before, after)

    def test_only_exact_zero_volume_named_tank_representation_may_change(self):
        for original, expected in (("{Amount:0,FluidName:\"poweradvantage:crude_oil\"}", "PASS"),
                                   ("{Amount:1,FluidName:\"poweradvantage:crude_oil\"}", "FAIL"),
                                   ("{Amount:0,FluidName:\"poweradvantage:crude_oil\",Hidden:1}", "FAIL"),
                                   ("{Amount:0b,FluidName:\"poweradvantage:crude_oil\"}", "FAIL")):
            before, after = self.pair()
            before["cases"][0]["positions"][0]["nbt"] = "{Tank:" + original + "}"
            after["cases"][0]["positions"][0]["nbt"] = "{Tank:{Empty:1b}}"
            self.assertEqual(expected, validate_restart(before, after)["status"])

    def test_pass_label_cannot_hide_short_clock_missing_output_or_failed_assertion(self):
        ids = sorted("power/" + name for name in REQUIRED["power"])
        catalog = dict(cases=[dict(id=key) for key in ids])
        good = dict(status="PASS", held=True, cases=[dict(id=key, status="PASS", verifiedAssertions=1,
                    assertions=[dict(phase="verify", status="PASS")], before=[stored()], after=[stored()],
                    startTick=100, finishTick=180, requiredTicks=80) for key in ids])
        self.assertEqual("PASS", validate_operation(catalog, good, "power")["status"])
        for patch in (dict(finishTick=179), dict(verifiedAssertions=0), dict(after=[]),
                      dict(assertions=[dict(phase="verify", status="FAIL")])):
            bad = copy.deepcopy(good); bad["cases"][0].update(patch)
            self.assertEqual("FAIL", validate_operation(catalog, bad, "power")["status"])


if __name__ == "__main__": unittest.main()
