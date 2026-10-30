"""Binary native NBT, resource loss and incomplete operation must not pass."""

import base64
import copy
import gzip
import io
import unittest
import nbtlib
from native110_machine_operations import REQUIRED, validate_operation, validate_restart


def record(text):
    tag = nbtlib.parse_nbt(text)
    output = io.BytesIO()
    nbtlib.File(tag).write(output)
    return dict(nbt="legacy display is not a typed protocol", nbtBase64=base64.b64encode(
        gzip.compress(output.getvalue())).decode("ascii"))


def case(text='{Items:[{Slot:0b,id:"minecraft:dirt",Count:16b}],Energy:[I;1065353216],growth:[B;9b]}'):
    row = dict(x=50000, y=80, z=50000, block="minecraft:chest", state="chest", **record(text))
    return dict(id="power/test", positions=[row])


class Native110RestartOracleTest(unittest.TestCase):
    def pair(self):
        return dict(cases=[case()]), dict(held=True, constructionCallsThisProcess=0, cases=[case()])

    def test_exact_binary_types_survive(self):
        before, after = self.pair()
        self.assertEqual("PASS", validate_restart(before, after)["status"])

    def test_missing_binary_record_never_falls_back_to_lossy_snbt(self):
        before, after = self.pair()
        del after["cases"][0]["positions"][0]["nbtBase64"]
        with self.assertRaises(KeyError): validate_restart(before, after)

    def test_lost_quantity_changed_type_and_extra_resource_fail(self):
        for text in ('{Items:[{Slot:0b,id:"minecraft:dirt",Count:15b}],Energy:[I;1065353216],growth:[B;9b]}',
                     '{Items:[{Slot:0b,id:"minecraft:dirt",Count:16}],Energy:[I;1065353216],growth:[B;9b]}',
                     '{Items:[{Slot:0b,id:"minecraft:dirt",Count:16b}],Energy:[I;1065353216],growth:[B;9b],Hidden:1000}'):
            before, after = self.pair()
            after["cases"][0]["positions"][0].update(record(text))
            self.assertEqual("FAIL", validate_restart(before, after)["status"])

    def test_unknown_and_duplicate_actors_do_not_disappear(self):
        before, after = self.pair()
        actor = dict(uuid="test-actor", **record("{Health:20.0f}"))
        before["cases"][0]["entities"] = [actor]
        after["cases"][0]["entities"] = [actor, actor]
        with self.assertRaises(AssertionError): validate_restart(before, after)

    def test_rebuilt_unfrozen_or_missing_cases_are_not_restart_proof(self):
        for mutation in (dict(constructionCallsThisProcess=1), dict(held=False), dict(cases=[])):
            before, after = self.pair()
            after.update(mutation)
            with self.assertRaises(AssertionError): validate_restart(before, after)

    def test_complete_catalog_and_measured_outputs_are_required(self):
        ids = sorted("power/" + name for name in REQUIRED["power"])
        catalog = dict(cases=[dict(id=key) for key in ids], notApplicable=[
            dict(id="pickup/" + name, status="NOT_APPLICABLE")
            for name in ("power-drain", "steam-pump", "electric-pump")])
        good = dict(status="PASS", held=True, cases=[dict(id=key, status="PASS", verifiedAssertions=1,
                    assertions=[dict(phase="verify", status="PASS")], before=[case()], after=[case()],
                    startTick=100, finishTick=180, requiredTicks=80) for key in ids])
        self.assertEqual("PASS", validate_operation(catalog, good, "power")["status"])
        for mutation in (dict(verifiedAssertions=0), dict(finishTick=179), dict(after=[]),
                         dict(assertions=[dict(phase="verify", status="FAIL")])):
            broken = copy.deepcopy(good)
            broken["cases"][0].update(mutation)
            self.assertEqual("FAIL", validate_operation(catalog, broken, "power")["status"])
