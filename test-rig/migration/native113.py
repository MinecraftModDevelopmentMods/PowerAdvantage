"""Strict native Forge 25 inputs for the expanded operating checks; never opens source worlds."""

import json
from pathlib import Path
import tomllib
import zipfile
from matrix import WORLD, digest, stage, write_json

RIG = Path(__file__).resolve().parents[1]
LAUNCHER = "forge-1.13.2-25.0.223.jar"


def output_path(path):
    path = path.resolve()
    if not path.is_relative_to((RIG / "build").resolve()):
        raise ValueError("Native113 outputs must remain under test-rig/build")
    if path.exists(): raise FileExistsError(path)
    return path


def require_disjoint(output, protected):
    output, protected = output.resolve(), protected.resolve()
    if output.is_relative_to(protected) or protected.is_relative_to(output):
        raise ValueError("Output overlaps a protected input: " + str(protected))


def identity(path):
    with zipfile.ZipFile(path) as archive:
        document = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8"))
    mods = document.get("mods", [])
    if len(mods) != 1: raise ValueError("Expected one declared mod per supplied jar")
    return mods[0]["modId"], mods[0]["version"]


def import_existing(source, output):
    """Retain approved jar digests; add explicit identities and a current seed-code seal."""
    from native113_code import code_files
    source, output = source.resolve(), output.resolve()
    require_disjoint(output, source)
    if output.exists(): raise FileExistsError(output)
    document = json.loads(source.read_text(encoding="utf-8"))
    target = document["target113"]
    for jar in target["jars"]:
        path = (source.parent / jar["path"]).resolve()
        if digest(path) != jar["sha256"].upper(): raise AssertionError("Archived source jar changed")
        mod, version = identity(path)
        jar.update(path=str(path), id=mod, version=version)
    seed = (source.parent / target["seed"]).resolve()
    target.update(seed=str(seed), launcher_sha256=digest(seed / LAUNCHER),
                  serverCode={"files": code_files(seed)}, qualificationStatus="UNQUALIFIED_EXPANDED_COVERAGE")
    write_json(output, dict(target113=target, baseManifest=str(source), baseSha256=digest(source)))
    pinned_profile(output, "target113")


def pinned_profile(manifest_path, key):
    manifest_path = manifest_path.resolve()
    target = json.loads(manifest_path.read_text(encoding="utf-8"))[key]
    seed = (manifest_path.parent / target["seed"]).resolve()
    jars, ids = [], set()
    for jar in target["jars"]:
        path = (manifest_path.parent / jar["path"]).resolve()
        if digest(path) != jar["sha256"].upper(): raise AssertionError("Pinned jar changed: " + str(path))
        actual = identity(path)
        if actual != (jar["id"], jar["version"]) or not jar["version"].endswith(".113021"):
            raise ValueError("Wrong native mod identity/version: " + str(path))
        if jar["id"] in ids: raise ValueError("Duplicate mod identity")
        ids.add(jar["id"]); jars.append(path)
    if key != "target113" or target["launcher"] != LAUNCHER or not {"poweradvantage", "advantageworkstest", "orespawn"} <= ids:
        raise ValueError("Incomplete/wrong native113 input profile")
    if digest(seed / LAUNCHER) != target["launcher_sha256"].upper() or not (seed / "libraries").is_dir():
        raise AssertionError("Native Forge 25 seed changed")
    return seed, LAUNCHER, jars
