"""Source approval requires a recorded stopped-runtime receipt, never a fresh hash."""

from argparse import Namespace
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from matrix import WORLD, digest, fingerprint, write_json
from native115 import RIG
from native115_campaign import ACCEPTED_POWER114, REQUIRED, generate114, require_source114_receipt, source_row


class SourceReceiptTests(unittest.TestCase):
    def setUp(self):
        root = RIG / "build/port-evidence"
        root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=root)
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def receipt(self):
        source = self.root / "disposable114"
        (source / WORLD).mkdir(parents=True)
        (source / WORLD / "level.dat").write_bytes(b"immutable stopped source witness")
        manifest = self.root / "accepted114.json"
        write_json(manifest, {})
        receipt_path = self.root / "receipt.json"
        receipt = dict(status="PASS", acceptedPower114=ACCEPTED_POWER114, sourceRuntime=str(source.resolve()),
                       runtimeFingerprint=fingerprint(source), worldFingerprint=fingerprint(source / WORLD),
                       target114Manifest=str(manifest), target114ManifestSha256=digest(manifest), blocks=355, tiles=332)
        write_json(receipt_path, receipt)
        row = dict(id="native114/full", kind="frozen", source=str(source), source114Receipt=str(receipt_path),
                   source114ReceiptSha256=digest(receipt_path), sourceStatus="GENERATED_DISPOSABLE_114_VERIFIED")
        return source, receipt_path, row

    def test_recorded_whole_runtime_and_world_fingerprints_are_both_verified(self):
        source, _, row = self.receipt()
        with patch("native115_campaign.native114.pinned_profile"):
            require_source114_receipt(row)
            (source / WORLD / "level.dat").write_bytes(b"changed")
            with self.assertRaisesRegex(AssertionError, "fingerprint changed"):
                require_source114_receipt(row)

    def test_counts_cannot_be_downgraded_even_with_a_new_receipt_digest(self):
        source, receipt_path, row = self.receipt()
        import json
        receipt = json.loads(receipt_path.read_text())
        receipt["tiles"] = 331
        write_json(receipt_path, receipt)
        row["source114ReceiptSha256"] = digest(receipt_path)
        with patch("native115_campaign.native114.pinned_profile"), self.assertRaisesRegex(AssertionError, "355-block/332-tile"):
            require_source114_receipt(row)

    def test_generated_source_metadata_is_disjoint_from_the_stopped_runtime(self):
        bundle = self.root / "generated"
        source = bundle / "native114-full/runtime"
        (source / WORLD).mkdir(parents=True)
        (source / WORLD / "level.dat").write_bytes(b"stopped world")
        write_json(source / "migration-restarted.json", [])
        write_json(source / "migration-fixture.json", {})
        manifest = self.root / "accepted114.json"
        write_json(manifest, {})
        before = fingerprint(source)
        with patch("native115_campaign.native115.pinned_profile"):
            with self.assertRaisesRegex(ValueError, "overlaps a protected input"):
                source_row(bundle, "frozen", "native114/full", source, manifest, "target114", "migration-restarted.json")
            row = source_row(bundle / "source-records", "frozen", "native114/full", source, manifest,
                             "target114", "migration-restarted.json")
        self.assertEqual(fingerprint(source), before)
        self.assertTrue(Path(row["sourceHashes"]).is_relative_to(bundle / "source-records"))
        self.assertEqual(digest(Path(row["sourceHashes"])), row["sourceHashesSha256"])

    def test_source_generation_does_not_launch_an_incomplete_comprehensive_fixture(self):
        source = self.root / "approved13"
        source.mkdir()
        write_json(source / "migration-restarted.json", [])
        manifest, hashes = self.root / "pins.json", self.root / "hashes.json"
        write_json(manifest, {})
        write_json(hashes, {})
        recipe = dict(source=str(source), sourceManifest=str(manifest), sourceManifestSha256=digest(manifest),
                      sourceHashes=str(hashes), sourceHashesSha256=digest(hashes), sourceKey="target113",
                      sourceSnapshot="migration-restarted.json", sourceSnapshotSha256=digest(source / "migration-restarted.json"))
        rows = []
        for kind, names in REQUIRED.items():
            for name in sorted(names):
                row = dict(kind=kind, id=name)
                if name.startswith("native114/"):
                    row.update(native114SourceRecipe=recipe, source114Manifest=str(manifest),
                               source114ManifestSha256=digest(manifest), sourceStatus="REQUIRES_DISPOSABLE_114_GENERATION")
                rows.append(row)
        # Put the comprehensive source first; no process is needed to reject it.
        rows.sort(key=lambda row: row["id"] != "native114/full")
        plan = self.root / "routes.json"
        write_json(plan, dict(format=1, acceptedPower114=ACCEPTED_POWER114, routes=rows))
        settings = Namespace(routes=plan, java8=self.root / "java.exe", output=self.root / "new", port=25595)
        before = fingerprint(source)
        with patch("native115_campaign.native114.pinned_profile", return_value=(self.root / "seed", "launcher", [])), \
                patch("native115_campaign.native114.run") as launch:
            with self.assertRaisesRegex(AssertionError, "355-block/332-tile"):
                generate114(settings)
            launch.assert_not_called()
        self.assertEqual(fingerprint(source), before)
        self.assertTrue((settings.output / "native114-full/source114-receipt.json").is_file())


if __name__ == "__main__":
    unittest.main()
