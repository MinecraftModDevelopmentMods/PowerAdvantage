import copy
import unittest
from unittest.mock import patch
import json
from pathlib import Path
import tempfile

from native116_respawn import native_respawn_verdict, publish_definition
from native116_audit_respawn import replay


class NativeRespawnAdapterTest(unittest.TestCase):
    def test_tick_reader_never_observes_definition_truncation(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "dimension-fixture.json"
            old = {"dimension": -1, "respawn": {"bedMode": "forced", "keepInventory": True}}
            new = {"dimension": -1, "respawn": {"bedMode": "bed", "keepInventory": True}}
            path.write_text(json.dumps(old))
            observed = []
            def interleaved_writer(target, value):
                target.write_text("")
                observed.append(json.loads(path.read_text()))
                target.write_text(json.dumps(value))
            with patch("native116_respawn.write_json", side_effect=interleaved_writer):
                publish_definition(path, new)
            self.assertEqual([old], observed)
            self.assertEqual(new, json.loads(path.read_text()))

    def test_failed_definition_write_keeps_live_payload_and_cleans_temporary_file(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "dimension-fixture.json"
            old = {"dimension": 1, "respawn": {"bedMode": "missing", "keepInventory": False}}
            path.write_text(json.dumps(old))
            with patch("native116_respawn.write_json", side_effect=OSError("disk write failed")):
                with self.assertRaisesRegex(OSError, "disk write failed"):
                    publish_definition(path, {"dimension": -1})
            self.assertEqual(old, json.loads(path.read_text()))
            self.assertEqual([path], list(path.parent.iterdir()))

    def test_only_exact_native_handler_is_translated(self):
        for handler, expected in (("ServerPlayNetHandler.PERFORM_RESPAWN", "NetHandlerPlayServer.PERFORM_RESPAWN"),
                                  ("Other.PERFORM_RESPAWN", "Other.PERFORM_RESPAWN"),
                                  ("NetHandlerPlayServer.PERFORM_RESPAWN", "NetHandlerPlayServer.PERFORM_RESPAWN")):
            original = {"respawn": {"handler": handler}, "retain": {"Inventory": [17], "bed": "missing"}}
            before = copy.deepcopy(original)
            with patch("native116_respawn.respawn_verdict", return_value={"status": "TEST"}) as oracle:
                self.assertEqual({"status": "TEST"}, native_respawn_verdict(original, {1: "enchantment"}, {"radius": 10}))
                adapted = copy.deepcopy(before)
                adapted["respawn"]["handler"] = expected
                oracle.assert_called_once_with(adapted, {1: "enchantment"}, {"radius": 10})
            self.assertEqual(before, original)

    def test_aborted_campaign_never_becomes_a_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in ("results.json", "source-fingerprints.json"):
                (root / name).write_text(json.dumps({}))
            manifest = root / "archived-inputs.json"
            manifest.write_text("{}")
            with self.assertRaisesRegex(ValueError, "explicit pinned target manifest"):
                replay(root)
            with patch("native116_audit_respawn.pinned_profile") as pin:
                result = replay(root, manifest)
            pin.assert_called_once_with(manifest.resolve(), "target116")
            self.assertEqual("FAIL", result["status"])
            self.assertIn("Campaign summary missing", result["findings"][0])
            self.assertFalse((root / "summary.json").exists())


if __name__ == "__main__":
    unittest.main()
