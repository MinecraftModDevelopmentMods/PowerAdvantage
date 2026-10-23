"""Inventory discovery and recipe-book persistence on hash-pinned packaged servers."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import socket

from matrix import POWER, RIG, Server, digest, stage, write_json


MATERIALS = [
    "minecraft:iron_ingot", "basemetals:steel_ingot", "basemetals:copper_ingot",
    "basemetals:lead_ingot", "basemetals:brass_ingot", "basemetals:steel_bars",
    "poweradvantage:steel_frame", "poweradvantage:fluid_pipe", "poweradvantage:item_conveyor",
    "steamadvantage:steam_pipe", "poweradvantage:bioplastic_ingot", "basemetals:copper_rod",
    "electricadvantage:silicon_ingot", "electricadvantage:integrated_circuit",
    "basemetals:tin_powder", "minecraft:sand", "minecraft:gunpowder",
    "basemetals:steel_plate", "electricadvantage:psu",
    "poweradvantage:starch", "electricadvantage:li_ingot", "electricadvantage:li_nugget",
    "electricadvantage:li_powder", "electricadvantage:li_ore",
    "electricadvantage:silicon_mix", "electricadvantage:solder_mix",
    "minecraft:potato",
]


def fixture(extensions="both"):
    recipes = []
    for mod, root in (
        ("poweradvantage", POWER),
        ("steamadvantage", POWER.parent / "SteamAdvantage/SteamAdvantage"),
        ("electricadvantage", POWER.parent / "ElectricAdvantage/ElectricAdvantage"),
    ):
        if mod == "steamadvantage" and extensions not in ("both", "steam"):
            continue
        if mod == "electricadvantage" and extensions not in ("both", "electric"):
            continue
        data = root / "src/main/resources/data" / mod
        for file in sorted((data / "recipes").glob("*.json")):
            recipe = json.loads(file.read_text())
            if recipe["type"] not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting"):
                continue
            unlock = json.loads((data / "advancements/recipes" / file.name).read_text())
            recipes.append({"id": mod + ":" + file.stem, "ingredients": {
                name: criterion["conditions"]["items"][0]
                for name, criterion in unlock["criteria"].items()
                if criterion["trigger"] == "minecraft:inventory_changed"
            }})
    expected = 35 + (24 if extensions in ("both", "steam") else 0) + (50 if extensions in ("both", "electric") else 0)
    assert len(recipes) == expected, "Update the expected recipe-book inventory deliberately"
    return {"recipes": recipes, "materials": MATERIALS}


def run(args):
    output = args.output.resolve()
    if not output.is_relative_to(POWER.resolve()) or output == POWER.resolve() or output.exists():
        raise ValueError("Use a new disposable output directory inside PowerAdvantage")
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    profile = "packaged-decoupled" if args.without_basemetals else "packaged-current"
    manifest = json.loads((RIG / "profiles" / (profile + ".json")).read_text())
    jars = []
    for mod in manifest["mods"]:
        if mod["id"] == "steamadvantage" and args.extensions not in ("both", "steam"):
            continue
        if mod["id"] == "electricadvantage" and args.extensions not in ("both", "electric"):
            continue
        jar = (RIG / mod["path"]).resolve()
        if digest(jar) != mod["sha256"]:
            raise AssertionError("Artifact pin mismatch: " + str(jar))
        jars.append(jar)
    seed = POWER / "run-advantage-works/packaged-decoupled-20261005-184456"
    launcher = stage(output, (seed, "forge-1.13.2-25.0.223.jar", jars))
    props = output / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
    (output / "advantage-recipes.allow").touch()
    definition = fixture(args.extensions)
    write_json(output / "recipe-fixture.json", definition)
    report = {"status": "RUNNING", "profile": profile, "extensions": args.extensions, "boots": [],
              "inputs": json.loads((output / "input-jars.json").read_text())}

    def boot(mode, label, operations):
        config = output / "config/poweradvantage-common.toml"
        config.parent.mkdir(exist_ok=True)
        config.write_text(f'[options]\nrecipeMode = "{mode}"\n')
        server = Server(output, args.java8, launcher, label)
        row = {"mode": mode, "label": label, "operations": []}
        report["boots"].append(row)
        try:
            for operation in operations:
                name = label + "-" + operation
                server.send(f"advrecipes {operation} {name}")
                server.wait("ADVRECIPES COMPLETE " + operation + " " + name, 120)
                result = json.loads((output / ("recipes-" + name + ".json")).read_text())
                assert result["status"] == "PASS" and len(result["cases"]) == len(definition["recipes"])
                if operation == "persist" and not args.without_basemetals:
                    assert not result["additionalMaterials"], "Human materials checklist has gaps"
                row["operations"].append({"operation": operation, "report": "recipes-" + name + ".json",
                                          "active": sum(case["recipeActive"] for case in result["cases"]),
                                          "passed": sum(case["status"] == "PASS" for case in result["cases"])})
            server.send("save-all flush")
        finally:
            server.close()

    try:
        boot("NORMAL", "normal", ("sweep", "persist"))
        boot("NORMAL", "normal-restart", ("restore",))
        boot("TECH_PROGRESSION", "tech", ("restore", "sweep", "persist"))
        boot("APOCALYPTIC", "apocalyptic", ("restore", "sweep", "persist"))
        boot("NORMAL", "normal-return", ("restore",))
        report["status"] = "PASS"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        write_json(output / "recipe-unlock-results.json", report)
    print(f"Recipe discovery PASS: {profile}, extensions={args.extensions}, all modes, five full process boots. Evidence: {output}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=POWER / "run-recipe-unlocks" /
                        datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--port", type=int, default=25589)
    parser.add_argument("--without-basemetals", action="store_true")
    parser.add_argument("--extensions", choices=("both", "steam", "electric", "none"), default="both")
    run(parser.parse_args())
