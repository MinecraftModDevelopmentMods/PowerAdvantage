"""Negative controls for packaged order proof and mixed-fixture construction."""

import copy
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib

import nbtlib

from matrix import WORLD, digest, write_json
from transformer_order import (ENGINE, METAL_BLOCKS, SUFFIXES, add_metal_blocks, metal_item,
                               backup_provenance, order_evidence, read_region, strict_equal, validate_patch)


def hook(mod, name):
    return {"method": "target()V", "index": 1, "owner": "zone/moddev/mc/" +
            ("basemetals" if mod == "bm" else "poweradvantage") + "/migration/LegacyWorldDataHook",
            "name": name, "descriptor": "()V"}


class OrderEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.events = [{"event": "registration", "mode": "bm-first", "natural": [],
                        "forced": [mod + "_legacy_" + suffix for suffix in SUFFIXES
                                   for mod in ("basemetals", "poweradvantage")]}]
        for suffix, (class_name, methods) in SUFFIXES.items():
            first = [hook("bm", name) for name in methods]
            final = []
            for name in methods:
                pair = ("bm", "pa") if name == "finalizeLegacyChunk" else ("pa", "bm")
                final.extend(hook(mod, name) for mod in pair)
            for mod, before, after in (("basemetals", [], first), ("poweradvantage", first, final)):
                dump = self.root / (suffix + "-" + mod + ".class")
                dump.write_bytes(b"test-bytecode")
                self.events.append({"event": "application", "class": class_name,
                                    "transformer": mod + "_legacy_" + suffix, "before": before,
                                    "after": after, "dump": dump.name, "sha256": digest(dump)})

    def prove(self):
        (self.root / "transformer-order-events.jsonl").write_text(
            "\n".join(json.dumps(e) for e in self.events))
        return order_evidence(self.root, "bm-first")

    def test_observed_application_and_reversed_prefix_order_pass(self):
        self.assertEqual("PASS", self.prove()["status"])

    def test_source_registration_alone_is_not_proof(self):
        self.events = self.events[:1]
        with self.assertRaisesRegex(AssertionError, "application order"):
            self.prove()

    def test_wrong_application_order_fails(self):
        self.events[1], self.events[2] = self.events[2], self.events[1]
        with self.assertRaisesRegex(AssertionError, "application order"):
            self.prove()

    def test_missing_injection_fails_even_when_callback_returned(self):
        self.events[1]["after"] = []
        with self.assertRaisesRegex(AssertionError, "injection"):
            self.prove()

    def test_wrong_emitted_execution_order_fails(self):
        self.events[2]["after"] = list(reversed(self.events[2]["after"]))
        with self.assertRaisesRegex(AssertionError, "emitted hook order"):
            self.prove()

    def test_duplicate_callback_fails(self):
        self.events.append(copy.deepcopy(self.events[1]))
        with self.assertRaisesRegex(AssertionError, "application order"):
            self.prove()

    def test_changed_bytecode_dump_fails(self):
        (self.root / self.events[1]["dump"]).write_bytes(b"changed")
        with self.assertRaisesRegex(AssertionError, "hash changed"):
            self.prove()

    def test_disconnected_class_nodes_fail(self):
        self.events[2]["before"] = []
        with self.assertRaisesRegex(AssertionError, "same evolving class"):
            self.prove()

    def test_wrong_forced_request_fails(self):
        self.events[0]["forced"] = list(reversed(self.events[0]["forced"]))
        with self.assertRaisesRegex(AssertionError, "application order"):
            self.prove()

    def test_duplicate_prefix_injection_fails(self):
        self.events[1]["after"].append(copy.deepcopy(self.events[1]["after"][0]))
        with self.assertRaisesRegex(AssertionError, "injection"):
            self.prove()


class FixtureTests(unittest.TestCase):
    def test_exact_comparison_detects_extra_fields(self):
        self.assertEqual("FAIL", strict_equal({"count": 1}, {"count": 1, "extra": 2})["status"])

    def test_damaged_item_keeps_typed_legacy_data(self):
        item = metal_item(26)
        self.assertIsInstance(item["Damage"], nbtlib.Short)
        self.assertEqual(17, int(item["Damage"]))
        self.assertEqual(26, int(item["Slot"]))
        self.assertEqual("keep-exactly", str(item["tag"]["OrderingWitness"]))

    def test_region_edits_only_requested_chunk_and_recovers_high_numeric_ids(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "region").mkdir()
            nbtlib.File({"FML": nbtlib.Compound({"Registries": nbtlib.Compound({
                "minecraft:blocks": nbtlib.Compound({"ids": nbtlib.List[nbtlib.Compound]([
                    nbtlib.Compound({"K": nbtlib.String(name), "V": nbtlib.Int(304 + i)})
                    for i, name in enumerate(METAL_BLOCKS)])})})})}).save(root / "level.dat", gzipped=True)
            chunk = nbtlib.File({"Level": nbtlib.Compound({"Sections": nbtlib.List[nbtlib.Compound]([
                nbtlib.Compound({"Y": nbtlib.Byte(5), "Blocks": nbtlib.ByteArray([0] * 4096),
                                 "Data": nbtlib.ByteArray([0] * 2048)})])})})
            stream = io.BytesIO()
            chunk.write(stream)
            body = b"\x02" + zlib.compress(stream.getvalue())
            payload = struct.pack(">I", len(body)) + body
            header = bytearray(4096)
            header[11 * 4:11 * 4 + 4] = (2 * 256 + 1).to_bytes(4, "big")
            header[12 * 4:12 * 4 + 4] = (3 * 256 + 1).to_bytes(4, "big")
            path = root / "region/r.0.0.mca"
            padded = payload + b"\x00" * (4096 - len(payload))
            path.write_bytes(header + b"\x00" * 4096 + padded + padded)
            _, original = read_region(path)
            definition = []
            add_metal_blocks(root, definition)
            _, updated = read_region(path)
            self.assertEqual(original[12], updated[12])
            self.assertNotEqual(original[11], updated[11])
            fixed = nbtlib.File.parse(io.BytesIO(zlib.decompress(updated[11][5:])))
            section = fixed["Level"]["Sections"][0]
            for i in range(3):
                index = (((81 + i) & 15) << 8) | (8 << 4) | 4
                numeric = (int(section["Blocks"][index]) & 255) | (
                    ((int(section["Add"][index // 2]) & 255) >> ((index & 1) * 4) & 15) << 8)
                self.assertEqual(304 + i, numeric)
            with self.assertRaisesRegex(AssertionError, "occupied"):
                add_metal_blocks(root, [])

    def test_patch_audit_rejects_any_other_changed_library_entry(self):
        with tempfile.TemporaryDirectory() as temp:
            before, after = Path(temp) / "before.jar", Path(temp) / "after.jar"
            with zipfile.ZipFile(before, "w") as archive:
                archive.writestr(ENGINE, b"old")
                archive.writestr("unrelated", b"original")
            with zipfile.ZipFile(after, "w") as archive:
                archive.writestr(ENGINE, b"new")
                archive.writestr("unrelated", b"changed")
                archive.writestr("qualification/order/TransformerOrderBridge.class", b"class")
                archive.writestr("qualification/order/TransformerOrderBridge$Observed.class", b"class")
            with self.assertRaisesRegex(AssertionError, "Unexpected coremods patch"):
                validate_patch(before, after)

    def test_original_byte_custody_passes_but_reports_intermediate_power_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture, runtime = Path(temp) / "fixture", Path(temp) / "runtime"
            (fixture / WORLD / "playerdata").mkdir(parents=True)
            (runtime / WORLD / "playerdata").mkdir(parents=True)
            write_json(runtime / "actor-fixture.json", {"playerUuid": "test"})
            for relative in (Path("level.dat"), Path("playerdata/test.dat")):
                (fixture / WORLD / relative).write_bytes(b"original")
                (runtime / WORLD / relative.with_name(relative.name + ".basemetals-legacy-backup")).write_bytes(b"original")
                (runtime / WORLD / relative.with_name(relative.name + ".poweradvantage-legacy-backup")).write_bytes(b"intermediate")
            proof = backup_provenance(fixture, runtime)
            self.assertEqual("PASS", proof["status"])
            self.assertFalse(proof["power_backup_matches_original"])
            (runtime / WORLD / "level.dat.basemetals-legacy-backup").write_bytes(b"lost-original")
            self.assertEqual("FAIL", backup_provenance(fixture, runtime)["status"])


if __name__ == "__main__":
    unittest.main()
