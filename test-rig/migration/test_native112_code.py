"""Server-code drift is rejected before process creation, without opening worlds."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from native112_code import INVENTORY, Server, code_files, verify_code


class Native112CodeCustodyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "libraries/native").mkdir(parents=True)
        (self.root / "libraries/native/server.jar").write_bytes(b"native")
        (self.root / "forge.jar").write_bytes(b"forge")
        self.expected = {"files": code_files(self.root)}
        (self.root / INVENTORY).write_text(json.dumps(self.expected), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_exact_seal_accepts_world_and_config_changes_outside_code(self):
        (self.root / "world").mkdir()
        (self.root / "world/level.dat").write_bytes(b"mutable world")
        (self.root / "server.properties").write_text("mutable configuration")
        self.assertEqual(self.expected, verify_code(self.root))

    def test_changed_missing_extra_and_timestamp_only_archive_bytes_are_rejected(self):
        original = self.root / "libraries/native/server.jar"
        for mutation in ("changed", "missing", "extra", "zip-timestamp-only"):
            original.write_bytes(b"native")
            extra = self.root / "libraries/extra.jar"
            if extra.exists():
                extra.unlink()
            if mutation in ("changed", "zip-timestamp-only"):
                original.write_bytes(b"nativE")
            elif mutation == "missing":
                original.unlink()
            else:
                extra.write_bytes(b"extra")
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                verify_code(self.root)

    def test_code_failure_precedes_external_process_creation(self):
        (self.root / "forge.jar").write_bytes(b"changed")
        with patch("rig_transport.subprocess.Popen") as process, self.assertRaises(AssertionError):
            Server(self.root, Path("java"), "forge.jar", "must-not-start")
        process.assert_not_called()

    def test_incomplete_inventory_cannot_authorize_runtime(self):
        with self.assertRaises(ValueError):
            verify_code(self.root, {"files": {}})

    def test_extra_root_jar_is_not_exempted(self):
        (self.root / "installer.jar").write_bytes(b"unexpected")
        with self.assertRaises(AssertionError):
            verify_code(self.root)


if __name__ == "__main__":
    unittest.main()
