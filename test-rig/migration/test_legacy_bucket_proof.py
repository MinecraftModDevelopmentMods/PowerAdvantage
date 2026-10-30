"""Fail-closed proof gates; no Minecraft process, builders or artifact substitutions."""
import copy
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import legacy_bucket_proof as proof

NOTICE = ("[12:34:56] [Server thread/ERROR] [FML]: Trying to activate the universal filled bucket too late. "
          "Call it statically in your Mods class. Mod: Base Metals")


def boot():
    return ["Starting minecraft server version 1.10.2",
            "Forge Mod Loader version 12.18.3.2511 for Minecraft 1.10.2 loading", NOTICE]


def records():
    rows = []
    for name in ("startup", "crude_oil", "refined_oil"):
        row = {"id": "power/bucket/" + name, "status": "PASS", "bucket/enabled": True,
               "bucket/created": True, "bucket/registered": True}
        if name != "startup":
            row.update({"bucket/placedFluid": name, "bucket/placedAmount": 1000,
                        "bucket/donorRemaining": 0, "bucket/sourceGone": True})
        rows.append(row)
    return {"cases": rows}


class Text(str):
    tag_id = 8


class Byte(int):
    tag_id = 1


class Integer(int):
    tag_id = 3


def typed_fixture():
    operation, inspected = {"cases": []}, {"cases": []}
    for name in ("startup", "crude_oil", "refined_oil"):
        rows = [] if name == "startup" else [{"nbtBase64": "native-binary-witness", "data": {"Items": [{
            "id": Text("forge:bucketFilled"), "Count": Byte(1),
            "tag": {"FluidName": Text(name), "Amount": Integer(1000)}}]}}]
        operation["cases"].append({"id": "power/bucket/" + name, "after": rows})
        inspected["cases"].append({"id": "power/bucket/" + name, "positions": copy.deepcopy(rows)})
    return operation, inspected


class LegacyBucketProofTest(unittest.TestCase):
    def test_non_power_pins_and_complete_code_inventory_must_be_identical(self):
        baseline = dict(mods=[dict(id=mod, version="declared", runtimeVersion="declared", sha256=mod)
                              for mod in sorted(proof.MOD_IDS)],
                        codeInventory={"files": {proof.LAUNCHER: {"sha256": proof.PUBLIC_FORGE_SHA},
                                                 "libraries/native.jar": {"sha256": "sealed"}}})
        candidate = copy.deepcopy(baseline)
        next(row for row in candidate["mods"] if row["id"] == "poweradvantage")["sha256"] = "new-power"
        bindings = dict(baseline=baseline, candidate=candidate)
        proof.assert_paired_pins(bindings)
        for mod in ("advantageworkstest", "orespawn", "basemetals"):
            for field in ("sha256", "version", "runtimeVersion"):
                bad = copy.deepcopy(bindings)
                next(row for row in bad["candidate"]["mods"] if row["id"] == mod)[field] = "changed"
                with self.assertRaises(AssertionError): proof.assert_paired_pins(bad)
        for path in (proof.LAUNCHER, "libraries/native.jar"):
            bad = copy.deepcopy(bindings)
            bad["candidate"]["codeInventory"]["files"][path]["sha256"] = "changed"
            with self.assertRaises(AssertionError): proof.assert_paired_pins(bad)
        bad = copy.deepcopy(bindings)
        bad["candidate"]["mods"].append(dict(id="steamadvantage"))
        with self.assertRaises(AssertionError): proof.assert_paired_pins(bad)

    def test_exact_line_is_pending_not_an_accepted_notice(self):
        result = proof.held_diagnostics(boot(), proof.BASE_SHA, proof.PUBLIC_FORGE_SHA)
        self.assertEqual(proof.PENDING, result["status"])
        self.assertEqual("ERROR", result["originalSeverity"])
        self.assertNotIn("classification", result)

    def test_wrong_dependency_hashes_are_never_eligible(self):
        for base, forge in (("0" * 64, proof.PUBLIC_FORGE_SHA), (proof.BASE_SHA, "0" * 64)):
            with self.assertRaises(AssertionError): proof.held_diagnostics(boot(), base, forge)

    def test_missing_duplicate_changed_or_different_mod_message_is_fatal(self):
        for lines in (boot()[:-1], boot() + [NOTICE], boot()[:-1] + [NOTICE + " "],
                      boot()[:-1] + [NOTICE.replace("Base Metals", "Power Advantage")]):
            with self.assertRaises(AssertionError):
                proof.held_diagnostics(lines, proof.BASE_SHA, proof.PUBLIC_FORGE_SHA)

    def test_all_other_errors_remain_fatal_and_native_markers_required(self):
        for lines in (boot() + ["[12:34:57] [Server thread/ERROR] [FML]: Unrelated failure"],
                      boot() + ["java.lang.NoSuchMethodError: broken"], [NOTICE]):
            with self.assertRaises(AssertionError):
                proof.held_diagnostics(lines, proof.BASE_SHA, proof.PUBLIC_FORGE_SHA)

    def test_flags_must_be_actual_true_registered_and_token_bound(self):
        valid = dict(token="a" * 32, enabled=True, created=True, registered=True,
                     item="forge:bucketFilled", world="AdvantageWorksMigration", mods={"basemetals": "2.4.0"})
        proof.startup_flags(valid, "a" * 32)
        proof.startup_flags(valid, "a" * 32, {"basemetals": "2.4.0"})
        with self.assertRaises(AssertionError):
            proof.startup_flags(valid, "a" * 32, {"basemetals": "2.4.0.11"})
        for key, value in (("enabled", False), ("registered", 1), ("created", None),
                           ("token", "wrong"), ("mods", {})):
            bad = copy.deepcopy(valid)
            bad[key] = value
            with self.assertRaises(AssertionError): proof.startup_flags(bad, "a" * 32)

    def test_complete_measured_bucket_records_required(self):
        proof.bucket_records(records())
        for key, value in (("bucket/donorRemaining", 1), ("bucket/placedAmount", 999),
                           ("bucket/sourceGone", False), ("bucket/placedFluid", "water"),
                           ("bucket/enabled", False), ("status", "FAIL")):
            bad = records()
            bad["cases"][1][key] = value
            with self.assertRaises(AssertionError): proof.bucket_records(bad)
        for bad in ({"cases": records()["cases"][:2]}, {"cases": records()["cases"] * 2}):
            with self.assertRaises(AssertionError): proof.bucket_records(bad)

    def test_typed_native_bucket_identity_amount_and_cold_stock_are_required(self):
        native = SimpleNamespace(recorded_nbt=lambda row: row["data"])
        before, after = typed_fixture()
        proof.typed_buckets(native, before, after)
        for field, value in (("id", Text("minecraft:water_bucket")), ("Count", Byte(2)),
                             ("Amount", 1000), ("Amount", Integer(999)), ("FluidName", Text("water"))):
            before, after = typed_fixture()
            stack = after["cases"][1]["positions"][0]["data"]["Items"][0]
            (stack if field in ("id", "Count") else stack["tag"])[field] = value
            with self.assertRaises(AssertionError): proof.typed_buckets(native, before, after)

    def test_validation_helpers_do_not_import_runtime_builders(self):
        with patch.object(proof.importlib, "import_module", side_effect=AssertionError("runtime import")):
            proof.bucket_records(records())
            proof.held_diagnostics(boot(), proof.BASE_SHA, proof.PUBLIC_FORGE_SHA)


if __name__ == "__main__": unittest.main()
