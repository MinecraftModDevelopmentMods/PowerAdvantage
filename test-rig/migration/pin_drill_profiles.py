"""Archive pre-fix artifacts or pin explicit repaired legacy runtime profiles."""
import argparse
import copy
import json
from pathlib import Path
import re
import shutil

from matrix import POWER, digest, write_json

WORKSPACE = POWER.parents[1]
REPOS = {version + "-" + mod: WORKSPACE / ("PA" + version) / mod / mod
         for version in ("110", "112") for mod in ("SteamAdvantage", "ElectricAdvantage")}


def archive(path, output):
    sha = digest(path)
    target = output / "artifacts" / sha / path.name
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        shutil.copy2(path, target)
    if digest(target) != sha:
        raise AssertionError("Artifact changed during archival")
    return dict(path=str(target.resolve()), sha256=sha)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    if args.mode == "archive":
        saved = []
        for name, repo in REPOS.items():
            for folder in ("build/libs", "build/libs-dev", "build/test-results/test"):
                for path in (repo / folder).glob("*"):
                    if path.is_file() and path.suffix in (".jar", ".xml"):
                        saved.append(dict(repo=name, source=str(path), artifact=archive(path, args.output)))
        write_json(args.output / "before-fix-artifacts.json", saved)
        print("Archived", len(saved), "pre-fix artifacts and test records")
        return
    if args.mode == "workspace-profiles":
        changes = []
        for path in (WORKSPACE / "PA110/PowerAdvantage/test-rig/profiles").glob("*.json"):
            profile = json.loads(path.read_text())
            previous = digest(path)
            updated = []
            for mod in profile["mods"]:
                if mod["id"] not in ("steamadvantage", "electricadvantage") or mod["version"] != "2.2.4.110021":
                    continue
                name = "SteamAdvantage" if mod["id"] == "steamadvantage" else "ElectricAdvantage"
                folder = "libs-dev" if mod["file"].endswith("-deobf.jar") else "libs"
                for key in ("version", "file", "path"):
                    mod[key] = mod[key].replace("2.2.4.110021", "2.2.5.110021")
                if "sha256" in mod:
                    mod["sha256"] = digest(REPOS["110-" + name] / "build" / folder / mod["file"])
                updated.append(mod["id"])
            if updated:
                path.write_text(json.dumps(profile, indent=2) + "\n", encoding="utf-8")
                changes.append(dict(path=str(path), before=previous, after=digest(path), mods=updated))
        write_json(args.output / "workspace-profile-updates.json", changes)
        print("Updated", len(changes), "mutable 1.10 profiles; archived migration profiles untouched")
        return
    profiles = json.loads(args.base.read_text())
    before = copy.deepcopy(profiles)
    profiles["native110"] = copy.deepcopy(profiles["maintained110"])
    replacements = []
    for version, profile_name in (("110", "native110"), ("112", "bridge112")):
        for mod in ("SteamAdvantage", "ElectricAdvantage"):
            jars = [p for p in (REPOS[version + "-" + mod] / "build/libs").glob(mod + "-*.jar")
                    if re.fullmatch(mod + r"-\d+\.\d+\.\d+\.\d+\.jar", p.name)]
            if len(jars) != 1:
                raise AssertionError("Build exactly one repaired release first: " + mod + version)
            for index, jar in enumerate(profiles[profile_name]["jars"]):
                if Path(jar["path"]).name.startswith(mod + "-"):
                    replacement = archive(jars[0], args.output)
                    replacements.append(dict(profile=profile_name, previous=jar, replacement=replacement))
                    profiles[profile_name]["jars"][index] = replacement
                    break
            else:
                raise AssertionError("Missing original mod in profile")
    for profile in profiles.values():
        for index, jar in enumerate(profile["jars"]):
            path = Path(jar["path"])
            if digest(path) != jar["sha256"]:
                raise AssertionError("Changed pinned input")
            profile["jars"][index] = archive(path, args.output)
    for name in ("original110", "early110", "maintained110", "legacy112", "maintained112", "target113"):
        if [j["sha256"] for j in before[name]["jars"]] != [j["sha256"] for j in profiles[name]["jars"]]:
            raise AssertionError("Historical source or 1.13 target changed")
    write_json(args.output / "profiles.json", profiles)
    write_json(args.output / "replacement-provenance.json", dict(base=str(args.base.resolve()),
               base_sha256=digest(args.base), replacements=replacements,
               historical_sources_and_target113="UNCHANGED"))
    print("Pinned four repaired artifacts; historical sources and 1.13 target unchanged")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("archive", "candidate", "workspace-profiles"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base", type=Path, default=POWER / "run-connected-migration/20261023-working-legacy110/profiles.json")
    run(parser.parse_args())
