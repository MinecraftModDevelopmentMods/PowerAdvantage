"""Retain and replay native saved-format evidence from disposable destinations only."""

import gzip
import io
import json
from pathlib import Path
import re
import shutil
import zlib

import nbtlib

from matrix import WORLD, digest, plain, write_json
from native116_format import decode_palette, worldgen_settings
from transformer_order import read_region


def chunk_data(payload):
    if len(payload) < 5 or int.from_bytes(payload[:4], 'big') != len(payload) - 4:
        raise ValueError('Malformed retained chunk length')
    decoders = {1: gzip.decompress, 2: zlib.decompress, 3: lambda value: value}
    if payload[4] not in decoders:
        raise ValueError('Unsupported native chunk compression')
    data = plain(nbtlib.File.parse(io.BytesIO(decoders[payload[4]](payload[5:]))).unpack())
    if data.get('DataVersion') != 2586:
        raise ValueError('Saved chunk is not native Minecraft 1.16.5 DataVersion 2586')
    return data['Level']


def sections(chunk):
    result = {}
    for section in chunk['Sections']:
        y = section['Y']
        if y in result:
            raise ValueError('Duplicate saved section')
        if 'Blocks' in section or 'Data' in section or 'Add' in section:
            raise ValueError('Legacy numeric chunk section is not native116 evidence')
        if 'Palette' in section:
            result[y] = decode_palette(section['Palette'], section.get('BlockStates', []))
        elif 'BlockStates' in section:
            raise ValueError('BlockStates without a palette')
        else:
            result[y] = None
    return result


def audit_save(runtime, receipt):
    root = (runtime / receipt['root']).resolve()
    if not root.is_relative_to(runtime.resolve() / 'native116-save-format'):
        raise ValueError('Saved-format evidence escaped its disposable destination')
    for name, checksum in receipt['files'].items():
        path = (root / name).resolve()
        if not path.is_relative_to(root) or digest(path) != checksum:
            raise AssertionError('Retained native save evidence changed: ' + name)
    actual_settings = worldgen_settings(plain(nbtlib.load(root / 'level.dat')['Data'].unpack()))
    if actual_settings != receipt['worldGenSettings']:
        raise AssertionError('Retained WorldGenSettings differs from raw level.dat')
    rows = json.loads((root / 'snapshot.json').read_text(encoding='utf-8'))
    cache = {}
    for row in rows:
        x, y, z = (int(row[key]) for key in ('x', 'y', 'z'))
        key = (x // 16, z // 16)
        if key not in cache:
            chunk = chunk_data((root / 'chunks' / ('%d_%d.chunk' % key)).read_bytes())
            if (chunk['xPos'], chunk['zPos']) != key:
                raise AssertionError('Saved chunk coordinate differs from witness')
            cache[key] = sections(chunk)
        blocks = cache[key].get(y // 16)
        state = blocks[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] if blocks else {'Name': 'minecraft:air'}
        if (state['Name'], state.get('Properties', {})) != (row['actualName'], row['properties']):
            raise AssertionError('Saved block differs from native snapshot: ' + str((x, y, z)))
    padded = sum(blocks is not None for chunk in cache.values() for blocks in chunk.values())
    if receipt['witnesses'] != len(rows) or not rows or receipt['paddedSections'] != padded:
        raise AssertionError('Saved-format witness/section count differs')
    if receipt['status'] != 'PASS':
        raise AssertionError('Saved-format receipt did not pass')
    return receipt


def capture_save(runtime, label, snapshot=None):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', label):
        raise ValueError('Unsafe saved-format label')
    root = runtime / 'native116-save-format' / label
    root.mkdir(parents=True, exist_ok=False)
    world = runtime / WORLD
    snapshot = snapshot or runtime / ('migration-' + label + '.json')
    shutil.copy2(world / 'level.dat', root / 'level.dat')
    shutil.copy2(snapshot, root / 'snapshot.json')
    rows = json.loads((root / 'snapshot.json').read_text(encoding='utf-8'))
    region_cache, chunk_cache = {}, {}
    (root / 'chunks').mkdir()
    for row in rows:
        cx, cz = int(row['x']) // 16, int(row['z']) // 16
        if (cx, cz) in chunk_cache:
            continue
        region = (cx // 32, cz // 32)
        if region not in region_cache:
            _, region_cache[region] = read_region(world / 'region' / ('r.%d.%d.mca' % region))
        payload = region_cache[region][(cx & 31) + (cz & 31) * 32]
        (root / 'chunks' / ('%d_%d.chunk' % (cx, cz))).write_bytes(payload)
        chunk_cache[(cx, cz)] = sections(chunk_data(payload))
    receipt = dict(status='PASS', root=str(root.relative_to(runtime)),
        worldGenSettings=worldgen_settings(plain(nbtlib.load(root / 'level.dat')['Data'].unpack())),
        witnesses=len(rows), paddedSections=sum(blocks is not None for chunk in chunk_cache.values() for blocks in chunk.values()),
        files={str(path.relative_to(root)): digest(path) for path in sorted(root.rglob('*')) if path.is_file()})
    audit_save(runtime, receipt)
    write_json(root / 'receipt.json', receipt)
    return receipt


def require_same_settings(first, second):
    if first['worldGenSettings'] != second['worldGenSettings']:
        raise AssertionError('Full native WorldGenSettings changed across restart')
