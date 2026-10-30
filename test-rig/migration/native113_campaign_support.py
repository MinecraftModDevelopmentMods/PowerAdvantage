"""Explicit native destination and archived-source transport for wider 1.13 campaigns."""

from argparse import Namespace
from pathlib import Path

from matrix import WORLD, digest, fingerprint, normalized as native_normalized, selected_profiles, compare as historical_compare
from native113 import LAUNCHER, output_path, require_disjoint, pinned_profile as native_target
from native113_lifecycle import prepare, read_json, require_native_boot, verify_staged
from native113_diagnostics import audit_lines


def pinned_profile(manifest, key):
    manifest = manifest.resolve()
    entry = read_json(manifest)[key]
    if entry.get("qualificationStatus") is not None:
        return native_target(manifest, key)
    # Historical source profiles keep their original hashes and are never booted here.
    return selected_profiles(Namespace(profile_manifest=manifest))[key]


def require_clean_log(path):
    return audit_lines(Path(path).read_text(encoding="utf-8", errors="replace").splitlines())


def compare(before, after, normalizer=None):
    if normalizer is not None and normalizer is not native_normalized:
        raise ValueError("The 1.13 comparison must retain its original normalizer")
    return historical_compare(before, after)


def verify_source(args):
    source = args.source.resolve()
    before = fingerprint(source)
    expected = {key.replace("\\", "/"): value.upper() for key, value in read_json(args.source_hashes).items()}
    actual = {key.replace("\\", "/"): value.upper() for key, value in fingerprint(source / WORLD).items()}
    if not expected or actual != expected:
        raise AssertionError("Protected retained source world fingerprint mismatch")
    profile = pinned_profile(args.source_manifest.resolve(), args.source_key)
    if profile[1] != LAUNCHER:
        raise ValueError("Supply the retained, already-converted native 1.13 source")
    pins = sorted((jar.name, digest(jar)) for jar in profile[2])
    recorded = sorted((row["file"], row["sha256"].upper()) for row in read_json(source / "input-jars.json"))
    if recorded != pins or sorted((jar.name, digest(jar)) for jar in (source / "mods").glob("*.jar")) != pins:
        raise AssertionError("Source mod bytes do not match the explicit archived profile")
    history = read_json(source / "state-comparison.json")
    if any(history.get(gate, {}).get("status") != "PASS" for gate in ("conversion", "restart", "idempotence")):
        raise AssertionError("Retained source lacks a passing preservation history")
    for name in ("migration-fixture.json", "migration-restarted.json"):
        if not (source / name).is_file(): raise FileNotFoundError(source / name)
    if fingerprint(source) != before: raise AssertionError("Source changed during preflight")
    return before, history
