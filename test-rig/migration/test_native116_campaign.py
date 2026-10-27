"""Source approval requires a recorded stopped-runtime receipt, never a fresh hash."""

from argparse import Namespace
from pathlib import Path
import copy
import json
import tempfile
import unittest
from unittest.mock import patch

from matrix import WORLD, digest, fingerprint, write_json
from native116 import RIG
from native116_campaign import ACCEPTED_POWER114, REQUIRED, generate114, require_source114_receipt, source_row
import native116_campaign as campaign
from native116_sources import SOURCE115
import test_native116_sources as source_tests


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
        with patch("native116_campaign.native114.pinned_profile"):
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
        with patch("native116_campaign.native114.pinned_profile"), self.assertRaisesRegex(AssertionError, "355-block/332-tile"):
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
        with patch("native116_campaign.native116.pinned_profile"):
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
        with patch("native116_campaign.native114.pinned_profile", return_value=(self.root / "seed", "launcher", [])), \
                patch("native116_campaign.native114.run") as launch:
            with self.assertRaisesRegex(AssertionError, "355-block/332-tile"):
                generate114(settings)
            launch.assert_not_called()
        self.assertEqual(fingerprint(source), before)
        self.assertTrue((settings.output / "native114-full/source114-receipt.json").is_file())


class Generate115CampaignTests(unittest.TestCase):
    def setUp(self):
        self.fixture = source_tests.Native115SourceGeneratorTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.args = self.fixture.args
        recipes = {(row['kind'], row['id']): row for row in self.fixture.rows}
        rows = []
        for kind, identities in REQUIRED.items():
            for identity in sorted(identities - SOURCE115[kind]):
                rows.append(copy.deepcopy(recipes.get((kind, identity), dict(id=identity, kind=kind))))
        self.plan_document = dict(format=1, acceptedPower114=ACCEPTED_POWER114, routes=rows)
        write_json(self.fixture.routes, self.plan_document)

    def fake_protocol(self, settings, kind):
        runtime = settings.output if kind == 'frozen' else settings.output / 'target115'
        self.fixture.frozen_result(runtime)
        if kind == 'connected':
            write_json(runtime / 'migration-restarted-worked.json', [])
        return 0, self.fixture.stops()

    def fake_result(self, kind, settings, stops):
        runtime = settings.output if kind == 'frozen' else settings.output / 'target115'
        proof = dict(stoppedCleanly=True, blocks=355, tiles=332, preservationStatus='PASS',
                     requestedTicks=[512, 512], advancedTicks=[512, 512])
        return runtime, 'migration-restarted.json' if kind == 'frozen' else 'restarted-worked', settings.source_key, proof

    def test_validate_only_does_not_create_any_output_or_launch(self):
        self.args.validate_only = True
        with self.fixture.pinned(), patch.object(campaign, 'run_source115') as launch:
            self.assertEqual(0, campaign.generate115(self.args))
            launch.assert_not_called()
        self.assertFalse(self.args.output.exists())

    def test_six_receipts_and_thirty_routes_keep_canonical_inputs_unchanged(self):
        original = fingerprint(self.fixture.root / 'protected fixtures')
        with self.fixture.pinned(), patch.object(campaign, 'run_source115', side_effect=self.fake_protocol) as run, \
                patch.object(campaign, 'source115_result', side_effect=self.fake_result):
            self.assertEqual(0, campaign.generate115(self.args))
            self.assertEqual(6, run.call_count)
            for call in run.call_args_list:
                self.assertEqual((25575, 512), (call.args[0].port, call.args[0].ticks))
                self.assertTrue(call.args[0].output.is_relative_to(self.args.output))
            bundle = json.loads((self.args.output / 'source115-receipts.json').read_text())
            document = json.loads((self.args.output / 'native116-upgrade-routes.json').read_text())
            self.assertEqual(('PASS', 6), (bundle['status'], len(bundle['receipts'])))
            campaign.require_routes(document)
            for row in document['routes']:
                campaign.require_source115_receipt(row)
            self.assertEqual('UNQUALIFIED', document['qualificationStatus'])
        self.assertEqual(original, fingerprint(self.fixture.root / 'protected fixtures'))
        self.assertEqual(self.plan_document, json.loads(self.fixture.routes.read_text()))

    def test_failed_second_source_keeps_first_receipt_and_partial_failure(self):
        calls = []
        def protocol(settings, kind):
            calls.append(settings)
            if len(calls) == 2:
                raise RuntimeError('retained server failure')
            return self.fake_protocol(settings, kind)
        with self.fixture.pinned(), patch.object(campaign, 'run_source115', side_effect=protocol), \
                patch.object(campaign, 'source115_result', side_effect=self.fake_result):
            with self.assertRaisesRegex(RuntimeError, 'retained server failure'):
                campaign.generate115(self.args)
        bundle = json.loads((self.args.output / 'source115-receipts.json').read_text())
        self.assertEqual(('FAIL', 1), (bundle['status'], len(bundle['receipts'])))
        first = json.loads(Path(bundle['receipts'][0]['receipt']).read_text())
        self.assertEqual('PASS', first['status'])
        failed = json.loads((calls[1].output.parent / 'source115-receipt.json').read_text())
        self.assertEqual(('FAIL', False), (failed['status'], failed['stoppedCleanly']))
        with self.fixture.pinned(), self.assertRaises(FileExistsError):
            campaign.generate115(self.args)

    def test_parent_canonical_thirty_routes_are_retained_without_replacing_114(self):
        canonical = copy.deepcopy(self.plan_document)
        canonical['routes'].extend(campaign.pending_routes())
        for row in canonical['routes']:
            if row['id'].startswith('native114/'):
                row['sourceStatus'] = 'REQUIRES_DISPOSABLE_114_GENERATION'
        self.args.routes = self.fixture.root / 'parent canonical routes.json'
        write_json(self.args.routes, canonical)
        before = digest(self.args.routes)
        with self.fixture.pinned(), patch.object(campaign, 'run_source115', side_effect=self.fake_protocol), \
                patch.object(campaign, 'source115_result', side_effect=self.fake_result):
            self.assertEqual(0, campaign.generate115(self.args))
        actual = json.loads((self.args.output / 'native116-upgrade-routes.json').read_text())
        self.assertEqual(before, digest(self.args.routes))
        self.assertEqual([row for row in canonical['routes'] if row['id'].startswith('native114/')],
                         [row for row in actual['routes'] if row['id'].startswith('native114/')])

    def test_canonical_recipe_change_is_not_a_new_source_approval(self):
        canonical = copy.deepcopy(self.plan_document)
        canonical['routes'].extend(campaign.pending_routes())
        next(row for row in canonical['routes'] if row['id'] == 'legacy/original110-chained-113')['sourceHashesSha256'] = '0' * 64
        self.args.routes = self.fixture.root / 'changed routes.json'
        write_json(self.args.routes, canonical)
        with self.fixture.pinned(), patch.object(campaign, 'run_source115') as launch:
            with self.assertRaisesRegex(AssertionError, 'sealed F source lineage'):
                campaign.generate115(self.args)
            launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
