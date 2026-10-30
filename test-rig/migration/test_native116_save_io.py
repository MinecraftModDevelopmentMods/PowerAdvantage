import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

import nbtlib

from matrix import WORLD, fingerprint, write_json
from native116_save_io import audit_save, capture_save, require_same_settings


class NativeSaveIoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.runtime = Path(self.temp.name)
        self.world = self.runtime / WORLD
        (self.world / 'region').mkdir(parents=True)
        settings = nbtlib.Compound({
            'seed': nbtlib.Long(42), 'generate_features': nbtlib.Byte(1), 'bonus_chest': nbtlib.Byte(0),
            'dimensions': nbtlib.Compound({key: nbtlib.Compound({
                'type': nbtlib.String(key), 'generator': nbtlib.Compound({
                    'type': nbtlib.String('minecraft:flat'), 'settings': nbtlib.Compound()})})
                for key in ('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')})})
        nbtlib.File({'Data': nbtlib.Compound({'WorldGenSettings': settings})}, gzipped=True).save(self.world / 'level.dat')
        self.rows = [dict(id='boundary', x=12, y=0, z=0, actualName='minecraft:stone', properties={})]
        write_json(self.runtime / 'migration-test.json', self.rows)
        self.chunk()

    def chunk(self, old_packing=False, name='minecraft:stone'):
        palette = nbtlib.List[nbtlib.Compound]([
            nbtlib.Compound({'Name': nbtlib.String('minecraft:air' if i == 0 else name if i == 1 else 'fixture:block_' + str(i))})
            for i in range(17)])
        words = [0] * (320 if old_packing else 342)
        if not old_packing:
            words[1] = 1  # Entry twelve begins a new padded long, not bit sixty.
        chunk = nbtlib.File({'DataVersion': nbtlib.Int(2586), 'Level': nbtlib.Compound({
            'xPos': nbtlib.Int(0), 'zPos': nbtlib.Int(0), 'Sections': nbtlib.List[nbtlib.Compound]([
                nbtlib.Compound({'Y': nbtlib.Byte(0), 'Palette': palette, 'BlockStates': nbtlib.LongArray(words)})])})})
        stream = io.BytesIO()
        chunk.write(stream)
        payload = b'\x02' + zlib.compress(stream.getvalue())
        record = struct.pack('>I', len(payload)) + payload
        sectors = (len(record) + 4095) // 4096
        header = ((2 << 8) | sectors).to_bytes(4, 'big') + bytes(8192 - 4)
        (self.world / 'region/r.0.0.mca').write_bytes(header + record + bytes(sectors * 4096 - len(record)))

    def test_native_padded_save_is_retained_and_replayed_without_world_writes(self):
        before = fingerprint(self.world)
        receipt = capture_save(self.runtime, 'test')
        self.assertEqual('PASS', receipt['status'])
        self.assertEqual(1, receipt['witnesses'])
        self.assertEqual(1, receipt['paddedSections'])
        self.assertEqual(42, receipt['worldGenSettings']['seed'])
        self.assertEqual(receipt, audit_save(self.runtime, receipt))
        self.assertEqual(before, fingerprint(self.world))

    def test_old_cross_long_packing_fails_in_actual_saved_chunk_path(self):
        self.chunk(old_packing=True)
        with self.assertRaisesRegex(ValueError, 'padded length'):
            capture_save(self.runtime, 'test')

    def test_palette_identity_mismatch_is_not_normalized(self):
        self.chunk(name='minecraft:dirt')
        with self.assertRaisesRegex(AssertionError, 'Saved block differs'):
            capture_save(self.runtime, 'test')

    def test_missing_region_cannot_qualify_as_air(self):
        (self.world / 'region/r.0.0.mca').unlink()
        with self.assertRaises(FileNotFoundError):
            capture_save(self.runtime, 'test')

    def test_retained_chunk_tampering_and_duplicate_capture_fail(self):
        receipt = capture_save(self.runtime, 'test')
        with self.assertRaises(FileExistsError):
            capture_save(self.runtime, 'test')
        path = self.runtime / receipt['root'] / next(name for name in receipt['files'] if name.endswith('.chunk'))
        path.write_bytes(b'altered')
        with self.assertRaisesRegex(AssertionError, 'evidence changed'):
            audit_save(self.runtime, receipt)

    def test_complete_native_settings_not_just_seed_are_restart_invariants(self):
        first = capture_save(self.runtime, 'test')
        changed = json.loads(json.dumps(first))
        changed['worldGenSettings']['bonus_chest'] = 1
        with self.assertRaisesRegex(AssertionError, 'WorldGenSettings changed'):
            require_same_settings(first, changed)
        self.assertIsNone(require_same_settings(first, first))


if __name__ == '__main__':
    unittest.main()
