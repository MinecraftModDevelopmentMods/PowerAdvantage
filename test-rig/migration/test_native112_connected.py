import base64
import copy
import gzip
import io
import unittest
import nbtlib
from native112_connected import strict_snapshot


def document(tag='{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4b,id:"minecraft:iron_ore"}]}'):
    output = io.BytesIO()
    nbtlib.File(nbtlib.parse_nbt(tag)).write(output)
    return dict(snapshot=[dict(id="test", station="C-STEAM", kind="furnace", name="steamadvantage:steam_furnace",
        x=80, y=80, z=80, actualName="steamadvantage:steam_furnace", properties={"facing":"north"},
        nbtBase64=base64.b64encode(gzip.compress(output.getvalue())).decode("ascii"))])


class NativeLegacyConnectedOracleTest(unittest.TestCase):
    def test_equal_typed_native_binary_is_a_pass(self):
        self.assertEqual("PASS", strict_snapshot(document(), document())["status"])

    def test_extra_resource_changed_quantity_and_changed_nbt_type_fail(self):
        for bad in ('{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:3b,id:"minecraft:iron_ore"}]}',
                    '{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4,id:"minecraft:iron_ore"}]}',
                    '{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4b,id:"minecraft:iron_ore"}],HiddenFluid:1000}'):
            self.assertEqual("FAIL", strict_snapshot(document(), document(bad))["status"])

    def test_missing_extra_duplicate_or_moved_block_is_not_preservation(self):
        source = document()
        for kind in ("missing", "extra", "duplicate", "moved"):
            after = copy.deepcopy(source)
            if kind == "missing": after["snapshot"] = []
            elif kind in ("extra", "duplicate"):
                row = copy.deepcopy(after["snapshot"][0])
                if kind == "extra": row["id"] = "unknown"
                after["snapshot"].append(row)
            else: after["snapshot"][0]["x"] += 1
            if kind == "duplicate":
                with self.assertRaises(AssertionError): strict_snapshot(source, after)
            else: self.assertEqual("FAIL", strict_snapshot(source, after)["status"])

    def test_historical_tile_id_alias_is_not_a_state_change(self):
        old = document('{id:"minecraft:steamadvantage.steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4b,id:"minecraft:iron_ore"}]}')
        self.assertEqual("PASS", strict_snapshot(old, document())["status"])

    def test_only_empty_forge_metadata_is_equivalent_to_absence(self):
        empty = document('{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4b,id:"minecraft:iron_ore"}],ForgeData:{},ForgeCaps:{}}')
        self.assertEqual("PASS", strict_snapshot(document(), empty)["status"])
        custom = document('{id:"steamadvantage:steam_furnace",Energy:[I;1],Items:[{Slot:1b,Count:4b,id:"minecraft:iron_ore"}],ForgeData:{Hidden:1}}')
        self.assertEqual("FAIL", strict_snapshot(document(), custom)["status"])

    def test_legacy_missing_facing_comes_from_actual_state_not_a_default(self):
        def drill(tag):
            result = document(tag)
            result["snapshot"][0].update(actualName="steamadvantage:steam_drill", name="steamadvantage:steam_drill", kind="drill")
            return result
        old = drill('{id:"steamadvantage:steam_drill",Energy:[I;1],Items:[]}')
        for facing, expected in ((2, "PASS"), (3, "FAIL")):
            current = drill('{id:"steamadvantage:steam_drill",Energy:[I;1],Items:[],Facing:' + str(facing) + 'b}')
            self.assertEqual(expected, strict_snapshot(old, current)["status"])
