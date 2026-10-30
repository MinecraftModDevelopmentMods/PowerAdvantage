"""Read-only six-mod stage inventory validation, separate from frozen auditors.

The caller must supply an independently pinned manifest SHA256 and keep the
runtime closed and unchanged. Rechecks detect observed changes; this is NOT an
atomic filesystem snapshot, server-exit proof, or gameplay qualification.
"""

import hashlib
import json
import ntpath
import os
from pathlib import Path
import re
import stat


EXPECTED_IDS = frozenset(("advantageworkstest", "poweradvantage", "steamadvantage",
                          "electricadvantage", "basemetals", "orespawn"))
_REPARSE = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)


class InventoryError(ValueError):
    pass


def _sha(value):
    if not isinstance(value, str) or re.fullmatch(r"[a-fA-F0-9]{64}", value) is None:
        raise InventoryError("Explicit SHA256 pin required")
    return value.upper()


def _path(value, parent=None):
    raw = os.fspath(value)
    if not isinstance(raw, str) or not raw or "\0" in raw or ".." in raw.replace("\\", "/").split("/"):
        raise InventoryError("Unsafe inventory path")
    path = Path(raw)
    drive, tail = ntpath.splitdrive(raw)
    if drive and not path.is_absolute():
        raise InventoryError("Drive-relative inventory path")
    if ":" in tail:
        raise InventoryError("Alternate stream/device path")
    if not path.is_absolute():
        if parent is None:
            raise InventoryError("Absolute inventory path required")
        path = parent / path
    return Path(os.path.abspath(path))


def _plain_path(path, directory=False):
    for ancestor in (*reversed(path.parents), path):
        metadata = ancestor.lstat()
        if stat.S_ISLNK(metadata.st_mode) or getattr(metadata, "st_file_attributes", 0) & _REPARSE:
            raise InventoryError("Link/reparse inventory path: " + str(ancestor))
    metadata = path.lstat()
    valid = stat.S_ISDIR(metadata.st_mode) if directory else stat.S_ISREG(metadata.st_mode)
    if not valid:
        raise InventoryError("Wrong inventory entry type: " + str(path))
    return metadata


def _stamp(metadata):
    return (metadata.st_dev, metadata.st_ino, metadata.st_size,
            metadata.st_mtime_ns, metadata.st_ctime_ns)


def _path_stamp(metadata):
    # Windows path and handle stat can disagree on ctime; compare it handle-to-handle.
    return (metadata.st_dev, metadata.st_ino, metadata.st_size, metadata.st_mtime_ns)


def _read(path, retain=False):
    before = _plain_path(path)
    digest, chunks = hashlib.sha256(), []
    with path.open("rb") as stream:
        opened = os.fstat(stream.fileno())
        if _path_stamp(before) != _path_stamp(opened):
            raise InventoryError("Inventory file changed before read: " + str(path))
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            if retain:
                chunks.append(chunk)
        after = os.fstat(stream.fileno())
    if _stamp(opened) != _stamp(after) or _path_stamp(opened) != _path_stamp(_plain_path(path)):
        raise InventoryError("Inventory file changed during read: " + str(path))
    return digest.hexdigest().upper(), b"".join(chunks), before.st_size


def _object(pairs):
    value = {}
    for key, child in pairs:
        if key in value:
            raise InventoryError("Duplicate JSON key: " + key)
        value[key] = child
    return value


def _constant(value):
    raise InventoryError("Non-finite JSON constant: " + value)


def _json(raw):
    try:
        return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=_object, parse_constant=_constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise InventoryError("Invalid inventory JSON") from error


def _name(value):
    if (not isinstance(value, str) or not value or value != value.strip()
            or any(ord(c) < 32 or c in '/\\:<>"|?*' for c in value)
            or not value.endswith(".jar") or value.startswith(".")
            or value.split(".", 1)[0].upper() in {"CON", "PRN", "AUX", "NUL", "CLOCK$"}
            or re.fullmatch(r"(?:COM|LPT)[1-9]", value.split(".", 1)[0], re.I)):
        raise InventoryError("Expected a flat safe jar filename: " + repr(value))
    return value


def _expected(manifest, parent):
    if not isinstance(manifest, dict) or not isinstance(manifest.get("target116"), dict):
        raise InventoryError("Missing sealed target116 manifest")
    rows = manifest["target116"].get("jars")
    if not isinstance(rows, list) or len(rows) != len(EXPECTED_IDS):
        raise InventoryError("Require the complete six-mod native116 target")
    expected, ids, folded = {}, set(), set()
    required = {"id", "version", "path", "sha256"}
    for row in rows:
        if (not isinstance(row, dict) or not required <= set(row)
                or set(row) - required - {"originPath", "sourceRef"}):
            raise InventoryError("Malformed sealed jar record")
        identity = row["id"]
        if not isinstance(identity, str) or identity not in EXPECTED_IDS or identity in ids:
            raise InventoryError("Unknown or duplicate sealed mod identity")
        if not isinstance(row["version"], str) or not row["version"].endswith(".116051"):
            raise InventoryError("Non-native sealed mod version")
        source = _path(row["path"], parent)
        name = _name(source.name)
        if name.casefold() in folded:
            raise InventoryError("Duplicate/case-colliding sealed jar filename")
        ids.add(identity)
        folded.add(name.casefold())
        expected[name] = dict(id=identity, source=source, sha256=_sha(row["sha256"]))
    if ids != EXPECTED_IDS:
        raise InventoryError("Incomplete sealed six-mod identity set")
    return expected


def _physical(mods):
    _plain_path(mods, directory=True)
    names, folded = set(), set()
    for entry in mods.iterdir():
        name = _name(entry.name)
        _plain_path(entry)
        if name.casefold() in folded:
            raise InventoryError("Duplicate/case-colliding physical mod filename")
        names.add(name)
        folded.add(name.casefold())
    return names


def _validate(runtime, manifest_path, manifest_sha256):
    runtime, manifest_path = _path(runtime), _path(manifest_path)
    _plain_path(runtime, directory=True)
    manifest_hash, raw_manifest, _ = _read(manifest_path, retain=True)
    if manifest_hash != _sha(manifest_sha256):
        raise InventoryError("Sealed manifest SHA256 mismatch")
    expected = _expected(_json(raw_manifest), manifest_path.parent)
    ledger_path, mods = runtime / "input-jars.json", runtime / "mods"
    ledger_hash, raw_ledger, _ = _read(ledger_path, retain=True)
    rows = _json(raw_ledger)
    if not isinstance(rows, list) or len(rows) != len(expected):
        raise InventoryError("Ledger does not contain exactly the sealed six jars")
    ledger, folded = {}, set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"file", "source", "sha256"}:
            raise InventoryError("Malformed native stage ledger row")
        name = _name(row["file"])
        if name.casefold() in folded:
            raise InventoryError("Duplicate/case-colliding ledger jar")
        folded.add(name.casefold())
        if name not in expected or _sha(row["sha256"]) != expected[name]["sha256"]:
            raise InventoryError("Ledger jar/hash differs from sealed manifest: " + name)
        if _path(row["source"]) != expected[name]["source"]:
            raise InventoryError("Ledger source differs from sealed manifest: " + name)
        ledger[name] = row
    if set(ledger) != set(expected) or _physical(mods) != set(expected):
        raise InventoryError("Physical mods, ledger and sealed manifest jar sets differ")
    observations = []
    for name, pin in sorted(expected.items()):
        source_hash, _, _ = _read(pin["source"])
        actual, _, size = _read(mods / name)
        if source_hash != pin["sha256"] or actual != pin["sha256"]:
            raise InventoryError("Actual jar bytes differ from sealed SHA256: " + name)
        observations.append(dict(file=name, id=pin["id"], sha256=actual, bytes=size))
    if (_physical(mods) != set(expected) or _read(manifest_path)[0] != manifest_hash
            or _read(ledger_path)[0] != ledger_hash):
        raise InventoryError("Inventory inputs changed during validation")
    return dict(status="PASS", scope="EXACT_SIX_MOD_INVENTORY_ONLY", atomicSnapshot=False,
                manifestSha256=manifest_hash, ledgerSha256=ledger_hash, jars=observations)


def validate_mod_inventory(runtime, manifest_path, manifest_sha256):
    """Require sealed sources, ledger and real mods to match; never writes inputs."""
    try:
        return _validate(runtime, manifest_path, manifest_sha256)
    except (OSError, TypeError) as error:
        raise InventoryError("Unreadable or malformed native inventory input") from error
