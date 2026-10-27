"""Worldless exact-stage inventory controls using isolated temporary files only."""

import copy
import hashlib
import json
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import native116_mod_inventory as inventory


class ModInventoryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="native116 inventory spaces ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.runtime = self.root / "Closed Runtime With Spaces"
        self.mods = self.runtime / "mods"
        self.mods.mkdir(parents=True)
        self.sources = self.root / "Sealed Input Jars"
        self.sources.mkdir()
        self.manifest = self.root / "sealed-manifest.json"
        self.ledger = self.runtime / "input-jars.json"
        self.rows, self.jars = [], []
        for identity in sorted(inventory.EXPECTED_IDS):
            name = identity + "-1.0.0.116051.jar"
            source = self.sources / name
            data = ("test jar payload: " + identity).encode("ascii")
            source.write_bytes(data)
            (self.mods / name).write_bytes(data)
            checksum = hashlib.sha256(data).hexdigest().upper()
            self.rows.append(dict(file=name, source=str(source), sha256=checksum))
            self.jars.append(dict(id=identity, version="1.0.0.116051", path=str(source), sha256=checksum))
        self.document = {"target116": {"jars": self.jars}}
        self.write_inputs()

    def write_inputs(self):
        self.manifest.write_text(json.dumps(self.document), encoding="utf-8")
        self.ledger.write_text(json.dumps(self.rows), encoding="utf-8")
        self.pin = hashlib.sha256(self.manifest.read_bytes()).hexdigest().upper()

    def validate(self):
        return inventory.validate_mod_inventory(self.runtime, self.manifest, self.pin)

    def reject(self):
        with self.assertRaises(inventory.InventoryError):
            self.validate()

    def test_exact_actual_native_stage_schema_and_six_mod_set_pass(self):
        result = self.validate()
        self.assertEqual("PASS", result["status"])
        self.assertEqual(inventory.EXPECTED_IDS, {row["id"] for row in result["jars"]})
        self.assertEqual(6, len(result["jars"]))
        self.assertFalse(result["atomicSnapshot"])
        self.assertEqual("EXACT_SIX_MOD_INVENTORY_ONLY", result["scope"])

    def test_missing_manifest_sha_argument_is_not_inferred_from_receipts(self):
        for pin in (None, "", "A" * 63, "G" * 64, "A" * 64 + " "):
            with self.subTest(pin=pin), self.assertRaises(inventory.InventoryError):
                inventory.validate_mod_inventory(self.runtime, self.manifest, pin)

    def test_manifest_byte_tampering_fails_even_when_semantics_unchanged(self):
        self.manifest.write_bytes(self.manifest.read_bytes() + b"\n")
        self.reject()

    def test_lowercase_hex_pins_are_accepted_but_values_remain_exact(self):
        self.assertEqual("PASS", inventory.validate_mod_inventory(self.runtime, self.manifest, self.pin.lower())["status"])

    def test_extra_jar_closes_original_receipt_only_false_positive(self):
        (self.mods / "unapproved-mod.jar").write_bytes(b"unlisted")
        self.assertTrue(all(hashlib.sha256((self.mods / row["file"]).read_bytes()).hexdigest().upper() == row["sha256"]
                            for row in self.rows))
        self.reject()

    def test_empty_ledger_does_not_vacuously_pass(self):
        self.rows.clear()
        self.write_inputs()
        self.reject()

    def test_missing_physical_jar_fails(self):
        (self.mods / self.rows[0]["file"]).unlink()
        self.reject()

    def test_missing_manifest_ledger_or_mods_directory_fails(self):
        for path in (self.manifest, self.ledger):
            contents = path.read_bytes()
            path.unlink()
            self.reject()
            path.write_bytes(contents)
        self.mods.rename(self.runtime / "unavailable-mods")
        self.reject()

    def test_altered_jar_fails_against_manifest_not_ledger(self):
        target = self.mods / self.rows[0]["file"]
        target.write_bytes(b"altered")
        self.rows[0]["sha256"] = hashlib.sha256(b"altered").hexdigest().upper()
        self.write_inputs()
        self.reject()

    def test_altered_physical_jar_with_unchanged_ledger_fails(self):
        (self.mods / self.rows[0]["file"]).write_bytes(b"altered")
        self.reject()

    def test_changed_sealed_source_jar_is_rejected(self):
        Path(self.jars[0]["path"]).write_bytes(b"altered sealed input")
        self.reject()

    def test_missing_sealed_source_jar_is_rejected(self):
        Path(self.jars[0]["path"]).unlink()
        self.reject()

    def test_missing_ledger_entry_and_extra_entry_fail(self):
        original = copy.deepcopy(self.rows)
        for rows in (original[:-1], original + [dict(file="extra.jar", source=str(self.sources / "extra.jar"), sha256="A" * 64)]):
            self.rows = rows
            self.write_inputs()
            self.reject()

    def test_duplicate_and_case_colliding_ledger_names_fail(self):
        for case_change in (False, True):
            self.rows[1] = copy.deepcopy(self.rows[0])
            if case_change:
                self.rows[1]["file"] = self.rows[1]["file"].upper().replace(".JAR", ".jar")
            self.write_inputs()
            self.reject()

    def test_ledger_source_must_equal_manifest_source_not_only_jar_hash(self):
        self.rows[0]["source"] = str(self.sources / "different.jar")
        self.write_inputs()
        self.reject()

    def test_path_traversal_absolute_ads_and_nonjar_ledger_names_fail(self):
        for name in ("../outside.jar", "sub/inside.jar", "sub\\inside.jar", "C:\\escape.jar", "/escape.jar",
                     "a.jar:stream", "a.txt", "a.JAR", ".hidden.jar", "NUL.jar", "COM1.jar", "a.jar ", "a\0.jar"):
            self.rows[0]["file"] = name
            self.write_inputs()
            with self.subTest(name=name):
                self.reject()

    def test_ledger_relative_source_and_traversal_sources_fail(self):
        for source in ("relative.jar", "D:relative.jar", str(self.sources / ".." / "Sealed Input Jars" / self.rows[0]["file"]),
                       str(self.sources / self.rows[0]["file"]) + ":stream"):
            self.rows[0]["source"] = source
            self.write_inputs()
            self.reject()

    def test_extra_nonjar_file_directory_and_nested_jar_fail(self):
        for name in ("README.txt", ".hidden", "disabled"):
            path = self.mods / name
            if name == "disabled":
                path.mkdir()
                (path / "nested.jar").write_bytes(b"nested")
            else:
                path.write_bytes(b"extra")
            with self.subTest(name=name):
                self.reject()
            if path.is_dir():
                (path / "nested.jar").unlink()
                path.rmdir()
            else:
                path.unlink()

    def test_directory_named_jar_is_not_a_regular_jar(self):
        path = self.mods / self.rows[0]["file"]
        path.unlink()
        path.mkdir()
        self.reject()

    def test_manifest_requires_exact_six_ids_no_optional_subset_or_extra_mod(self):
        original = copy.deepcopy(self.jars)
        for jars in (original[:-1], original + [copy.deepcopy(original[0])],
                     [dict(row, id="unknown") if index == 0 else row for index, row in enumerate(original)]):
            self.document["target116"]["jars"] = jars
            self.write_inputs()
            self.reject()

    def test_duplicate_manifest_ids_are_rejected(self):
        self.jars[1]["id"] = self.jars[0]["id"]
        self.write_inputs()
        self.reject()

    def test_duplicate_or_case_colliding_manifest_filenames_are_rejected(self):
        for case_change in (False, True):
            name = Path(self.jars[0]["path"]).name
            if case_change:
                name = name.upper().replace(".JAR", ".jar")
            self.jars[1]["path"] = str(self.root / "other directory" / name)
            self.write_inputs()
            self.reject()

    def test_manifest_relative_source_is_resolved_like_native_pinned_profile(self):
        for jar in self.jars:
            jar["path"] = str(Path(jar["path"]).relative_to(self.root))
        self.write_inputs()
        self.assertEqual("PASS", self.validate()["status"])

    def test_malformed_manifest_and_ledger_rows_fail_closed(self):
        good_document, good_rows = copy.deepcopy(self.document), copy.deepcopy(self.rows)
        for document in ({}, [], {"target116": []}, {"target116": {"jars": {}}}):
            self.document = document
            self.write_inputs()
            self.reject()
        self.document = good_document
        for row in (None, {}, dict(good_rows[0], extra=1), dict(good_rows[0], sha256=True), dict(good_rows[0], source=None)):
            self.rows = [row] + good_rows[1:]
            self.write_inputs()
            self.reject()

    def test_malformed_manifest_jar_fields_fail_closed(self):
        original = copy.deepcopy(self.jars[0])
        for field, value in (("id", []), ("version", None), ("version", "1.15.2"), ("path", None),
                             ("sha256", "bad"), ("extra", "unrecognized")):
            self.jars[0] = dict(original, **{field: value})
            self.write_inputs()
            with self.subTest(field=field):
                self.reject()

    def test_duplicate_json_keys_are_rejected_in_both_inputs(self):
        encoded = self.manifest.read_text()
        self.manifest.write_text(encoded.replace('"id":', '"id":"duplicate","id":', 1))
        self.pin = hashlib.sha256(self.manifest.read_bytes()).hexdigest().upper()
        self.reject()
        self.write_inputs()
        self.ledger.write_text(self.ledger.read_text().replace('"file":', '"file":"duplicate.jar","file":', 1))
        self.reject()

    def test_nonfinite_json_constants_invalid_encoding_and_truncated_json_fail(self):
        original = self.ledger.read_bytes()
        for raw in (b"[", b"\xff", original.replace(b'"sha256":', b'"sha256":NaN,"unused":', 1)):
            self.ledger.write_bytes(raw)
            self.reject()

    def fake_reparse(self, target, symbolic=False):
        original = Path.lstat

        def metadata(path, *args, **kwargs):
            value = original(path, *args, **kwargs)
            if path == target:
                fields = {name: getattr(value, name) for name in dir(value) if name.startswith("st_")}
                fields["st_file_attributes"] = getattr(value, "st_file_attributes", 0) | inventory._REPARSE
                if symbolic:
                    fields["st_mode"] = stat.S_IFLNK | 0o777
                    fields["st_file_attributes"] &= ~inventory._REPARSE
                return SimpleNamespace(**fields)
            return value

        return patch.object(Path, "lstat", metadata)

    def test_reparse_file_mod_directory_ledger_manifest_source_and_ancestors_fail(self):
        targets = (self.mods / self.rows[0]["file"], self.mods, self.ledger,
                   self.manifest, Path(self.jars[0]["path"]), self.runtime, self.root)
        for target in targets:
            with self.subTest(target=target), self.fake_reparse(target):
                self.reject()

    def test_symbolic_link_mode_is_rejected_even_without_platform_reparse_flag(self):
        target = self.mods / self.rows[0]["file"]
        with self.fake_reparse(target, symbolic=True):
            self.reject()

    def test_physical_case_collision_is_rejected_on_all_host_filesystems(self):
        entries = list(self.mods.iterdir())
        collision = entries[0].with_name(entries[0].name.upper().replace(".JAR", ".jar"))
        with patch.object(Path, "iterdir", return_value=iter(entries + [collision])), \
             patch.object(inventory, "_plain_path"):
            with self.assertRaisesRegex(inventory.InventoryError, "case-colliding physical"):
                inventory._physical(self.mods)

    def test_windows_path_handle_ctime_difference_is_not_a_content_change(self):
        original = inventory.os.fstat

        def handle_metadata(descriptor):
            metadata = original(descriptor)
            fields = {name: getattr(metadata, name) for name in dir(metadata) if name.startswith("st_")}
            fields["st_ctime_ns"] += 1
            return SimpleNamespace(**fields)

        with patch.object(inventory.os, "fstat", side_effect=handle_metadata):
            self.assertEqual("PASS", self.validate()["status"])

    def test_changed_handle_metadata_during_hash_read_is_rejected(self):
        original = inventory.os.fstat
        calls = 0

        def handle_metadata(descriptor):
            nonlocal calls
            calls += 1
            metadata = original(descriptor)
            fields = {name: getattr(metadata, name) for name in dir(metadata) if name.startswith("st_")}
            fields["st_ctime_ns"] += calls
            return SimpleNamespace(**fields)

        with patch.object(inventory.os, "fstat", side_effect=handle_metadata):
            self.reject()

    def test_manifest_or_ledger_change_during_validation_is_rejected(self):
        original = inventory._read
        for target in (self.manifest, self.ledger):
            calls = 0

            def changed(path, retain=False):
                nonlocal calls
                result = original(path, retain)
                if path == target:
                    calls += 1
                    if calls > 1:
                        return "0" * 64, result[1], result[2]
                return result

            with self.subTest(target=target), patch.object(inventory, "_read", side_effect=changed):
                self.reject()

    def test_added_physical_entry_before_final_recheck_is_rejected(self):
        original = inventory._physical
        calls = 0

        def changed(mods):
            nonlocal calls
            calls += 1
            return original(mods) | ({"late.jar"} if calls > 1 else set())

        with patch.object(inventory, "_physical", side_effect=changed):
            self.reject()

    def test_successful_validation_does_not_modify_any_file_or_return_mutable_inputs(self):
        before = {str(path): path.read_bytes() for path in self.root.rglob("*") if path.is_file()}
        first = self.validate()
        first["jars"][0]["sha256"] = "changed result only"
        self.assertEqual("PASS", self.validate()["status"])
        self.assertEqual(before, {str(path): path.read_bytes() for path in self.root.rglob("*") if path.is_file()})


if __name__ == "__main__":
    unittest.main()
