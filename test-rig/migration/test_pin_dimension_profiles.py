import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from matrix import digest
from pin_dimension_profiles import pin


class DimensionProfilePins(unittest.TestCase):
    def fixture(self, root):
        probe = root / "test-rig/build/libs/AdvantageWorksTestHarness-1.1.0.113021.jar"
        probe.parent.mkdir(parents=True)
        with zipfile.ZipFile(probe, "w") as archive:
            archive.writestr("zone/moddev/mc/advantageworks/DimensionEntityProbe.class", b"fixture-probe")
        production = root / "PowerAdvantage.jar"
        production.write_bytes(b"unchanged-production")
        original = {"target113": {"seed": "unused", "launcher": "unused", "jars": [
            {"path": str(production), "sha256": digest(production)},
            {"path": str(probe), "sha256": "previous-probe-hash"}]}}
        base = root / "profiles.json"
        base.write_text(json.dumps(original))
        return base, production, probe, original

    def test_only_probes_are_replaced_and_archived(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, production, probe, original = self.fixture(root)
            with patch("pin_dimension_profiles.POWER", root):
                result = pin(base, root / "new/profiles.json", root / "archive")
            jars = result["target113"]["jars"]
            self.assertEqual(original["target113"]["jars"][0], jars[0])
            archived = Path(jars[1]["path"])
            self.assertNotEqual(probe, archived)
            self.assertEqual(digest(probe), digest(archived))
            self.assertEqual(original, json.loads(base.read_text()))
            probe.write_bytes(b"later-build")
            self.assertEqual(jars[1]["sha256"], digest(archived))
            self.assertEqual(original["target113"]["jars"][0]["sha256"], digest(production))

    def test_changed_production_jar_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, production, _, _ = self.fixture(root)
            production.write_bytes(b"different-production")
            with patch("pin_dimension_profiles.POWER", root), self.assertRaisesRegex(AssertionError, "Production/dependency"):
                pin(base, root / "new/profiles.json")

    def test_missing_dimension_probe_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, _, probe, _ = self.fixture(root)
            with zipfile.ZipFile(probe, "w") as archive:
                archive.writestr("unrelated.class", b"not-a-dimension-probe")
            with patch("pin_dimension_profiles.POWER", root), self.assertRaisesRegex(AssertionError, "probe missing"):
                pin(base, root / "new/profiles.json")


if __name__ == "__main__":
    unittest.main()
