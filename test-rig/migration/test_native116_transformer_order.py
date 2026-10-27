import unittest
import json
from pathlib import Path
import tempfile
from unittest.mock import patch

import transformer_order as historical
import test_transformer_order as historical_tests
import native116_audit_order as audit
from native116_transformer_order import BRIDGE_LIBRARIES, COREMODS, SUFFIXES, native_order_evidence


class NativeTransformerOrderAdapterTest(unittest.TestCase):
    def test_audit_uses_native_actor_decoder_before_strict_replay(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, value in {
                "results.json": {"original110/natural": {}},
                "source-fingerprints.json": {},
                "probe-build.json": {"patched_sha256": "hash"},
                "summary.json": {"patchedLibrarySha256": "hash"},
            }.items():
                (root / name).write_text(json.dumps(value))
            mixed = root / "original110-mixed-input"
            mixed.mkdir()
            (mixed / "semantic-source.json").write_text("{}")
            with patch.object(audit, "digest", return_value="hash"), \
                 patch.object(historical, "assert_staged"), \
                 patch.object(audit, "capture_actors", create=True,
                              side_effect=RuntimeError("native decoder reached")) as capture, \
                 patch.object(audit.actors, "capture",
                              side_effect=AssertionError("historical decoder bypasses native UUIDs")):
                with self.assertRaisesRegex(RuntimeError, "native decoder reached"):
                    audit.replay(root)
                runtime = root / "original110-natural"
                capture.assert_called_once_with(runtime, "converted", runtime / "ordering-evidence/converted")

    def test_oracle_code_is_unchanged_with_only_platform_targets_bound(self):
        self.assertIs(historical.order_evidence.__code__, native_order_evidence.__code__)
        self.assertEqual("net/minecraft/world/storage/SaveFormat", SUFFIXES["world_info"][0])
        self.assertEqual("net/minecraft/world/chunk/storage/ChunkLoader", SUFFIXES["chunk_status"][0])
        self.assertEqual(historical.SUFFIXES["worldgen_guard"], SUFFIXES["worldgen_guard"])
        self.assertNotEqual(historical.SUFFIXES, SUFFIXES)
        self.assertEqual("coremods-4.0.6.jar", COREMODS.name)
        self.assertEqual(("asm-9.1.jar", "asm-tree-9.1.jar", "modlauncher-8.1.3.jar"),
                         tuple(path.rsplit("/", 1)[1] for path in BRIDGE_LIBRARIES))


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
