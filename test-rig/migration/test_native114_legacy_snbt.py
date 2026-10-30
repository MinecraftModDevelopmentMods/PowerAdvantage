import unittest
import nbtlib
from native114_legacy_snbt import parse_legacy_snbt


class LegacyDiagnosticSnbtTests(unittest.TestCase):
    def test_summary_uses_actual_nonzero_binary_bytes(self):
        binary = nbtlib.parse_nbt('{growth:[B;4b,-2b,7b],prog:[I;0,1,2]}')
        parsed = parse_legacy_snbt('{growth:[3 bytes],prog:[0,1,2,]}', binary)
        self.assertEqual([4, -2, 7], parsed["growth"].unpack().tolist())
        self.assertEqual([0, 1, 2], parsed["prog"].unpack())

    def test_summary_without_binary_or_wrong_shape_fails(self):
        for peer in (None, nbtlib.parse_nbt('{growth:[B;1b,2b]}'), nbtlib.parse_nbt('{growth:[I;1,2,3]}')):
            with self.assertRaises(ValueError): parse_legacy_snbt('{growth:[3 bytes]}', peer)

    def test_nested_summary_and_quoted_text_are_distinct(self):
        binary = nbtlib.parse_nbt('{Items:[{id:"minecraft:dirt",Count:3b,tag:{bytes:[B;8b],Name:"[3 bytes]"}}]}')
        parsed = parse_legacy_snbt('{Items:[0:{id:"minecraft:dirt",Count:3b,tag:{bytes:[1 bytes],Name:"[3 bytes]"}}]}', binary)
        self.assertEqual(8, int(parsed["Items"][0]["tag"]["bytes"][0]))
        self.assertEqual("[3 bytes]", str(parsed["Items"][0]["tag"]["Name"]))

    def test_plain_and_indexed_lists_remain_supported(self):
        self.assertEqual([0, 1], parse_legacy_snbt('[0,1,]').unpack())
        self.assertEqual(["a", "b"], parse_legacy_snbt('[0:"a",1:"b"]').unpack())

    def test_malformed_legacy_forms_fail(self):
        for text in ('[1:"a"]', '[0:"a",0:"b"]', '[0:1,2]', '[3 bytes,1]', '[3 bytes] extra'):
            with self.assertRaises(ValueError): parse_legacy_snbt(text)


if __name__ == "__main__": unittest.main()
