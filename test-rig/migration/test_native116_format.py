import copy
import unittest

from native116_format import actor_nbt, decode_palette, dimension_id, uuid_longs, world_info, worldgen_settings


class Native116FormatTest(unittest.TestCase):
    def test_native_dimensions_are_exact_and_unknown_keys_fail(self):
        for identity, key in ((-1, "minecraft:the_nether"), (0, "minecraft:overworld"), (1, "minecraft:the_end")):
            self.assertEqual(identity, dimension_id(key))
        for value in (True, "0", "minecraft:nether", "custom:moon", 2):
            with self.assertRaises(ValueError):
                dimension_id(value)

    def test_uuid_words_retain_every_signed_bit(self):
        self.assertEqual((-1, -(1 << 63)), uuid_longs([-1, -1, -(1 << 31), 0]))
        for value in ([1, 2, 3], [1, 2, 3, 1 << 31], [True, 0, 0, 0]):
            with self.assertRaises(ValueError):
                uuid_longs(value)

    def test_actor_adapter_retains_raw_input_and_inventory(self):
        value = {"UUID": [0, 1, 0, 2], "Dimension": "minecraft:the_end", "Inventory": [{"id": "poweradvantage:test", "Count": 7}]}
        original = copy.deepcopy(value)
        actual = actor_nbt(value)
        self.assertEqual(original, value)
        self.assertEqual((1, 2, 1), (actual["UUIDMost"], actual["UUIDLeast"], actual["Dimension"]))
        self.assertEqual(value["Inventory"], actual["Inventory"])

    def settings(self):
        return {"WorldGenSettings": {"seed": 5919679243107101036, "generate_features": False, "bonus_chest": False,
                "dimensions": {key: {"type": key, "generator": {"type": "minecraft:flat", "settings": {"layers": []}}}
                               for key in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")}}}

    def test_worldgen_reads_native_fields_not_stale_legacy_fields(self):
        data = self.settings()
        data.update(MapFeatures=1, RandomSeed=0, generatorName="wrong")
        self.assertEqual({"MapFeatures": 0, "RandomSeed": 5919679243107101036, "generatorName": "flat"}, world_info(data))
        self.assertEqual(data["WorldGenSettings"], worldgen_settings(data))

    def test_legacy_fields_are_not_native_proof(self):
        data = dict(MapFeatures=0, RandomSeed=4, generatorName="flat")
        with self.assertRaises(ValueError):
            world_info(data)
        self.assertEqual(data, world_info(data, allow_legacy=True))

    def test_missing_native_dimension_fails(self):
        data = self.settings()
        del data["WorldGenSettings"]["dimensions"]["minecraft:the_end"]
        with self.assertRaises(ValueError):
            world_info(data)

    def test_padded_palette_boundaries_at_five_seven_and_thirteen_bits(self):
        for size, count in ((17, 4096), (65, 4096), (4097, 8192)):
            palette = list(range(size))
            bits = max(4, (size - 1).bit_length())
            per_long = 64 // bits
            values = [index % size for index in range(count)]
            words = [0] * ((count + per_long - 1) // per_long)
            for index, value in enumerate(values):
                words[index // per_long] |= value << ((index % per_long) * bits)
            self.assertEqual(values, decode_palette(palette, words, count))

    def test_old_cross_long_packing_is_rejected(self):
        with self.assertRaises(ValueError):
            decode_palette(list(range(17)), [0] * 320)

    def test_missing_palette_index_is_not_clamped(self):
        with self.assertRaises(ValueError):
            decode_palette(["air", "stone"], [15] * 256)


if __name__ == "__main__":
    unittest.main()
