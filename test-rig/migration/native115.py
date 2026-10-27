"""Separate native115 destination pinning and frozen-copy migration witness runner.

Never builds or launches a source. Historical matrix/connected/actor campaigns remain
separate and their failures are not reclassified by this destination-only runner.
"""

import argparse
import json
from pathlib import Path
import re
import tomllib
import zipfile

from matrix import WORLD, compare, copy_fixture, digest, fingerprint, normalized, stage, start_snapshot, write_json


RIG = Path(__file__).resolve().parents[1]
LEGACY_POWER = RIG.parents[2] / "PA113/PowerAdvantage"
LAUNCHER = "forge-1.15.2-31.2.57.jar"


def native_normalized(value, enchantments=None):
    result = normalized(value, enchantments)
    # Native 1.14 LockCode.write omits an empty lock. Give only vanilla chest
    # roots their canonical unlocked value, retaining strict checks on real locks.
    if isinstance(result, dict) and result.get("id") == "minecraft:chest":
        result.setdefault("Lock", "")
    return result


def require_clean_log(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    failures = [line for line in text.splitlines() if "/ERROR]" in line or any(marker in line for marker in (
        "NoSuchFieldError", "NoSuchMethodError", "Failed to reload data packs", "Encountered an unexpected exception"))]
    if failures:
        raise AssertionError(f"Native runtime errors in {path}: {failures[:5]}")


def output_path(path):
    path = path.resolve()
    if not path.is_relative_to((RIG / "build").resolve()):
        raise ValueError("Native115 outputs must be new paths under test-rig/build")
    if path.exists():
        raise FileExistsError(path)
    return path


def require_disjoint(output, protected):
    output, protected = output.resolve(), protected.resolve()
    if output.is_relative_to(protected) or protected.is_relative_to(output):
        raise ValueError(f"Output overlaps a protected input: {protected}")


def jar_identity(path, mod_id, version):
    with zipfile.ZipFile(path) as archive:
        text = archive.read("META-INF/mods.toml").decode("utf-8")
    for fields in tomllib.loads(text).get("mods", []):
        if fields.get("modId") == mod_id and fields.get("version") == version:
            return
    raise ValueError(f"Wrong native mod identity/version: {path}")


def pin_target(profile_path, seed, output):
    output = output_path(output)
    seed = seed.resolve()
    require_disjoint(output, seed)
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    if (profile.get("minecraftVersion") != "1.15.2" or profile.get("mode") != "packaged"
            or profile.get("portStatus") != "PORTED_UNQUALIFIED"
            or profile.get("forge", {}).get("version") != "1.15.2-31.2.57"):
        raise ValueError("A ported native115 packaged destination profile is required")
    if not (seed / LAUNCHER).is_file() or not (seed / "libraries").is_dir():
        raise FileNotFoundError("Supply an installed native Forge 31.2.57 server seed")
    jars = []
    for mod in profile["mods"]:
        if not re.fullmatch(r"[A-Fa-f0-9]{64}", mod.get("sha256") or ""):
            raise ValueError("Pending native115 artifact pin: " + mod["id"])
        path = (RIG / mod["path"]).resolve()
        require_disjoint(output, path)
        if not mod["version"].endswith(".115021") or path.name != mod["file"] or "deobf" in path.name:
            raise ValueError(f"Not a native115 packaged input: {path}")
        jar_identity(path, mod["id"], mod["version"])
        actual = digest(path)
        if mod.get("sha256") and actual != mod["sha256"].upper():
            raise AssertionError(f"Destination jar hash mismatch: {path}")
        entry = {"path": str(path), "sha256": actual, "id": mod["id"], "version": mod["version"]}
        for field in ("originPath", "sourceRef"):
            if field in mod:
                entry[field] = mod[field]
        jars.append(entry)
    if not {"poweradvantage", "advantageworkstest", "orespawn"}.issubset({j["id"] for j in jars}):
        raise ValueError("Missing native Power, OreSpawn or harness input")
    write_json(output, {"target115": {
        "seed": str(seed), "launcher": LAUNCHER, "launcher_sha256": digest(seed / LAUNCHER),
        "jars": jars, "qualificationStatus": "UNQUALIFIED",
        "profile": str(profile_path.resolve()), "profileSha256": digest(profile_path),
        "legacySourceRoot": str(LEGACY_POWER),
    }})


def pinned_profile(manifest_path, key):
    entry = json.loads(manifest_path.read_text(encoding="utf-8"))[key]
    def resolve(value):
        return (manifest_path.parent / value).resolve()
    jars = []
    for jar in entry["jars"]:
        path = resolve(jar["path"])
        if digest(path) != jar["sha256"].upper():
            raise AssertionError(f"Pinned input changed: {path}")
        if key == "target115":
            if not jar.get("version", "").endswith(".115021"):
                raise ValueError("Destination manifest contains a non-native input")
            jar_identity(path, jar["id"], jar["version"])
        jars.append(path)
    seed = resolve(entry["seed"])
    if key == "target115":
        if entry["launcher"] != LAUNCHER or digest(seed / LAUNCHER) != entry["launcher_sha256"].upper():
            raise AssertionError("Native115 server launcher changed")
        identities = [jar["id"] for jar in entry["jars"]]
        if len(set(identities)) != len(identities) or not {"poweradvantage", "advantageworkstest", "orespawn"}.issubset(identities):
            raise ValueError("Destination must contain unique native Power, OreSpawn and harness inputs")
        if not (seed / "libraries").is_dir():
            raise FileNotFoundError("Destination server libraries are missing")
    return seed, entry["launcher"], jars


def run(args):
    source = args.source.resolve()
    output = output_path(args.output)
    for protected in (source, args.source_manifest, args.target_manifest):
        require_disjoint(output, protected)
    if not re.fullmatch(r"migration-[A-Za-z0-9_-]+\.json", args.source_snapshot):
        raise ValueError("Source snapshot must name an existing snapshot inside the source runtime")
    # Explicit archived source manifests resolve against their original PA113 location,
    # never against matrix.py's copied PA114 convenience defaults.
    pinned_profile(args.source_manifest.resolve(), args.source_key)
    target = pinned_profile(args.target_manifest.resolve(), "target115")
    require_disjoint(output, target[0])
    before = fingerprint(source)
    expected = json.loads(args.source_hashes.read_text(encoding="utf-8"))
    if fingerprint(source / WORLD) != expected:
        raise AssertionError("Immutable source world does not match its approved fingerprint")
    snapshot = source / args.source_snapshot
    if not snapshot.is_file() or not (source / "migration-fixture.json").is_file():
        raise FileNotFoundError("Missing retained source snapshot or fixture specification")
    try:
        launcher = stage(output, target)
        copy_fixture(source, output)
        results = {}
        for label in ("converted", "restarted"):
            start_snapshot(output, args.java8, launcher, label)
            require_clean_log(output / f"console-{label}.log")
            results[label] = compare(snapshot, output / f"migration-{label}.json", native_normalized)
        results["idempotence"] = compare(output / "migration-converted.json", output / "migration-restarted.json", native_normalized)
        write_json(output / "state-comparison.json", results)
        write_json(output / "native115-provenance.json", {
            "source": str(source), "sourceManifest": str(args.source_manifest.resolve()),
            "sourceManifestSha256": digest(args.source_manifest), "sourceKey": args.source_key,
            "sourceSnapshotSha256": digest(snapshot), "sourceHashes": expected,
            "targetManifest": str(args.target_manifest.resolve()), "targetManifestSha256": digest(args.target_manifest),
            "scope": "frozen-copy-state-and-restart-only",
            "platformNormalization": "Only vanilla chest roots treat omitted Lock as the canonical empty string; nonempty locks remain strict.",
        })
        return int(any(result["status"] != "PASS" for result in results.values()))
    finally:
        if fingerprint(source) != before:
            raise AssertionError("Protected source runtime changed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pin = commands.add_parser("pin-target", help="Pin actual destination jars and an installed server, without opening worlds")
    pin.add_argument("--profile", type=Path, default=RIG / "profiles/native115/packaged-current.json")
    pin.add_argument("--server-seed", type=Path, required=True)
    pin.add_argument("--output", type=Path, required=True)
    launch = commands.add_parser("run", help="Convert only a fingerprinted copy; never construct or operate machinery")
    launch.add_argument("--source", type=Path, required=True)
    launch.add_argument("--source-manifest", type=Path, required=True)
    launch.add_argument("--source-key", required=True)
    launch.add_argument("--source-hashes", type=Path, required=True)
    launch.add_argument("--source-snapshot", default="migration-source.json")
    launch.add_argument("--target-manifest", type=Path, required=True)
    launch.add_argument("--java8", type=Path, required=True)
    launch.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "pin-target":
        pin_target(args.profile, args.server_seed, args.output)
        return 0
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
