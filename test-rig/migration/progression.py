"""Prove all survival Advantage items have manufacture paths from natural inputs."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import socket

from matrix import POWER, RIG, Server, digest, stage, write_json


NATURAL_RESOURCES = [
    "minecraft:oak_log", "minecraft:oak_sapling", "minecraft:stone", "minecraft:cobblestone",
    "minecraft:dirt", "minecraft:coal", "minecraft:iron_ore", "minecraft:gold_ore",
    "minecraft:sand", "minecraft:clay_ball", "minecraft:gravel", "minecraft:redstone",
    "minecraft:diamond", "minecraft:emerald", "minecraft:obsidian", "minecraft:potato",
    "minecraft:poisonous_potato", "minecraft:wheat", "minecraft:sugar_cane",
    "minecraft:gunpowder", "minecraft:flint", "minecraft:leather", "minecraft:string",
    "minecraft:feather", "minecraft:blaze_rod", "minecraft:ender_pearl", "minecraft:quartz",
    "basemetals:copper_ore", "basemetals:tin_ore", "basemetals:zinc_ore", "basemetals:lead_ore",
    "basemetals:nickel_ore", "basemetals:silver_ore", "electricadvantage:li_ore",
    "electricadvantage:sulfur_ore",
]

EXCLUSIONS = {
    "poweradvantage:infinite_steam": "Creative-only source, intentionally no survival recipe",
    "poweradvantage:infinite_electricity": "Creative-only source, intentionally no survival recipe",
    "poweradvantage:infinite_quantum": "Creative-only source, intentionally no survival recipe",
    "poweradvantage:crude_oil": "World fluid block, placed and collected through its bucket",
    "poweradvantage:refined_oil": "World fluid block, produced by distillation and placed through its bucket",
    "poweradvantage:fluid_pipe_terminal": "Hidden state of an ordinary pipe adjacent to a fluid capability",
    "steamadvantage:drillbit": "Hidden helper placed by a working steam drill; craft steam_drill_bit instead",
    "steamadvantage:platform": "Elevator-generated helper with no block drops",
    "steamadvantage:pump_pipe_steam": "Hidden helper created by a steam pump",
    "electricadvantage:pump_pipe_electric": "Hidden helper created by an electric pump",
    "electricadvantage:laser_turret_evil": "Hidden converted state of the craftable laser turret",
}


def fixture():
    return {"naturalResources": NATURAL_RESOURCES, "exclusions": EXCLUSIONS,
            "machineBounds": {"min": [16384, 78, 16384], "max": [16415, 88, 16415]}}


def validate_tags(result, mode, rubber):
    tags = result["tags"]
    assert "electricadvantage:electric_conduit" in tags["wires"]
    assert {"basemetals:coal_powder", "basemetals:charcoal_powder"} <= set(tags["dusts/carbon"])
    assert ("poweradvantage:bioplastic_ingot" in tags["rubber"]) == rubber
    assert {"poweradvantage:bioplastic_ingot", "electricadvantage:petrolplastic_ingot"} <= set(tags["plastic"])
    assert "electricadvantage:control_circuit" in tags["circuit_boards"]
    assert "electricadvantage:blank_circuit_board" not in tags["circuit_boards"]
    assert "electricadvantage:solder" in tags["solder"]
    for metal in ("lead", "tin", "silver"):
        assert (f"basemetals:{metal}_ingot" in tags["solder"]) == (mode == "NORMAL")


def run(args):
    output = args.output.resolve()
    if not output.is_relative_to(POWER.resolve()) or output == POWER.resolve() or output.exists():
        raise ValueError("Use a new disposable output inside PowerAdvantage")
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    jars = []
    if args.baseline:
        # Immutable artifacts from the preceding discovery qualification, not rebuilt paths.
        baseline = POWER / "run-recipe-unlocks/20261022-current-final"
        for entry in json.loads((baseline / "input-jars.json").read_text()):
            if "TestHarness" in entry["file"]:
                continue
            jar = baseline / "mods" / entry["file"]
            if digest(jar) != entry["sha256"]:
                raise AssertionError("Baseline hash mismatch: " + str(jar))
            jars.append(jar)
        jars.append(RIG / "build/libs/AdvantageWorksTestHarness-1.1.0.113021.jar")
    else:
        manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
        for mod in manifest["mods"]:
            jar = (RIG / mod["path"]).resolve()
            if digest(jar) != mod["sha256"]:
                raise AssertionError("Artifact pin mismatch: " + str(jar))
            jars.append(jar)
    launcher = stage(output, (POWER / "run-advantage-works/packaged-decoupled-20261005-184456",
                              "forge-1.13.2-25.0.223.jar", jars))
    props = output / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
    (output / "advantage-progression.allow").touch()
    write_json(output / "progression-fixture.json", fixture())
    pack = output / "AdvantageWorksMigration/datapacks/progression-override"
    write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 4, "description": "Disposable loot-override witness"}})
    write_json(pack / "data/minecraft/loot_tables/chests/simple_dungeon.json", {
        "pools": [{"name": "test_override", "rolls": 1, "entries": [{"type": "item", "name": "minecraft:dirt"}]}]})
    report = {"status": "RUNNING", "baseline": args.baseline, "cases": [],
              "inputs": json.loads((output / "input-jars.json").read_text())}
    cases = [("NORMAL", True)] if args.baseline else [
        (mode, rubber) for mode in ([args.mode] if args.mode else ("NORMAL", "TECH_PROGRESSION", "APOCALYPTIC"))
        for rubber in (True, False)]
    try:
        for mode, rubber in cases:
            config = output / "config/poweradvantage-common.toml"
            config.parent.mkdir(exist_ok=True)
            config.write_text(f'[options]\nrecipeMode = "{mode}"\nplasticEqualsRubber = {str(rubber).lower()}\n')
            for phase in ("initial", "restart"):
                label = mode.lower() + ("-rubber" if rubber else "-no-rubber") + "-" + phase
                server = Server(output, args.java8, launcher, label)
                try:
                    for reload in (False, True) if phase == "initial" and not args.baseline else (False,):
                        current = label + ("-reload" if reload else "")
                        if reload:
                            server.send("reload")
                        server.send("advprogression " + current)
                        server.wait("ADVPROGRESSION COMPLETE " + current, 180)
                        result = json.loads((output / ("progression-" + current + ".json")).read_text())
                        report["cases"].append({"mode": mode, "rubber": rubber, "phase": "reload" if reload else phase,
                                                "status": result["status"], "blocked": result["blocked"],
                                                "report": "progression-" + current + ".json"})
                        if args.baseline:
                            if result["status"] != "FAIL":
                                raise AssertionError("Baseline unexpectedly has complete manufacture paths")
                        else:
                            validate_tags(result, mode, rubber)
                            if result["status"] != "PASS":
                                raise AssertionError("Unobtainable components: " + ", ".join(
                                    item["id"] for item in result["targets"] if item["status"] == "BLOCKED")
                                    + "; blocked recipes: " + ", ".join(result.get("blockedRecipes", [])))
                    server.send("save-all flush")
                finally:
                    server.close()
        report["status"] = "EXPECTED_FAIL" if args.baseline else "PASS"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        write_json(output / "progression-results.json", report)
    print(f'Manufacturing progression {report["status"]}: {output}', flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=POWER / "run-progression" /
                        datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--port", type=int, default=25590)
    parser.add_argument("--baseline", action="store_true")
    parser.add_argument("--mode", choices=("NORMAL", "TECH_PROGRESSION", "APOCALYPTIC"))
    run(parser.parse_args())
