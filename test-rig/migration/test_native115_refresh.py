from pathlib import Path
import tempfile
import unittest

from matrix import digest
from native115_refresh import snapshot_name


class RefreshProvenanceTests(unittest.TestCase):
    def test_only_exact_archived_snapshot_is_selected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "migration-source.json"
            second = root / "migration-restarted.json"
            first.write_text("source")
            second.write_text("different")
            self.assertEqual(snapshot_name(root, digest(first)), first.name)
            with self.assertRaisesRegex(AssertionError, "No retained"):
                snapshot_name(root, "0" * 64)
            second.write_text("source")
            self.assertEqual(snapshot_name(root, digest(first)), second.name)


if __name__ == "__main__":
    unittest.main()
