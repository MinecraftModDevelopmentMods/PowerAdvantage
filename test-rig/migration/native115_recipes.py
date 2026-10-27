"""Native 1.15 recipe exports and progression on new hash-pinned disposable servers.

Historical campaign modules supply assertion data only. No legacy profile, seed,
world, artifact fallback or historical result is used as native runtime evidence.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import socket
import zipfile

import nbtlib

from matrix import WORLD, Server, digest, fingerprint, stage, write_json
from native115 import RIG, output_path, pinned_profile, require_clean_log, require_disjoint
from progression import fixture as progression_fixture, validate_tags
from recipe_unlocks import MATERIALS


MOD_COUNTS = {"poweradvantage": 35, "steamadvantage": 24, "electricadvantage": 50}
RECIPE_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting"}
UNLOCK_BOOTS = (
    ("NORMAL", "normal", ("sweep", "persist")),
    ("NORMAL", "normal-restart", ("restore",)),
    ("TECH_PROGRESSION", "tech", ("restore", "sweep", "persist")),
    ("APOCALYPTIC", "apocalyptic", ("restore", "sweep", "persist")),
    ("NORMAL", "normal-return", ("restore",)),
)
MODES = ("NORMAL", "TECH_PROGRESSION", "APOCALYPTIC")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def recipe_fixture(jars):
    recipes = []
    sources = {}
    for path in jars:
        with zipfile.ZipFile(path) as jar:
            for mod in MOD_COUNTS:
                prefix = f"data/{mod}/recipes/"
                names = sorted(name for name in jar.namelist() if name.startswith(prefix)
                               and name.endswith(".json") and "/" not in name[len(prefix):])
                for name in names:
                    recipe = json.loads(jar.read(name))
                    if recipe["type"] not in RECIPE_TYPES:
                        continue
                    stem = Path(name).stem
                    advancement_name = f"data/{mod}/advancements/recipes/{stem}.json"
                    advancement = json.loads(jar.read(advancement_name))
                    predicates = {}
                    for key, criterion in advancement["criteria"].items():
                        if criterion["trigger"] != "minecraft:inventory_changed":
                            continue
                        items = criterion["conditions"]["items"]
                        if len(items) != 1 or set(items[0]) not in ({"item"}, {"tag"}):
                            raise AssertionError(f"Unsupported inventory predicate: {advancement_name}/{key}")
                        predicates[key] = items[0]
                    if not predicates:
                        raise AssertionError(f"No ingredient-discovery predicates: {advancement_name}")
                    identity = f"{mod}:{stem}"
                    if identity in sources:
                        raise AssertionError(f"Duplicate packaged recipe: {identity}")
                    sources[identity] = {"jar": str(path), "recipe": name, "advancement": advancement_name}
                    recipes.append({"id": identity, "ingredients": predicates})
    counts = Counter(row["id"].split(":")[0] for row in recipes)
    if counts != MOD_COUNTS:
        raise AssertionError(f"Packaged recipe inventory changed: {dict(counts)}; expected {MOD_COUNTS}")
    return {"recipes": recipes, "materials": MATERIALS}, sources


def native_target(manifest):
    entry = read_json(manifest)["target115"]
    required = {"advantageworkstest", "poweradvantage", "steamadvantage", "electricadvantage", "basemetals", "orespawn"}
    if {jar["id"] for jar in entry["jars"]} != required:
        raise ValueError("This campaign requires exactly the native harness and five packaged mods")
    target = pinned_profile(manifest, "target115")
    if any("deobf" in path.name.lower() for path in target[2]):
        raise ValueError("Use packaged native jars, not development candidates")
    return target


def require_free_port(port):
    with socket.socket() as check:
        check.bind(("127.0.0.1", port))


def stage_native(runtime, target, port, campaign):
    require_free_port(port)
    launcher = stage(runtime, target)
    # Replace only the disposable stage's legacy generator and port defaults.
    props = dict(line.split("=", 1) for line in (runtime / "server.properties").read_text().splitlines() if "=" in line)
    props.update({"server-port": str(port), "generator-settings": "", "level-type": "FLAT"})
    (runtime / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in props.items()), encoding="utf-8")
    marker = "advantage-recipes.allow" if campaign == "unlock" else "advantage-progression.allow"
    (runtime / marker).touch()
    if digest(runtime / launcher) != digest(target[0] / launcher):
        raise AssertionError("Staged launcher differs from native input")
    for jar in target[2]:
        if digest(runtime / "mods" / jar.name) != digest(jar):
            raise AssertionError(f"Staged mod differs from pinned input: {jar}")
    if (runtime / WORLD).exists():
        raise AssertionError("Staging must not import an existing world")
    return launcher


def configure(runtime, mode, rubber=True):
    config = runtime / "config/poweradvantage-common.toml"
    config.parent.mkdir(exist_ok=True)
    config.write_text(f'[options]\nrecipeMode = "{mode}"\nplasticEqualsRubber = {str(rubber).lower()}\n', encoding="utf-8")


def validate_unlock(result, definition, operation):
    expected = {row["id"] for row in definition["recipes"]}
    rows = result["cases"]
    if (result["status"] != "PASS" or len(rows) != len(expected)
            or {row["id"] for row in rows} != expected or result["potatoCraftingCases"] != 59):
        raise AssertionError("Incomplete native unlock/potato-crafting report")
    if not result["bookRecipes"]:
        raise AssertionError("Native server exported no recipe-book recipes")
    if operation == "persist" and result["additionalMaterials"]:
        raise AssertionError("Human materials checklist has gaps: " + str(result["additionalMaterials"]))
    allowed = {"PASS", "SKIP_NO_INGREDIENT_PROVIDER"}
    if any(row["status"] not in allowed for row in rows):
        raise AssertionError("Failed native discovery case")
    return {"cases": len(rows), "active": sum(row["recipeActive"] for row in rows),
            "passed": sum(row["status"] == "PASS" for row in rows),
            "skipped": sum(row["status"] != "PASS" for row in rows), "potatoCraftingCases": 59}


def validate_progression(result, mode, rubber):
    validate_tags(result, mode, rubber)
    if result["mode"] != mode or result["plasticEqualsRubber"] != rubber:
        raise AssertionError("Native progression ignored its requested mode/rubber configuration")
    if result["status"] != "PASS" or result["blocked"] != 0 or result["blockedRecipes"]:
        raise AssertionError("Native manufacture blocked: " + str(result.get("blockedRecipes")) + "; "
                             + str([row["id"] for row in result["targets"] if row["status"] == "BLOCKED"]))
    if not result["targets"] or not result["reachable"] or not result["executedRecipes"] or not result["processWitnesses"]:
        raise AssertionError("Empty native manufacture evidence")
    if not result["lootDataPackOverrideVerified"]:
        raise AssertionError("Player loot override was not preserved")


def verify_level(runtime, entry):
    level_path = runtime / WORLD / "level.dat"
    level = nbtlib.load(level_path)
    if str(level["Data"]["Version"]["Name"]) != "1.15.2":
        raise AssertionError("Server did not write a native 1.15.2 level header")
    mods = {str(row["ModId"]): str(row["ModVersion"]) for row in level["fml"]["LoadingModList"]}
    if mods.get("forge") != "31.2.57" or any(mods.get(jar["id"]) != jar["version"] for jar in entry["jars"]):
        raise AssertionError(f"Saved native mod identities differ from the target manifest: {mods}")
    registry = level["fml"]["Registries"]
    if any(not registry.get(name, {}).get("ids") for name in ("minecraft:item", "minecraft:block")):
        raise AssertionError("Missing native item/block registry snapshot; legacy plural registry names are not valid")
    return {"path": str(level_path), "sha256": digest(level_path), "minecraft": "1.15.2", "mods": mods}


def audit_unlock_log(path, previous_recipes, current_recipes):
    """Witness vanilla's exact mode-conditional saved-book pruning, not arbitrary errors."""
    removed = set(previous_recipes) - set(current_recipes)
    observed = set()
    events = []
    for original in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = re.sub(r"\x1b\[[0-9;]*m", "", original)
        if "/ERROR]" not in line and not any(token in line for token in (
                "NoSuchFieldError", "NoSuchMethodError", "Failed to reload data packs", "Encountered an unexpected exception")):
            continue
        match = re.search(r"\[minecraft/ServerRecipeBook\]: Tried to load unrecognized recipe: ([a-z0-9_]+:[a-z0-9_./-]+) removed now\.$", line)
        if not match or match[1] not in removed:
            raise AssertionError(f"Unexpected native recipe runtime error: {original}")
        observed.add(match[1])
        events.append(original)
    if observed != removed:
        raise AssertionError(f"Saved-book pruning did not match the native packet-set difference: expected {sorted(removed)}, saw {sorted(observed)}")
    return {"expected_removed_ids": sorted(removed), "observed_removed_ids": sorted(observed), "events": events}


def unlock_campaign(root, target, entry, java, port, definition, report):
    runtime = root / "unlock"
    launcher = stage_native(runtime, target, port, "unlock")
    write_json(runtime / "recipe-fixture.json", definition)
    previous_recipes = set()
    for mode, label, operations in UNLOCK_BOOTS:
        configure(runtime, mode)
        row = {"mode": mode, "label": label, "operations": [], "status": "RUNNING"}
        report["unlock_boots"].append(row)
        write_json(root / "native115-recipes-results.json", report)
        print(f"Native115 unlock boot {label}: {', '.join(operations)}", flush=True)
        server = Server(runtime, java, launcher, label)
        try:
            for operation in operations:
                name = f"{label}-{operation}"
                server.send(f"advrecipes {operation} {name}")
                server.wait(f"ADVRECIPES COMPLETE {operation} {name}", 120)
                result = read_json(runtime / f"recipes-{name}.json")
                counts = validate_unlock(result, definition, operation)
                packet = runtime / f"recipes-{name}.bin"
                if not packet.is_file() or packet.stat().st_size == 0:
                    raise AssertionError("Missing native server recipe packet")
                row["operations"].append({"operation": operation, "report": f"recipes-{name}.json",
                                          "packet_sha256": digest(packet), **counts})
            server.send("save-all flush")
        finally:
            server.close()
        current_recipes = {recipe["id"] for recipe in result["bookRecipes"]}
        row["vanilla_recipe_pruning"] = audit_unlock_log(runtime / f"console-{label}.log", previous_recipes, current_recipes)
        previous_recipes = current_recipes
        verify_level(runtime, entry)
        row["status"] = "PASS"
    artifacts = [runtime / f"recipes-{mode}-sweep.{extension}" for mode in ("normal", "tech", "apocalyptic")
                 for extension in ("bin", "json")]
    report["recipe_fixture"] = {"directory": str(runtime), "registry": verify_level(runtime, entry),
                                "files": [{"file": path.name, "sha256": digest(path)} for path in artifacts]}


def progression_campaign(root, target, entry, java, port, report):
    runtime = root / "progression"
    launcher = stage_native(runtime, target, port, "progression")
    write_json(runtime / "progression-fixture.json", progression_fixture())
    pack = runtime / WORLD / "datapacks/progression-override"
    write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 4, "description": "Disposable loot-override witness"}})
    write_json(pack / "data/minecraft/loot_tables/chests/simple_dungeon.json", {
        "pools": [{"name": "test_override", "rolls": 1, "entries": [{"type": "item", "name": "minecraft:dirt"}]}]})
    for mode in MODES:
        for rubber in (True, False):
            configure(runtime, mode, rubber)
            for phase in ("initial", "restart"):
                label = mode.lower() + ("-rubber" if rubber else "-no-rubber") + "-" + phase
                print(f"Native115 progression boot {label}", flush=True)
                server = Server(runtime, java, launcher, label)
                try:
                    for reload in ((False, True) if phase == "initial" else (False,)):
                        name = label + ("-reload" if reload else "")
                        row = {"mode": mode, "rubber": rubber, "phase": "reload" if reload else phase,
                               "report": f"progression-{name}.json", "status": "RUNNING"}
                        report["progression_cases"].append(row)
                        write_json(root / "native115-recipes-results.json", report)
                        if reload:
                            server.send("reload")
                            server.wait("Reloading!", 60)
                        server.send(f"advprogression {name}")
                        server.wait(f"ADVPROGRESSION COMPLETE {name}", 240)
                        result = read_json(runtime / row["report"])
                        validate_progression(result, mode, rubber)
                        row.update(status="PASS", targets=len(result["targets"]),
                                   witnesses=len(result["processWitnesses"]), blocked=result["blocked"])
                    server.send("save-all flush")
                finally:
                    server.close()
                require_clean_log(runtime / f"console-{label}.log")
                verify_level(runtime, entry)


def run(args):
    manifest = args.target_manifest.resolve()
    target = native_target(manifest)
    entry = read_json(manifest)["target115"]
    root = output_path(args.output)
    for protected in (manifest, target[0], *target[2]):
        require_disjoint(root, protected)
    java = args.java8.resolve()
    if not java.is_file() or 'JAVA_VERSION="1.8.' not in (java.parent.parent / "release").read_text():
        raise ValueError("Supply an existing Java 8 bin/java.exe")
    require_free_port(args.port)
    definition, sources = recipe_fixture(target[2])
    before = fingerprint(target[0])
    root.mkdir(parents=True, exist_ok=False)
    report = {"status": "RUNNING", "minecraft": "1.15.2", "forge": "31.2.57", "campaign": args.campaign,
              "target_manifest": str(manifest), "target_manifest_sha256": digest(manifest),
              "target_jars": entry["jars"], "java": str(java), "java_sha256": digest(java),
              "unlock_boots": [], "progression_cases": [], "recipe_inventory": dict(MOD_COUNTS),
              "scope": "packaged native server assertions and recipe exports; not graphical/manual acceptance"}
    write_json(root / "packaged-recipe-sources.json", sources)
    write_json(root / "seed-fingerprint-before.json", before)
    try:
        if args.campaign in ("all", "unlock"):
            unlock_campaign(root, target, entry, java, args.port, definition, report)
        if args.campaign in ("all", "progression"):
            progression_campaign(root, target, entry, java, args.port, report)
        if args.campaign in ("all", "unlock") and len(report["unlock_boots"]) != 5:
            raise AssertionError("The native unlock campaign did not complete five boots")
        if args.campaign in ("all", "progression") and len(report["progression_cases"]) != 18:
            raise AssertionError("The native progression campaign did not complete eighteen checks")
        native_target(manifest)
        if digest(manifest) != report["target_manifest_sha256"]:
            raise AssertionError("Pinned manifest changed during the campaign")
        report["status"] = "PASS"
    except BaseException as failure:
        report.update(status="FAIL", error=f"{type(failure).__name__}: {failure}")
        raise
    finally:
        after = fingerprint(target[0])
        write_json(root / "seed-fingerprint-after.json", after)
        report["seed_unchanged"] = before == after
        if before != after:
            report.update(status="FAIL", seed_error="Protected installed server seed changed")
        write_json(root / "native115-recipes-results.json", report)
        if before != after:
            raise AssertionError("Protected installed server seed changed")
    print(f"Native115 recipe campaign PASS: {root}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=RIG / "build" / ("native115-recipes-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")))
    parser.add_argument("--port", type=int, default=25577)
    parser.add_argument("--campaign", choices=("all", "unlock", "progression"), default="all")
    run(parser.parse_args())


if __name__ == "__main__":
    main()
