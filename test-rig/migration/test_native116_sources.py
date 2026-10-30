import copy
from contextlib import contextmanager
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import connected
import native115
import native116_sources as sources
from matrix import WORLD, digest, fingerprint, write_json
from native116 import RIG
from native116_campaign import ACCEPTED_POWER114, REQUIRED, require_routes
from native116_sources import ACCEPTED_POWER115, SOURCE115, pending_routes, require_source115_receipt
from test_connected import advanced, entries, nbt, sample


class Native115SourceReceiptTests(unittest.TestCase):
    def setUp(self):
        root = RIG / "build/native116-source-unit"
        root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=root)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def receipt(self, kind="frozen"):
        source = self.root / "stopped-native115"
        (source / WORLD).mkdir(parents=True)
        (source / WORLD / "level.dat").write_bytes(b"stopped source; never launched")
        manifest = self.root / "target115.json"
        write_json(manifest, {})
        record = dict(status="PASS", scope="DISPOSABLE_NATIVE115_SOURCE_ONLY", acceptedPower115=ACCEPTED_POWER115,
                      stoppedCleanly=True, sourceRuntime=str(source.resolve()), runtimeFingerprint=fingerprint(source),
                      worldFingerprint=fingerprint(source / WORLD), target115Manifest=str(manifest),
                      target115ManifestSha256=digest(manifest), blocks=355, tiles=332,
                      preservationStatus="PASS", requestedTicks=[512, 512], advancedTicks=[512, 512])
        path = self.root / "receipt.json"
        write_json(path, record)
        row = dict(id="native115/full" if kind == "frozen" else "native115/original110", kind=kind,
                   source=str(source), sourceStatus="GENERATED_DISPOSABLE_115_VERIFIED",
                   source115Receipt=str(path), source115ReceiptSha256=digest(path))
        return source, path, record, row

    def test_fifteen_frozen_and_fifteen_connected_routes_are_required(self):
        self.assertEqual((15, 15), (len(REQUIRED["frozen"]), len(REQUIRED["connected"])))
        document = dict(format=1, acceptedPower114=ACCEPTED_POWER114,
                        routes=[dict(id=identity, kind=kind) for kind, identities in REQUIRED.items() for identity in identities])
        require_routes(document)
        for kind, identities in SOURCE115.items():
            for identity in identities:
                value = copy.deepcopy(document)
                value["routes"] = [row for row in value["routes"] if row["id"] != identity]
                with self.subTest(kind=kind, identity=identity), self.assertRaises(AssertionError):
                    require_routes(value)

    def test_pending_routes_cannot_be_promoted_by_old_destination_pass(self):
        for row in pending_routes():
            row["status"] = "PASS"
            with self.assertRaisesRegex(AssertionError, "stopped-source receipt"):
                require_source115_receipt(row)

    def test_exact_stopped_source_passes_and_changed_world_fails(self):
        source, _, _, row = self.receipt()
        with patch("native116_sources.native115.pinned_profile"):
            require_source115_receipt(row)
            (source / WORLD / "level.dat").write_bytes(b"different source")
            with self.assertRaisesRegex(AssertionError, "fingerprint"):
                require_source115_receipt(row)

    def test_source_acceptance_needs_scope_merge_and_clean_stop(self):
        _, path, original, row = self.receipt()
        for field, value in (("scope", "native115-destination-PASS"), ("acceptedPower115", "0" * 40),
                             ("stoppedCleanly", False), ("status", "UNQUALIFIED")):
            record = dict(original, **{field: value})
            write_json(path, record)
            row["source115ReceiptSha256"] = digest(path)
            with self.subTest(field=field), patch("native116_sources.native115.pinned_profile"), self.assertRaises(AssertionError):
                require_source115_receipt(row)

    def test_comprehensive_counts_cannot_drop_one_tile(self):
        _, path, record, row = self.receipt()
        record["tiles"] = 331
        write_json(path, record)
        row["source115ReceiptSha256"] = digest(path)
        with patch("native116_sources.native115.pinned_profile"), self.assertRaisesRegex(AssertionError, "355 blocks and 332 tiles"):
            require_source115_receipt(row)

    def test_both_connected_boots_need_exact_native_ticks(self):
        _, path, original, row = self.receipt("connected")
        with patch("native116_sources.native115.pinned_profile"):
            require_source115_receipt(row)
            for field, value in (("requestedTicks", [512]), ("advancedTicks", [512, 511]), ("preservationStatus", "FAIL")):
                write_json(path, dict(original, **{field: value}))
                row["source115ReceiptSha256"] = digest(path)
                with self.subTest(field=field), self.assertRaisesRegex(AssertionError, "512-tick"):
                    require_source115_receipt(row)

    def test_manifest_change_is_not_approved_by_receipt_status(self):
        _, _, record, row = self.receipt()
        Path(record["target115Manifest"]).write_text("changed")
        with self.assertRaisesRegex(AssertionError, "manifest changed"):
            require_source115_receipt(row)


class Native115SourceGeneratorTests(unittest.TestCase):
    def setUp(self):
        root = RIG / 'build/native116-source-unit'
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.seed = self.root / 'seed with spaces'
        (self.seed / 'libraries').mkdir(parents=True)
        (self.seed / 'libraries/library.jar').write_bytes(b'code only')
        (self.seed / native115.LAUNCHER).write_bytes(b'pinned launcher')
        self.manifest = self.root / 'archived F.json'
        write_json(self.manifest, {'target115': {'seed': str(self.seed), 'launcher': native115.LAUNCHER,
            'launcher_sha256': digest(self.seed / native115.LAUNCHER), 'jars': []}})
        self.rows = []
        for kind, name in [('frozen', 'original110-chained-113')] + [('connected', n) for n in sources.HISTORICAL]:
            source = self.root / 'protected fixtures' / (kind + '-' + name)
            (source / WORLD).mkdir(parents=True)
            (source / WORLD / 'level.dat').write_bytes(b'immutable source bytes')
            snapshot = 'migration-restarted.json' if kind == 'frozen' else 'source'
            if kind == 'frozen':
                rows = [dict(id=str(n), nbtBase64='retained tile bytes') if n < 332 else dict(id=str(n)) for n in range(355)]
                write_json(source / snapshot, rows)
                write_json(source / 'migration-fixture.json', rows)
            else:
                write_json(source / 'connected-fixture.json', connected.fixture(name))
                write_json(source / 'migration-fixture.json', connected.fixture(name)['blocks'])
                write_json(source / 'connected-source.json', advanced(sample()))
                write_json(source / 'migration-source.json', advanced(sample())['snapshot'])
            hashes = self.root / ('hashes-' + kind + '-' + name + '.json')
            write_json(hashes, fingerprint(source / WORLD))
            source_manifest = self.root / ('pins-' + kind + '-' + name + '.json')
            write_json(source_manifest, {})
            selected = source / (snapshot if kind == 'frozen' else 'migration-source.json')
            self.rows.append(dict(id='legacy/' + name, kind=kind, source=str(source), sourceKey=name,
                sourceManifest=str(source_manifest), sourceManifestSha256=digest(source_manifest),
                sourceHashes=str(hashes), sourceHashesSha256=digest(hashes),
                sourceSnapshot=snapshot, sourceSnapshotSha256=digest(selected)))
        self.routes = self.root / 'approved routes.json'
        write_json(self.routes, dict(routes=self.rows))
        self.args = SimpleNamespace(source_routes=self.routes, target115_manifest=self.manifest,
            output=self.root / 'new sources with spaces', java8=self.root / 'java.exe', port=25575, validate_only=False)

    @contextmanager
    def pinned(self):
        with patch.object(sources, 'ARCHIVED115_F_SHA256', digest(self.manifest)), \
                patch.object(sources, 'ARCHIVED115_ROUTES_SHA256', digest(self.routes)), \
                patch.object(native115, 'pinned_profile', return_value=(self.seed, native115.LAUNCHER, [])), \
                patch('native115_connected.verify_source'):
            yield

    def plan(self):
        with self.pinned():
            return sources.preflight_source115(self.args)

    def stops(self):
        return [dict(label=label, returncode=0, commands=['save-all flush', 'stop']) for label in ('converted', 'restarted')]

    def frozen_result(self, runtime):
        runtime.mkdir(parents=True)
        (runtime / WORLD).mkdir()
        (runtime / WORLD / 'level.dat').write_bytes(b'unit saved native115 world')
        rows = [dict(id=str(n), nbtBase64='retained tile bytes') if n < 332 else dict(id=str(n)) for n in range(355)]
        write_json(runtime / 'migration-restarted.json', rows)
        write_json(runtime / 'migration-fixture.json', rows)
        write_json(runtime / 'state-comparison.json', {gate: dict(status='PASS', witnesses=dict(blocks=355, tiles=332))
            for gate in ('converted', 'restarted', 'idempotence')})
        for label in ('converted', 'restarted'):
            (runtime / ('console-' + label + '.log')).write_text(
                'Starting minecraft server version 1.15.2\n'
                'Forge mod loading, version 31.2.57, for MC 1.15.2 with MCP 20200515.085601\n'
                'Saved the game\nStopping server\nSaving worlds\nAll chunks are saved\n')

    def test_preflight_selects_six_copied_source_recipes_without_creation_or_launch(self):
        with patch('subprocess.Popen', side_effect=AssertionError('No lease used in tests')):
            plan = self.plan()
        self.assertEqual(set().union(*sources.SOURCE115.values()), {row['id'] for row in plan['sources']})
        self.assertFalse(self.args.output.exists())

    def test_wrong_archived_F_hash_fails_before_any_output(self):
        with self.pinned(), patch.object(sources, 'ARCHIVED115_F_SHA256', '0' * 64), self.assertRaisesRegex(AssertionError, 'archived F'):
            sources.preflight_source115(self.args)
        self.assertFalse(self.args.output.exists())

    def test_wrong_archived_source_plan_hash_fails_before_any_output(self):
        with self.pinned(), patch.object(sources, 'ARCHIVED115_ROUTES_SHA256', '0' * 64), self.assertRaisesRegex(AssertionError, 'source plan'):
            sources.preflight_source115(self.args)
        self.assertFalse(self.args.output.exists())

    def test_comprehensive_331_tiles_is_not_a_source(self):
        source = Path(self.rows[0]['source'])
        path = source / self.rows[0]['sourceSnapshot']
        rows = json.loads(path.read_text())
        rows[331].pop('nbtBase64')
        write_json(path, rows)
        self.rows[0]['sourceSnapshotSha256'] = digest(path)
        write_json(self.routes, dict(routes=self.rows))
        with self.assertRaisesRegex(AssertionError, '355 blocks and 332 tiles'):
            self.plan()
        self.assertFalse(self.args.output.exists())

    def test_changed_world_is_rejected_before_output(self):
        (Path(self.rows[0]['source']) / WORLD / 'level.dat').write_bytes(b'changed')
        with self.assertRaisesRegex(AssertionError, 'world fingerprint'):
            self.plan()

    def test_duplicate_source_recipe_is_rejected(self):
        write_json(self.routes, dict(routes=self.rows + [self.rows[0]]))
        with self.assertRaisesRegex(AssertionError, 'exactly once'):
            self.plan()

    def test_output_cannot_overlap_canonical_source(self):
        self.args.output = Path(self.rows[0]['source']) / 'new-output'
        with self.assertRaisesRegex(ValueError, 'overlaps a protected input'):
            self.plan()

    def test_seed_or_source_changes_after_preflight_fail(self):
        plan = self.plan()
        (self.seed / 'libraries/library.jar').write_bytes(b'changed code')
        with self.assertRaisesRegex(AssertionError, 'seed code'):
            sources.verify_source115_inputs(plan)

    def test_valid_frozen_result_requires_real_two_boot_clean_stops_and_counts(self):
        runtime = self.root / 'disposable-result'
        self.frozen_result(runtime)
        settings = SimpleNamespace(output=runtime)
        actual, snapshot, fixture, proof = sources.source115_result('frozen', settings, self.stops())
        self.assertEqual((runtime, 'migration-restarted.json', None), (actual, snapshot, fixture))
        self.assertEqual((355, 332, True), (proof['blocks'], proof['tiles'], proof['stoppedCleanly']))

    def test_nonzero_missing_save_or_missing_second_stop_cannot_qualify(self):
        runtime = self.root / 'disposable-result'
        self.frozen_result(runtime)
        for stops in (self.stops()[:1], [dict(row, returncode=1) for row in self.stops()],
                      [dict(row, commands=['stop']) for row in self.stops()]):
            with self.subTest(stops=stops), self.assertRaisesRegex(AssertionError, 'cleanly saved and stopped'):
                sources.source115_result('frozen', SimpleNamespace(output=runtime), stops)

    def test_success_exit_does_not_hide_missing_semantic_gate(self):
        runtime = self.root / 'disposable-result'
        self.frozen_result(runtime)
        write_json(runtime / 'state-comparison.json', {'converted': dict(status='PASS')})
        with self.assertRaisesRegex(AssertionError, 'semantic'):
            sources.source115_result('frozen', SimpleNamespace(output=runtime), self.stops())

    def test_registry_errors_are_never_filtered_from_source_receipt(self):
        runtime = self.root / 'disposable-result'
        self.frozen_result(runtime)
        with (runtime / 'console-converted.log').open('a') as stream:
            stream.write('[main/ERROR] retained failure\n')
        with self.assertRaisesRegex(AssertionError, 'Native runtime errors'):
            sources.source115_result('frozen', SimpleNamespace(output=runtime), self.stops())

    def connected_result(self):
        output = self.root / 'connected-result'
        runtime = output / 'target115'
        self.frozen_result(runtime)
        write_json(runtime / 'connected-fixture.json', connected.fixture('original110'))
        first = advanced(sample())
        first.update(requestedTicks=512, advancedTicks=512)
        second = copy.deepcopy(first)
        rows = entries(second)
        nbt(rows['C-CONVEYOR-input'], '{Items:[{id:"minecraft:dirt",Count:1726}]}')
        nbt(rows['C-CONVEYOR-output'], '{Items:[{id:"minecraft:dirt",Count:2b}]}')
        for station, count in (('C-STEAM', 190), ('C-ELECTRIC', 62)):
            nbt(rows[station + '-furnace'], '{Items:[{id:"minecraft:iron_ore",Count:%d},{id:"minecraft:iron_ingot",Count:2b}]}' % count)
        nbt(rows['C-STEAM-crusher'], '{Items:[{id:"minecraft:cobblestone",Count:62b},{id:"minecraft:gravel",Count:2b}]}')
        for station, count, item in (('C-SDRILL', 2, 'gravel'), ('C-EDRILL', 18, 'cobblestone')):
            targets = [e for e in second['snapshot'] if e['station'] == station and e['kind'].startswith('target-')]
            for target in targets[:count]:
                target['actualName'] = 'minecraft:air'
            nbt(rows[station + '-drill'], '{Items:[{id:"minecraft:%s",Count:%db}]}' % (item, count))
        for label, before, after in (('converted', sample(), first), ('restarted', first, second)):
            loaded = copy.deepcopy(before)
            loaded.update(requestedTicks=0, advancedTicks=0)
            write_json(runtime / ('connected-' + label + '-loaded.json'), loaded)
            write_json(runtime / ('connected-' + label + '-worked.json'), after)
        gates = {label + '_' + gate: dict(status='PASS') for label in ('converted', 'restarted')
                 for gate in ('preservation', 'operation', 'native_boot')}
        gates['real_output_across_restarts'] = dict(status='PASS')
        write_json(output / 'native115-connected-results.json', dict(status='PASS', sourcePreservation=dict(status='PASS'), results=gates))
        return SimpleNamespace(output=output), runtime

    def test_connected_raw_two_interval_proof_uses_unchanged_oracle(self):
        settings, runtime = self.connected_result()
        actual, snapshot, key, proof = sources.source115_result('connected', settings, self.stops())
        self.assertEqual((runtime, 'restarted-worked', 'original110'), (actual, snapshot, key))
        self.assertEqual(([512, 512], [512, 512]), (proof['requestedTicks'], proof['advancedTicks']))

    def test_claimed_pass_cannot_hide_511_raw_ticks(self):
        settings, runtime = self.connected_result()
        path = runtime / 'connected-restarted-worked.json'
        document = json.loads(path.read_text())
        document['advancedTicks'] = 511
        write_json(path, document)
        with self.assertRaisesRegex(AssertionError, '512-tick'):
            sources.source115_result('connected', settings, self.stops())

    def test_claimed_pass_cannot_hide_a_rebuilt_machine_or_empty_gate_set(self):
        settings, runtime = self.connected_result()
        path = runtime / 'connected-restarted-worked.json'
        document = json.loads(path.read_text())
        document['buildCallsThisProcess'] = 1
        write_json(path, document)
        with self.assertRaisesRegex(AssertionError, 'construction is forbidden'):
            sources.source115_result('connected', settings, self.stops())
        write_json(settings.output / 'native115-connected-results.json', dict(status='PASS', sourcePreservation=dict(status='PASS'), results={}))
        with self.assertRaisesRegex(AssertionError, 'operating gates'):
            sources.source115_result('connected', settings, self.stops())

    def test_frozen_process_adapter_refuses_a_canonical_launch_before_popen(self):
        settings = SimpleNamespace(output=self.root / 'new-copy', port=25575)
        def bad_protocol(arguments):
            native115.start_snapshot(Path(self.rows[0]['source']), Path('java.exe'), native115.LAUNCHER, 'converted')
        with patch.object(native115, 'run', side_effect=bad_protocol), patch('subprocess.Popen') as launch:
            with self.assertRaisesRegex(AssertionError, 'NEW disposable'):
                sources.run_source115(settings, 'frozen')
            launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
