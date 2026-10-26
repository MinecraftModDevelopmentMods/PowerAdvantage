import unittest
import json

import transformer_order as historical
import test_transformer_order as historical_tests
from native114_transformer_order import COREMODS, SUFFIXES, native_order_evidence


class NativeTransformerOrderAdapterTest(unittest.TestCase):
    def test_oracle_code_is_unchanged_with_only_platform_targets_bound(self):
        self.assertIs(historical.order_evidence.__code__, native_order_evidence.__code__)
        self.assertEqual("net/minecraft/world/storage/SaveFormat", SUFFIXES["world_info"][0])
        self.assertEqual("net/minecraft/world/chunk/storage/ChunkLoader", SUFFIXES["chunk_status"][0])
        self.assertEqual(historical.SUFFIXES["worldgen_guard"], SUFFIXES["worldgen_guard"])
        self.assertNotEqual(historical.SUFFIXES, SUFFIXES)
        self.assertEqual("coremods-1.0.0.jar", COREMODS.name)


class NativeOrderEvidenceNegativeControls(historical_tests.OrderEvidenceTests):
    def setUp(self):
        super().setUp()
        mapping = {old[0]: SUFFIXES[suffix][0] for suffix, old in historical.SUFFIXES.items()}
        for event in self.events:
            if "class" in event:
                event["class"] = mapping[event["class"]]

    def prove(self):
        (self.root / "transformer-order-events.jsonl").write_text("\n".join(json.dumps(event) for event in self.events))
        return native_order_evidence(self.root, "bm-first")


if __name__ == "__main__":
    unittest.main()
