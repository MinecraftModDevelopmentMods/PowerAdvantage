"""Synthetic mutation controls for the offline auditor; not native qualification."""

import base64
import contextlib
from copy import deepcopy
import gzip
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import nbtlib
import steam_control_audit as audit


LINE = Path(__file__).resolve().parents[3].name[2:]


def snapshot(position, block, data=None):
    row = dict(zip(("x", "y", "z"), position), block=block, state=block)
    if data is not None:
        data = deepcopy(data)
        data.update({key: nbtlib.Int(value) for key, value in zip(("x", "y", "z"), position)})
        row["nbt"] = nbtlib.serialize_tag(data)
        if LINE == "110":
            stream = io.BytesIO()
            nbtlib.File(data).write(stream)
            row["nbtBase64"] = base64.b64encode(gzip.compress(stream.getvalue())).decode("ascii")
    return row


def data(item, count, slot, reserve=0, work=None, furnace=None):
    entries = []
    if count:
        entries.append(nbtlib.Compound(Slot=nbtlib.Byte(slot), Count=nbtlib.Byte(count),
                                       id=nbtlib.String(item), Damage=nbtlib.Short(0)))
    value = nbtlib.Compound(Items=nbtlib.List[nbtlib.Compound](entries),
                           Energy=nbtlib.IntArray([audit.bits(reserve)]))
    if work is not None:
        value["progress"] = nbtlib.Short(work)
    if furnace is not None:
        value["Data"] = nbtlib.IntArray(furnace)
    return value


def fixture():
    base = audit.native(LINE)
    ids = sorted({district + "/" + name for district, names in base.REQUIRED.items() for name in names} | audit.IDS)
    catalog = {"group": "all", "cases": []}
    if LINE in ("110", "112"):
        catalog["notApplicable"] = [dict(id="pickup/" + name, status="NOT_APPLICABLE")
                                   for name in ("power-drain", "steam-pump", "electric-pump")]
    operation = {"status": "PASS", "held": True, "cases": []}
    saved = {"cases": []}
    for index, case_id in enumerate(ids):
        origin = (50000 + index * 64, 80, 50000)
        def at(x):
            return (origin[0] + x, origin[1] + 4, origin[2] + 8)
        row = dict(id=case_id, status="PASS", startTick=10, finishTick=11, requiredTicks=1,
                   verifiedAssertions=1, assertions=[dict(phase="verify", status="PASS")])
        if case_id == audit.COMPARATOR:
            before, after = [], []
            for i, stock in enumerate((0, 1, 32, 64)):
                tile = snapshot(at(3 + 6 * i), "steamadvantage:steam_crusher",
                                data("minecraft:stone", stock, 0, work=0))
                before.append(tile); after.append(deepcopy(tile))
                row["count/" + str(3 + 6 * i) + "/4/8/minecraft:stone"] = stock
            row.update({"comparator.inputs": [0, 1, 32, 64], "comparator.actual": [0, 1, 8, 15]})
        elif case_id in audit.CRUSHERS or case_id in audit.FURNACES:
            crusher = case_id in audit.CRUSHERS
            signal, initial = audit.CRUSHERS[case_id] if crusher else (audit.FURNACES[case_id], 40)
            if crusher:
                block, item, slot = "steamadvantage:steam_crusher", "minecraft:stone", 0
                work = 105 if signal or initial == 0 else 107
                reserve = 0 if initial == 0 else 39.9375 if signal else 38.4375
                before_data = data(item, 1, slot, initial, work=106)
                after_data = data(item, 1, slot, reserve, work=work)
                row["firstLoaded.progress"] = work
            else:
                block, item, slot = "steamadvantage:steam_furnace", "minecraft:sand", 1
                heat = 989.5 if signal else 1027.0
                work = audit.f32(.25 + audit.f32(audit.f32(3.5e-6) * heat))
                reserve = 40 if signal else 39.5
                before_data = data(item, 1, slot, initial, furnace=[audit.bits(.25), 0, 0, 10, 10, audit.bits(1000)])
                after_data = data(item, 1, slot, reserve, furnace=[audit.bits(work), 0, 0, 9, 10, audit.bits(heat)])
                row.update({"firstLoaded.heat": heat, "firstLoaded.progress": work})
            before = [snapshot(at(8), block, before_data)]
            after = [snapshot(at(8), block, after_data)]
            if signal:
                control = snapshot(at(7), "minecraft:redstone_block")
                before.append(control); after.append(deepcopy(control))
            row.update({"firstLoaded.signalled": signal, "firstLoaded.steam": reserve,
                        "energy/8/4/8/steam": reserve, "count/8/4/8/" + item: 1,
                        "count/8/4/8/minecraft:" + ("cobblestone" if crusher else "glass"): 0})
        else:
            before = [snapshot(at(1), "minecraft:stone")]
            after = deepcopy(before)
        row.update(before=before, after=after)
        if case_id in audit.IDS:
            columns = (3, 9, 15, 21) if case_id == audit.COMPARATOR else (8,)
            row["control.heldTiles"] = [dict(x=x, y=4, z=8, removed=True, sameTile=True, nbtUnchanged=True)
                                        for x in columns]
            row["control.heldAtMeasuredEnd"] = True
        catalog["cases"].append(dict(id=case_id, ticks=1))
        operation["cases"].append(row)
        saved["cases"].append(dict(id=case_id, index=index, positions=deepcopy(after), entities=[]))
    cold = deepcopy(saved)
    cold.update(held=True, constructionCallsThisProcess=0)
    return dict(catalog=catalog, operation=operation, fixture=saved, cold=cold)


def case(bundle, case_id, phase="operation"):
    return next(row for row in bundle[phase]["cases"] if row["id"] == case_id)


def change_tile(row, line, **changes):
    tag = audit.nbt(row, line)
    tag.update(changes)
    replacement = snapshot(tuple(row[key] for key in ("x", "y", "z")), row["block"], tag)
    row.update(replacement)


class SteamControlAuditTests(unittest.TestCase):
    def verify(self, bundle, status="FAIL"):
        result = audit.audit_documents(**bundle, line=LINE)
        self.assertEqual(status, result["status"], result)
        return result

    def test_full_native_schema_recomputes_operation_and_exact_cold(self):
        bundle = fixture(); base = audit.native(LINE)
        with patch.object(base, "validate_operation", wraps=base.validate_operation) as operation, \
                patch.object(base, "validate_restart", wraps=base.validate_restart) as cold:
            result = self.verify(bundle, "PASS")
            operation.assert_called_once(); cold.assert_called_once()
        self.assertEqual(audit.IDS, set(result["cases"]))
        self.assertEqual(len(bundle["operation"]["cases"]), result["cold"]["cases"])

    def test_each_control_case_is_required_even_if_all_documents_omit_it(self):
        for omitted in audit.IDS:
            bundle = fixture()
            for document in bundle.values():
                document["cases"] = [r for r in document["cases"] if r["id"] != omitted]
            with self.subTest(omitted=omitted):
                self.verify(bundle)

    def test_six_passing_control_cases_cannot_replace_the_full_base_group(self):
        bundle = fixture()
        for document in bundle.values():
            document["cases"] = [r for r in document["cases"] if r["id"] in audit.IDS]
        self.verify(bundle)

    def test_each_raw_measurement_is_required_not_just_pass_labels(self):
        original = fixture()
        for row in original["operation"]["cases"]:
            if row["id"] not in audit.IDS:
                continue
            for key in row:
                if not key.startswith(("firstLoaded.", "comparator.", "energy/", "count/")):
                    continue
                bundle = deepcopy(original); case(bundle, row["id"]).pop(key)
                with self.subTest(case=row["id"], key=key):
                    self.verify(bundle)

    def test_every_raw_scalar_mutation_is_rejected(self):
        original = fixture()
        for row in original["operation"]["cases"]:
            if row["id"] not in audit.IDS:
                continue
            for key, value in row.items():
                if not key.startswith(("firstLoaded.", "energy/", "count/")):
                    continue
                bundle = deepcopy(original)
                case(bundle, row["id"])[key] = not value if isinstance(value, bool) else value + 1
                with self.subTest(case=row["id"], key=key):
                    self.verify(bundle)

    def test_comparator_arrays_reject_mutation_boolean_and_wrong_length(self):
        for field in ("comparator.inputs", "comparator.actual"):
            for value in ([0, 1, 7, 15], [0, True, 8, 15], [0, 1, 8]):
                bundle = fixture(); case(bundle, audit.COMPARATOR)[field] = value
                with self.subTest(field=field, value=value):
                    self.verify(bundle)

    def test_wrong_saved_crusher_progress_is_not_rescued_by_correct_scalar(self):
        for case_id in audit.CRUSHERS:
            bundle = fixture()
            for row in (case(bundle, case_id)["after"][0],
                        case(bundle, case_id, "fixture")["positions"][0],
                        case(bundle, case_id, "cold")["positions"][0]):
                change_tile(row, LINE, progress=nbtlib.Short(125))
            with self.subTest(case=case_id):
                self.verify(bundle)

    def test_wrong_saved_energy_is_not_rescued_by_matching_metrics(self):
        for case_id in set(audit.CRUSHERS) | set(audit.FURNACES):
            bundle = fixture(); row = case(bundle, case_id)
            row["firstLoaded.steam"] = row["energy/8/4/8/steam"] = 12.0
            for record in (row["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                           case(bundle, case_id, "cold")["positions"][0]):
                change_tile(record, LINE, Energy=nbtlib.IntArray([audit.bits(12)]))
            with self.subTest(case=case_id):
                self.verify(bundle)

    def test_furnace_wrong_work_heat_or_burn_time_rejected_when_cold_matches(self):
        for case_id in audit.FURNACES:
            for field in range(6):
                bundle = fixture(); row = case(bundle, case_id)
                for record in (row["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                               case(bundle, case_id, "cold")["positions"][0]):
                    tag = audit.nbt(record, LINE)
                    values = [int(n) for n in tag["Data"]]; values[field] += 1
                    change_tile(record, LINE, Data=nbtlib.IntArray(values))
                with self.subTest(case=case_id, field=field):
                    self.verify(bundle)

    def test_typed_progress_and_energy_cannot_be_substituted_by_equal_numbers(self):
        for key, value in (("progress", nbtlib.Int(107)),
                           ("Energy", nbtlib.List[nbtlib.Int]([audit.bits(38.4375)]))):
            bundle = fixture(); case_id = audit.PREFIX + "crusher-first-loaded-unpowered"
            for record in (case(bundle, case_id)["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                           case(bundle, case_id, "cold")["positions"][0]):
                change_tile(record, LINE, **{key: value})
            with self.subTest(field=key):
                self.verify(bundle)

    def test_inventory_quantity_identity_slot_or_extra_output_rejected(self):
        case_id = audit.PREFIX + "crusher-first-loaded-unpowered"
        for changed in ("quantity", "identity", "slot", "extra-output", "count-type"):
            bundle = fixture()
            for record in (case(bundle, case_id)["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                           case(bundle, case_id, "cold")["positions"][0]):
                tag = audit.nbt(record, LINE); stack = tag["Items"][0]
                if changed == "quantity": stack["Count"] = nbtlib.Byte(2)
                elif changed == "identity": stack["id"] = nbtlib.String("minecraft:dirt")
                elif changed == "slot": stack["Slot"] = nbtlib.Byte(1)
                elif changed == "count-type": stack["Count"] = nbtlib.Int(1)
                else: tag["Items"].append(nbtlib.Compound(Slot=nbtlib.Byte(1), Count=nbtlib.Byte(1),
                                                      id=nbtlib.String("minecraft:cobblestone")))
                change_tile(record, LINE, Items=tag["Items"])
            with self.subTest(mutation=changed):
                self.verify(bundle)

    def test_comparator_inventory_must_match_the_measured_stock(self):
        bundle = fixture()
        for record in (case(bundle, audit.COMPARATOR)["after"][2],
                       case(bundle, audit.COMPARATOR, "fixture")["positions"][2],
                       case(bundle, audit.COMPARATOR, "cold")["positions"][2]):
            tag = audit.nbt(record, LINE); tag["Items"][0]["Count"] = nbtlib.Byte(31)
            change_tile(record, LINE, Items=tag["Items"])
        self.verify(bundle)

    def test_first_tick_is_not_a_later_tick_or_boolean_counter(self):
        case_id = next(iter(audit.CRUSHERS))
        for field, value in (("finishTick", 12), ("requiredTicks", True), ("startTick", True)):
            bundle = fixture(); case(bundle, case_id)[field] = value
            with self.subTest(field=field):
                self.verify(bundle)

    def test_inconsistent_after_fixture_is_rejected_even_when_cold_agrees(self):
        bundle = fixture(); case_id = next(iter(audit.CRUSHERS))
        for phase in ("fixture", "cold"):
            case(bundle, case_id, phase)["positions"][0]["state"] += "[changed]"
        self.verify(bundle)

    def test_cold_is_recomputed_for_unrelated_cases_not_only_the_six(self):
        bundle = fixture()
        other = next(row for row in bundle["cold"]["cases"] if row["id"] not in audit.IDS)
        other["positions"][0]["block"] = "minecraft:air"
        result = self.verify(bundle)
        self.assertEqual("FAIL", result["cold"]["status"])

    def test_missing_unrelated_cold_case_cannot_shrink_the_restart_oracle(self):
        bundle = fixture(); omitted = next(r["id"] for r in bundle["fixture"]["cases"] if r["id"] not in audit.IDS)
        for phase in ("fixture", "cold"):
            bundle[phase]["cases"] = [r for r in bundle[phase]["cases"] if r["id"] != omitted]
        self.verify(bundle)

    def test_failed_unrelated_operation_is_not_rescued_by_root_pass(self):
        bundle = fixture()
        next(r for r in bundle["operation"]["cases"] if r["id"] not in audit.IDS)["verifiedAssertions"] = 0
        result = self.verify(bundle)
        self.assertEqual("FAIL", result["operation"]["status"])

    def test_redstone_control_requires_the_actual_adjacent_block(self):
        bundle = fixture(); case_id = audit.PREFIX + "crusher-first-loaded-signalled"
        for collection in (case(bundle, case_id)["before"], case(bundle, case_id)["after"],
                           case(bundle, case_id, "fixture")["positions"], case(bundle, case_id, "cold")["positions"]):
            collection[1].update(block="minecraft:air", state="minecraft:air")
        self.verify(bundle)

    def test_duplicate_cases_and_positions_fail_closed(self):
        for mutation in ("case", "position"):
            bundle = fixture()
            if mutation == "case": bundle["operation"]["cases"].append(deepcopy(bundle["operation"]["cases"][0]))
            else:
                row = case(bundle, audit.COMPARATOR); row["after"].append(deepcopy(row["after"][0]))
            with self.subTest(mutation=mutation):
                self.verify(bundle)

    def test_nonfinite_or_boolean_raw_number_is_not_valid_measurement(self):
        for value in (float("nan"), float("inf"), True):
            bundle = fixture(); case(bundle, next(iter(audit.CRUSHERS)))["firstLoaded.steam"] = value
            with self.subTest(value=value):
                self.verify(bundle)

    def test_offline_cli_reads_raw_reports_and_never_trusts_a_summary(self):
        bundle = fixture()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, relative in audit.INPUTS.items():
                path = root / relative; path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(bundle[name]), encoding="utf-8")
            summary = root / ("native" + LINE + "-machine-operation-results.json")
            summary.write_text('{"status":"FAIL"}', encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()) as stream:
                self.assertEqual(0, audit.main(["--runtime", folder, "--line", LINE]))
            result = json.loads(stream.getvalue())
            self.assertEqual("PASS", result["status"]); self.assertEqual(4, len(result["inputSha256"]))
            self.assertEqual('{"status":"FAIL"}', summary.read_text(encoding="utf-8"))
            (root / audit.INPUTS["cold"]).unlink()
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, audit.main(["--runtime", folder, "--line", LINE]))

    def test_binary_nbt_cannot_be_overruled_by_correct_looking_text(self):
        bundle = fixture(); case_id = audit.PREFIX + "crusher-first-loaded-unpowered"
        for record in (case(bundle, case_id)["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                       case(bundle, case_id, "cold")["positions"][0]):
            tag = audit.nbt(record, LINE); tag["progress"] = nbtlib.Short(125)
            stream = io.BytesIO(); nbtlib.File(tag).write(stream)
            record["nbtBase64"] = base64.b64encode(gzip.compress(stream.getvalue())).decode("ascii")
        self.verify(bundle)

    def test_missing_saved_tile_nbt_is_not_replaced_by_scalar_measurements(self):
        bundle = fixture(); case_id = audit.PREFIX + "crusher-first-loaded-unpowered"
        for record in (case(bundle, case_id)["after"][0], case(bundle, case_id, "fixture")["positions"][0],
                       case(bundle, case_id, "cold")["positions"][0]):
            record.pop("nbt", None); record.pop("nbtBase64", None)
        self.verify(bundle)

    def test_cold_must_be_held_and_have_no_reconstruction(self):
        for field, value in (("held", False), ("constructionCallsThisProcess", 1)):
            bundle = fixture(); bundle["cold"][field] = value
            with self.subTest(field=field):
                self.verify(bundle)

    def test_every_control_requires_both_explicit_hold_measurements(self):
        for case_id in audit.IDS:
            for field in ("control.heldTiles", "control.heldAtMeasuredEnd"):
                bundle = fixture(); case(bundle, case_id).pop(field)
                with self.subTest(case=case_id, missing=field):
                    self.verify(bundle)

    def test_hold_at_end_is_a_true_boolean_not_a_truthy_label(self):
        for case_id in audit.IDS:
            for value in (False, 1, "true", None):
                bundle = fixture(); case(bundle, case_id)["control.heldAtMeasuredEnd"] = value
                with self.subTest(case=case_id, value=value):
                    self.verify(bundle)

    def test_each_hold_requires_removal_same_tile_and_unchanged_nbt(self):
        for case_id in audit.IDS:
            for field in ("removed", "sameTile", "nbtUnchanged"):
                for value in ("missing", False, 1, "true", None):
                    bundle = fixture(); held = case(bundle, case_id)["control.heldTiles"][0]
                    if value == "missing": held.pop(field)
                    else: held[field] = value
                    with self.subTest(case=case_id, field=field, value=value):
                        self.verify(bundle)

    def test_hold_records_are_complete_unique_and_not_extra_tiles(self):
        for case_id in audit.IDS:
            for mutation in ("empty", "missing", "duplicate", "extra", "not-list"):
                bundle = fixture(); row = case(bundle, case_id); held = row["control.heldTiles"]
                if mutation == "empty": row["control.heldTiles"] = []
                elif mutation == "missing": held.pop()
                elif mutation == "duplicate":
                    if len(held) > 1: held[-1] = deepcopy(held[0])
                    else: held.append(deepcopy(held[0]))
                elif mutation == "extra":
                    held.append(dict(x=2, y=4, z=8, removed=True, sameTile=True, nbtUnchanged=True))
                else: row["control.heldTiles"] = {"allHeld": True}
                with self.subTest(case=case_id, mutation=mutation):
                    self.verify(bundle)

    def test_hold_coordinates_must_name_the_actual_measured_control_tiles(self):
        for case_id in audit.IDS:
            for field in ("x", "y", "z"):
                for mutation in ("different", "boolean", "missing"):
                    bundle = fixture(); held = case(bundle, case_id)["control.heldTiles"][0]
                    if mutation == "different": held[field] += 1
                    elif mutation == "boolean": held[field] = True
                    else: held.pop(field)
                    with self.subTest(case=case_id, field=field, mutation=mutation):
                        self.verify(bundle)

    def test_hold_flags_never_rescue_after_fixture_or_cold_nbt_drift(self):
        for stage in ("fixture", "cold"):
            bundle = fixture(); case_id = audit.PREFIX + "crusher-first-loaded-unpowered"
            record = case(bundle, case_id, stage)["positions"][0]
            change_tile(record, LINE, progress=nbtlib.Short(0))
            with self.subTest(stage=stage):
                self.verify(bundle)

    def test_json_duplicate_keys_are_not_silently_overwritten(self):
        with self.assertRaises(AssertionError):
            json.loads('{"status":"FAIL","status":"PASS"}', object_pairs_hook=audit.unique_object)


if __name__ == "__main__":
    unittest.main()
