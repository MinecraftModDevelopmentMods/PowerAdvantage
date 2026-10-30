"""Mutation controls use the retained migration/connected row and NBT schemas."""

import base64
import copy
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest

import nbtlib

from native113_resource_audit import BATTERIES, SLOT_LIMITS, canonical_resources, compare_resources, parse_legacy_snbt


def encode(row, snbt):
    stream = io.BytesIO()
    nbtlib.File(nbtlib.parse_nbt(snbt)).write(stream)
    row["nbt"] = snbt
    row["nbtBase64"] = base64.b64encode(gzip.compress(stream.getvalue())).decode("ascii")
    return row


def witness(block="minecraft:chest", snbt="{Items:[]}", x=143):
    return encode(dict(id="fixture-" + str(x), station="C-ELECTRIC", kind="battery",
                       name=block, actualName=block, x=x, y=80, z=47,
                       properties={}, witness="fixture-" + str(x), objectIdentity=123), snbt)


def bits(amount):
    return struct.unpack(">i", struct.pack(">f", amount))[0]


class LegacySnbtGrammarTests(unittest.TestCase):
    def test_nested_legacy_lists_preserve_quoted_text(self):
        value = parse_legacy_snbt('{Items:[0:{id:"minecraft:dirt",Count:2b,tag:{Name:"[0:not an index]",Lore:[0:"first",1:"second"]}}]}')
        self.assertEqual(2, int(value["Items"][0]["Count"]))
        self.assertEqual("[0:not an index]", str(value["Items"][0]["tag"]["Name"]))
        self.assertEqual(["first", "second"], value["Items"][0]["tag"]["Lore"].unpack())

    def test_modern_number_lists_still_parse(self):
        self.assertEqual([0, 1, 2], parse_legacy_snbt('[0,1,2]').unpack())

    def test_legacy_int_array_trailing_comma_is_preserved(self):
        self.assertEqual([1168297984], parse_legacy_snbt('{Energy:[1168297984,]}')["Energy"].unpack())

    def test_mixed_skipped_duplicate_and_trailing_indices_fail(self):
        for text in ('[0:1,2]', '[1:1]', '[0:1,0:2]', '[0:1] extra'):
            with self.assertRaises(ValueError): parse_legacy_snbt(text)


def sample():
    return [
        witness("electricadvantage:electric_battery_array",
                '{Energy:[I;1174011904],Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:1b,tag:{energy:8000.0f}}]}'),
        witness("steamadvantage:steam_boiler_coal",
                '{Energy:[I;1088421888,0],BurnTime:802,BurnTimeTotal:1600,Items:[{Slot:0b,id:"minecraft:coal",Count:31b}],Tank:{FluidName:"minecraft:water",Amount:3992}}', 65),
        witness("electricadvantage:steam_powered_generator",
                '{Energy:[I;1147797504,0],Items:[],steam:29.452f}', 141),
    ]


class ResourceAuditTest(unittest.TestCase):
    def check(self, before, after, status="FAIL", **kwargs):
        result = compare_resources(before, after, **kwargs)
        self.assertEqual(status, result["status"], result)
        return result

    def test_actual_row_schemas_accept_arrays_and_connected_wrappers(self):
        before = sample()
        wrapped = dict(held=True, requestedTicks=0, advancedTicks=0,
                       gameTime=807, buildCallsThisProcess=0, snapshot=copy.deepcopy(before))
        self.check(before, wrapped, "PASS")
        self.assertEqual(3, compare_resources(wrapped, before)["positions"])

    def test_read_only_path_api_does_not_change_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "migration-converted.json", Path(directory) / "connected-loaded.json"
            a.write_text(json.dumps(sample()), encoding="utf-8")
            b.write_text(json.dumps(dict(snapshot=sample(), held=True)), encoding="utf-8")
            hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in (a, b)]
            self.check(a, b, "PASS")
            self.assertEqual(hashes, [hashlib.sha256(path.read_bytes()).hexdigest() for path in (a, b)])

    def test_inputs_and_override_maps_are_not_mutated(self):
        before = sample()
        after = copy.deepcopy(before)
        backup = copy.deepcopy(before)
        limits = {"electricadvantage:electric_battery_array": 8}
        self.check(before, after, "PASS", slot_limits=limits)
        self.assertEqual(before, backup)
        self.assertEqual(after, backup)
        self.assertEqual(limits, {"electricadvantage:electric_battery_array": 8})

    def test_root_nonresource_native_metadata_is_not_blanket_nbt_equality(self):
        before = [witness(snbt='{Items:[],Lock:""}')]
        after = [witness(snbt='{Items:[],CustomName:"native",DataVersion:2586,ForgeData:{AdvConnectedWitness:"same",cache:42},initDone:1b,Facing:2b,progress:7s,BurnTimeTotal:1600}')]
        after[0].update(objectIdentity=999, properties={"facing": "north"})
        self.check(before, after, "PASS")

    def test_target_only_energy_and_extra_positive_channel_fail(self):
        before = [witness("electricadvantage:electric_furnace", "{Items:[]}")]
        self.check(before, [witness("electricadvantage:electric_furnace", "{Items:[],Energy:[I;%d]}" % bits(100))])
        self.check([witness(snbt="{Energy:[I;%d,0]}" % bits(1))],
                   [witness(snbt="{Energy:[I;%d,%d]}" % (bits(1), bits(2)))])

    def test_optional_zero_energy_arrays_and_scalars_are_inert(self):
        self.check([witness(snbt="{}")],
                   [witness(snbt="{Items:[],Energy:[I;0,0,0],Electricity:0.0f,BurnTime:0,steam:0.0f}")], "PASS")
        self.check([witness(snbt="{Energy:[I;%d]}" % bits(1))],
                   [witness(snbt="{Energy:[I;%d,0,0]}" % bits(1))], "PASS")

    def test_each_known_scalar_reserve_detects_target_only_additions(self):
        for field in ("Electricity", "energy", "steam", "soil", "Temperature", "BurnTime", "burnTime", "Fuel", "fuel"):
            with self.subTest(field=field):
                self.check([witness(snbt="{}")], [witness(snbt="{%s:1}" % field)])

    def test_target_only_positive_named_tanks_fail(self):
        for field in ("Tank", "TankIn", "TankOut", "InputTank", "OutputTank", "WaterTank", "FuelTank", "AuxTank"):
            with self.subTest(field=field):
                self.check([witness(snbt="{}")],
                           [witness(snbt='{%s:{FluidName:"minecraft:water",Amount:1}}' % field)])

    def test_exact_zero_named_fluid_and_native_empty_representations_match(self):
        for field in ("Tank", "TankIn", "TankOut", "InputTank"):
            with self.subTest(field=field):
                self.check([witness(snbt='{%s:{FluidName:"poweradvantage:crude_oil",Amount:0}}' % field)],
                           [witness(snbt='{%s:{Empty:1b}}' % field)], "PASS")
                self.check([witness(snbt="{}")], [witness(snbt='{%s:{Empty:0b}}' % field)], "PASS")

    def test_positive_fluid_cannot_be_normalized_to_empty(self):
        self.check([witness(snbt='{Tank:{FluidName:"water",Amount:1}}')],
                   [witness(snbt='{Tank:{Empty:1b}}')])

    def test_fluid_identity_amount_and_payload_are_exact(self):
        before = [witness(snbt='{Tank:{FluidName:"water",Amount:25,Tag:{grade:1}}}')]
        for tank in ('{FluidName:"lava",Amount:25,Tag:{grade:1}}',
                     '{FluidName:"water",Amount:24,Tag:{grade:1}}',
                     '{FluidName:"water",Amount:25,Tag:{grade:2}}'):
            with self.subTest(tank=tank): self.check(before, [witness(snbt="{Tank:" + tank + "}")])

    def test_known_fluid_namespace_normalizations_are_retained(self):
        for old, new in (("water", "minecraft:water"), ("crude_oil", "poweradvantage:crude_oil")):
            self.check([witness(snbt='{Tank:{FluidName:"%s",Amount:25}}' % old)],
                       [witness(snbt='{Tank:{FluidName:"%s",Amount:25}}' % new)], "PASS")

    def test_target_only_inventory_and_quantity_or_slot_mutations_fail(self):
        empty = [witness(snbt="{}")]
        one = [witness(snbt='{Items:[{Slot:0b,id:"minecraft:coal",Count:1b}]}')]
        self.check(empty, one)
        for slot, item, count in ((0, "minecraft:coal", 2), (1, "minecraft:coal", 1), (0, "minecraft:diamond", 1)):
            self.check(one, [witness(snbt='{Items:[{Slot:%db,id:"%s",Count:%db}]}' % (slot, item, count))])

    def test_inventory_and_witness_order_do_not_change_per_position_budgets(self):
        a = witness(snbt='{Items:[{Slot:0b,id:"minecraft:coal",Count:1b},{Slot:3b,id:"minecraft:stone",Count:2b}]}')
        b = witness(snbt='{Items:[{Slot:3b,id:"minecraft:stone",Count:2b},{Slot:0b,id:"minecraft:coal",Count:1b}]}')
        other = witness(x=144)
        self.check([a, other], [other, b], "PASS")

    def test_equal_global_total_cannot_hide_energy_moved_between_positions(self):
        before = [witness(snbt="{Energy:[I;%d]}" % bits(5), x=1), witness(snbt="{Energy:[I;%d]}" % bits(7), x=2)]
        after = [witness(snbt="{Energy:[I;%d]}" % bits(7), x=1), witness(snbt="{Energy:[I;%d]}" % bits(5), x=2)]
        result = self.check(before, after)
        self.assertEqual(2, len(result["differences"]))

    def test_missing_new_duplicate_or_moved_witnesses_fail(self):
        before = [witness(), witness(x=144)]
        for after in (before[:1], before + [witness(x=145)], before + [copy.deepcopy(before[0])], [witness(), witness(x=145)]):
            self.check(before, after)

    def test_changed_block_identity_cannot_hide_behind_empty_resources(self):
        self.check([witness()], [witness("minecraft:hopper")])

    def test_missing_known_container_nbt_is_not_zero_stock_evidence(self):
        row = witness()
        row.pop("nbt"); row.pop("nbtBase64")
        self.check([row], [row])

    def test_duplicate_negative_and_out_of_range_slots_fail(self):
        for items in ('[{Slot:0b,id:"minecraft:coal",Count:1b},{Slot:0b,id:"minecraft:coal",Count:1b}]',
                      '[{Slot:-1b,id:"minecraft:coal",Count:1b}]',
                      '[{Slot:27b,id:"minecraft:coal",Count:1b}]',
                      '[{id:"minecraft:coal",Count:1b}]'):
            row = witness(snbt="{Items:" + items + "}")
            self.check([row], [row])

    def test_constructor_proven_furnace_and_other_slot_limits(self):
        for block, limit in (("electricadvantage:electric_furnace", 10),
                             ("electricadvantage:electric_crusher", 9),
                             ("electricadvantage:electric_drill", 6),
                             ("steamadvantage:steam_furnace", 7),
                             ("steamadvantage:steam_drill", 5)):
            self.assertEqual(limit, SLOT_LIMITS[block])
            valid = [witness(block, '{Items:[{Slot:%db,id:"minecraft:coal",Count:1b}]}' % (limit - 1))]
            invalid = [witness(block, '{Items:[{Slot:%db,id:"minecraft:coal",Count:1b}]}' % limit)]
            self.check(valid, valid, "PASS")
            self.check(invalid, invalid)

    def test_caller_can_add_unknown_registry_container_limit(self):
        rows = [witness("example:container", '{Items:[{Slot:2b,id:"minecraft:coal",Count:1b}]}')]
        self.check(rows, rows, "PASS")
        self.check(rows, rows, slot_limits={"example:container": 2})

    def test_invalid_stack_quantities_fail_even_when_both_sides_match(self):
        for count in (-1, 0, 65):
            rows = [witness(snbt='{Items:[{Slot:0b,id:"minecraft:coal",Count:%d}]}' % count)]
            self.check(rows, rows)

    def test_known_legacy_damage_bucket_and_empty_tag_representations_match(self):
        old = [witness(snbt='{id:"Chest",Items:[{Slot:0b,id:"forge:bucketfilled",Count:1b,Damage:0s,tag:{FluidName:"crude_oil",Amount:1000}}]}')]
        new = [witness(snbt='{id:"minecraft:chest",Items:[{Slot:0b,id:"poweradvantage:crude_oil_bucket",Count:1b,tag:{}}]}')]
        self.check(old, new, "PASS")
        damaged = [witness(snbt='{Items:[{Slot:0b,id:"steamadvantage:musket",Count:1b,Damage:5s}]}')]
        modern = [witness(snbt='{Items:[{Slot:0b,id:"steamadvantage:musket",Count:1b,tag:{Damage:5}}]}')]
        self.check(damaged, modern, "PASS")

    def test_enchantments_use_explicit_world_registry_maps(self):
        old = [witness(snbt='{Items:[{Slot:0b,id:"steamadvantage:musket",Count:1b,tag:{ench:[{id:11s,lvl:2s}]}}]}')]
        new = [witness(snbt='{Items:[{Slot:0b,id:"steamadvantage:musket",Count:1b,tag:{Enchantments:[{id:"steamadvantage:spreadshot",lvl:2s}]}}]}')]
        self.check(old, new, "PASS", before_enchantments={11: "steamadvantage:spreadshot"})
        self.check(old, new, before_enchantments={11: "minecraft:binding_curse"})

    def test_battery_defaults_match_native_full_charge_but_charge_creation_fails(self):
        for item, capacity in BATTERIES.items():
            old = [witness(snbt='{Items:[{Slot:0b,id:"%s",Count:1b}]}' % item)]
            full = [witness(snbt='{Items:[{Slot:0b,id:"%s",Count:1b,tag:{energy:%df}}]}' % (item, capacity))]
            self.check(old, full, "PASS")
            zero = [witness(snbt='{Items:[{Slot:0b,id:"%s",Count:1b,tag:{energy:0.0f}}]}' % item)]
            self.check(zero, full)
            invalid = [witness(snbt='{Items:[{Slot:0b,id:"%s",Count:1b,tag:{energy:%df}}]}' % (item, capacity + 1))]
            self.check(invalid, invalid)

    def test_legacy_battery_stack_counts_are_preserved_not_repaired(self):
        original = [witness(snbt='{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:3b,tag:{energy:123.0f}}]}')]
        self.check(original, copy.deepcopy(original), "PASS")
        for count, charge in ((1,123), (4,123), (3,124)):
            changed = [witness(snbt='{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:%db,tag:{energy:%df}}]}' % (count, charge))]
            self.check(original, changed)

    def test_lossy_legacy_byte_summaries_require_matching_binary_and_keep_real_values(self):
        row = witness(snbt='{ForgeCaps:{custom:{Items:[],Fuel:2}},growth:[B;4b,-2b,7b]}')
        row["nbt"] = '{ForgeCaps:{custom:{Items:[],Fuel:2}},growth:[3 bytes]}'
        self.check([row], [copy.deepcopy(row)], "PASS")
        for text in ('{ForgeCaps:{custom:{Items:[],Fuel:2}},growth:[2 bytes]}',
                     '{ForgeCaps:{custom:{Items:[],Fuel:3}},growth:[3 bytes]}'):
            bad = copy.deepcopy(row); bad["nbt"] = text
            self.check([bad], [bad])
        missing = copy.deepcopy(row); missing.pop("nbtBase64")
        self.check([missing], [missing])

    def test_negative_and_nonfinite_resource_encodings_fail_closed(self):
        for snbt in ("{Energy:[I;%d]}" % bits(-1), "{Energy:[I;%d]}" % bits(float("nan")),
                     "{Energy:[I;%d]}" % bits(float("inf")), "{steam:-1.0f}",
                     '{Tank:{FluidName:"water",Amount:-1}}', '{Tank:{}}',
                     '{Tank:{Empty:1b,FluidName:"water",Amount:1}}'):
            rows = [witness(snbt=snbt)]
            self.check(rows, rows)

    def test_resource_fields_inside_capability_compounds_are_not_ignored(self):
        self.check([witness(snbt="{}")], [witness(snbt="{ForgeCaps:{example:{Energy:[I;%d]}}}" % bits(5))])

    def test_conflicting_base64_and_text_payloads_are_not_silently_selected(self):
        row = witness(snbt="{Energy:[I;%d]}" % bits(5))
        row["nbt"] = "{Energy:[I;0]}"
        result = self.check([row], [row])
        self.assertTrue(result["failures"])

    def test_snbt_float_rounding_uses_declared_binary_type_not_tolerance(self):
        row = witness(snbt="{steam:29.452f}")
        self.check([row], [row], "PASS")
        text_only = copy.deepcopy(row)
        text_only.pop("nbtBase64")
        self.check([row], [text_only], "PASS")
        changed = witness(snbt="{steam:29.453f}")
        self.check([row], [changed])
        double = witness(snbt="{steam:29.452d}")
        self.check([row], [double])

    def test_empty_or_unsupported_documents_fail_instead_of_vacuous_pass(self):
        for document in ([], {}, {"snapshot": []}, {"cases": []}):
            self.check(document, document)

    def test_snbt_only_operation_position_rows_can_be_compared_after_explicit_extraction(self):
        row = witness(snbt='{TankIn:{FluidName:"crude_oil",Amount:0},TankOut:{FluidName:"refined_oil",Amount:20}}')
        row["block"] = row.pop("actualName")
        row.pop("nbtBase64")
        other = copy.deepcopy(row)
        other["nbt"] = '{TankIn:{Empty:1b},TankOut:{FluidName:"refined_oil",Amount:20}}'
        self.check([row], [other], "PASS")


if __name__ == "__main__":
    unittest.main()
