"""Real chunk unload/reload and six-facing drill moves in disposable packaged worlds."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import time
import traceback

import nbtlib
from endurance import require
from matrix import POWER, RIG, WORLD, Server, copy_fixture, digest, fingerprint, plain, profiles, stage, write_json


FACINGS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1),
           "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def offset(pos, delta, steps=1):
    return [a + steps * b for a, b in zip(pos, delta)]


def fixtures():
    result = []
    for mod in ("steamadvantage", "electricadvantage"):
        for facing, direction in FACINGS.items():
            base = 2048 + len(result) * 64
            origin = [base + 15, 80, 2063]
            track = (0, 0, 1) if direction[0] else (1, 0, 0)
            control = (0, 0, 1) if direction[1] else (0, 1, 0)
            target = offset(origin, track)
            entry = {"id": ("S-MOVE-" if mod == "steamadvantage" else "E-MOVE-") + facing,
                     "min": [base, 72, 2048], "max": [base + 31, 88, 2079], "from": origin, "to": target,
                     "hold": offset(target, control), "block": mod + (":steam_drill" if mod == "steamadvantage" else ":electric_drill"),
                     "energyType": "steam" if mod == "steamadvantage" else "electricity",
                     "initialEnergy": 40 if mod == "steamadvantage" else 400,
                     "facing": facing, "direction": direction, "control": offset(origin, control),
                     "rear": offset(target, direction, -1), "source": offset(origin, control),
                     "witnesses": [origin, target, offset(origin, direction), offset(target, direction),
                                   offset(target, direction, 2), offset(target, direction, -1), offset(target, control)]}
            if mod == "steamadvantage": entry["witnesses"].append(offset(target, direction, 3))
            result.append(entry)
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 4096 + index * 64
        result.append({"id": name, "min": [base, 72, 2048], "max": [base + 31, 88, 2079],
                       "witnesses": [[base + x, 80, 2056] for x in (13, 14, 15, 16, 17)]})
    return result


def parse(entry):
    return plain(nbtlib.parse_nbt(entry.get("nbt", "{}")))


def at(snapshot, name, pos):
    return next(entry for entry in snapshot["cases"][name] if entry["pos"] == list(pos))


def contents(entry):
    return parse(entry).get("Items", [])


def counts(entries):
    result = Counter()
    for entry in entries:
        for stack in contents(entry): result[stack["id"]] += int(stack["Count"])
    return result


def require_unloaded(before, after):
    require(len(before["chunks"]) == len(after["chunks"]) > 0, "Chunk witness count changed")
    old = {tuple(e["pos"]): e for e in before["chunks"]}
    for chunk in after["chunks"]:
        require(not chunk["loaded"], f"Chunk did not unload: {chunk['pos']}")
        require(chunk["unloads"] > old[tuple(chunk["pos"])]["unloads"], f"No real unload event: {chunk['pos']}")


def move_result(fixture, movement):
    before, after = movement["before"], movement["after"]
    failures = []
    def check(ok, detail):
        if not ok: failures.append(detail)
    check(after["pos"] == fixture["to"], "wrong destination")
    check([parse(after).get(axis) for axis in ("x", "y", "z")] == fixture["to"], "wrong serialized tile position")
    check("facing=" + fixture["facing"] in after["state"], "facing changed")
    check(contents(before) == contents(after), "inventory or custom item NBT changed")
    check(counts([after]) == {"minecraft:dirt": 7, "minecraft:apple": 13}, "seeded inventory lost or duplicated")
    check(movement["oldPosition"]["block"] == ("steamadvantage:steam_pipe" if fixture["energyType"] == "steam"
                                               else "electricadvantage:electric_conduit"), "old position is not a conduit")
    old_energy = before["energy"][fixture["energyType"]]
    new_energy = after["energy"][fixture["energyType"]]
    expected = 10 if fixture["energyType"] == "steam" else 250
    # Steam decays 1/16 per normal tick before its eight-tick power update.
    decay = 0.0625 if fixture["energyType"] == "steam" else 0
    check(abs((old_energy - new_energy) - expected - decay) < 0.001,
          f"move energy debit {old_energy - new_energy}, expected {expected}+{decay}")
    return {"status": "FAIL" if failures else "PASS", "failures": failures,
            "energy_before": old_energy, "energy_after": new_energy, "expected_move_cost": expected,
            "inventory_quantity": sum(counts([after]).values())}


class LifecycleRun:
    def __init__(self, runtime, java, launcher, definition):
        self.runtime, self.java, self.launcher, self.definition = runtime, java, launcher, definition
        self.server = None

    def boot(self, label): self.server = Server(self.runtime, self.java, self.launcher, label)

    def close(self):
        if self.server:
            server, self.server = self.server, None
            server.close()

    def command(self, command): self.server.send(command)

    def probe(self, operation, label):
        self.command(f"advlifecycle {operation} {label}")
        self.server.wait(f"ADVLIFECYCLE COMPLETE {operation} {label}", 120)
        return json.loads((self.runtime / "advantage-works-results" / f"lifecycle-{label}.json").read_text())

    def block(self, pos, state): self.command("setblock " + " ".join(map(str, pos)) + " " + state)

    def inventory(self, pos, slot, item, count):
        self.command(f"replaceitem block {' '.join(map(str, pos))} container.{slot} {item} {count}")

    def build(self):
        self.probe("load", "loaded")
        for f in self.definition:
            self.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
            if "from" in f:
                mod = f["block"].split(":")[0]
                self.block(f["control"], "minecraft:redstone_block")
                self.block(f["from"], f["block"] + "[facing=" + f["facing"] + "]")
                self.block(f["to"], mod + (":steam_track" if mod == "steamadvantage" else ":electric_track"))
                self.block(offset(f["from"], f["direction"]), "minecraft:bedrock")
                self.block(offset(f["to"], f["direction"]), "minecraft:bedrock")
                self.inventory(f["from"], 0, 'minecraft:dirt{AdvWorksWitness:"' + f["id"] + '"}', 7)
                self.inventory(f["from"], 1, "minecraft:apple", 13)
            elif f["id"] == "C-FLUID":
                self.block(f["witnesses"][0], "poweradvantage:fluid_pipe")
                for pos in f["witnesses"][1:4]: self.block(pos, "poweradvantage:fluid_pipe")
                self.block(f["witnesses"][4], "poweradvantage:fluid_metal_tank")
            else:
                steam = f["id"] == "C-STEAM"
                self.block(f["witnesses"][0], "poweradvantage:infinite_" + ("steam" if steam else "electricity"))
                for pos in f["witnesses"][1:4]: self.block(pos, "steamadvantage:steam_pipe" if steam else "electricadvantage:electric_conduit")
                self.block(f["witnesses"][4], "steamadvantage:steam_furnace" if steam else "electricadvantage:electric_furnace")
                if steam: self.inventory(f["witnesses"][4], 0, "minecraft:coal", 64)
                self.inventory(f["witnesses"][4], 1 if steam else 0, "minecraft:iron_ore", 4)
        self.probe("prime", "primed")
        self.probe("offer", "first-fluid-offer")
        self.probe("watch", "watching")
        for f in self.definition:
            if "from" in f: self.block(f["control"], "minecraft:air")

    def wait_moves(self):
        for attempt in range(30):
            snapshot = self.probe("snapshot", f"moving-{attempt:02}")
            if len(snapshot["movements"]) == 12: return snapshot
            time.sleep(1)
        raise AssertionError("Drills did not all move within 30 seconds")

    def unload_reload(self, name):
        before = self.probe("snapshot", name + "-before")
        self.command("save-all flush")
        self.probe("release", name + "-released")
        for attempt in range(45):
            absent = self.probe("snapshot", f"{name}-absent-{attempt:02}")
            if all(not c["loaded"] for c in absent["chunks"]): break
            time.sleep(1)
        require_unloaded(before, absent)
        # Prove they stay absent; the diagnostic itself must never acquire a chunk.
        time.sleep(2)
        absent = self.probe("snapshot", name + "-absent-confirmed")
        require_unloaded(before, absent)
        loaded = self.probe("load", name + "-loaded")
        for old, new in zip(before["chunks"], loaded["chunks"]):
            require(old["pos"] == new["pos"] and new["loaded"] and new["loads"] > old["loads"], "Missing chunk reload event")
        for fixture in self.definition:
            positions = [fixture["to"]] if "to" in fixture else fixture["witnesses"]
            for pos in positions:
                old, new = at(before, fixture["id"], pos), at(loaded, fixture["id"], pos)
                require(old["block"] == new["block"] and old["state"] == new["state"], "Block state changed during chunk reload")
                require(contents(old) == contents(new), f"Inventory changed during chunk reload: {fixture['id']}")
                if "tileIdentity" in old:
                    require(new.get("tileIdentity") != old["tileIdentity"], "Old tile object reused, not a real reload")
                if "to" in fixture:
                    require([parse(new).get(axis) for axis in ("x", "y", "z")] == fixture["to"], "Moved tile reverted its position")
                    energy_type = fixture["energyType"]
                    # Exact reserve comparison applies only to the held, isolated movement witness.
                    # Live mining both consumes and receives power during save/load command ticks.
                    if at(before, fixture["id"], fixture["hold"])["block"] == "minecraft:redstone_block":
                        require(abs(old["energy"][energy_type] - new["energy"][energy_type]) <= (1 if energy_type == "steam" else 0),
                                f"Moved drill reserve reset during chunk reload: {fixture['id']}")
                    require(0 <= new["energy"][energy_type] <= (50 if energy_type == "steam" else 500),
                            "Out-of-range drill energy")
                if "Tank" in parse(old):
                    require(parse(old)["Tank"] == parse(new).get("Tank"), f"Tank changed during chunk reload: {fixture['id']}")
        return loaded

    def processing(self, label, fluid_amount):
        snapshot = self.probe("snapshot", label)
        for f in self.definition:
            if f["id"] in ("C-STEAM", "C-ELECTRIC"):
                items = counts([at(snapshot, f["id"], f["witnesses"][4])])
                require(items["minecraft:iron_ingot"] == 4 and items["minecraft:iron_ore"] == 0,
                        f"{f['id']}: processing failed: {items}")
                require(sum(at(snapshot, f["id"], f["witnesses"][4])["energy"].values()) > 0, "No restored power")
            elif f["id"] == "C-FLUID":
                tank = parse(at(snapshot, f["id"], f["witnesses"][4])).get("Tank", {})
                require(tank.get("FluidName") == "minecraft:water" and tank.get("Amount") == fluid_amount, f"Fluid loss: {tank}")
        return snapshot

    def replenish(self):
        for f in self.definition:
            if f["id"] not in ("C-STEAM", "C-ELECTRIC"): continue
            machine = f["witnesses"][4]
            size = 7 if f["id"] == "C-STEAM" else 10
            for slot in range(1 if f["id"] == "C-STEAM" else 0, size):
                self.inventory(machine, slot, "minecraft:air", 1)
            self.inventory(machine, 1 if f["id"] == "C-STEAM" else 0, "minecraft:iron_ore", 4)

    def mine(self):
        for f in self.definition:
            if "to" not in f: continue
            self.block(f["rear"], "minecraft:chest")
            self.block(f["source"], "poweradvantage:infinite_" + f["energyType"])
            self.target_plane(f, "minecraft:cobblestone")
            self.block(offset(f["to"], f["direction"], 2), "minecraft:bedrock")
            self.block(f["hold"], "minecraft:air")

    def mining_results(self, snapshot):
        result = {}
        for f in self.definition:
            if "to" not in f: continue
            inventory = counts([at(snapshot, f["id"], pos) for pos in (f["to"], f["rear"])])
            expected = Counter({"minecraft:dirt": 7, "minecraft:apple": 13,
                                "minecraft:gravel" if f["energyType"] == "steam" else "minecraft:cobblestone":
                                1 if f["energyType"] == "steam" else 9})
            errors = []
            if inventory != expected: errors.append(f"incorrect mining yield or item conservation: {dict(inventory)}")
            if counts([at(snapshot, f["id"], f["rear"])]) != expected: errors.append("not all output reached rear chest")
            tagged = sum(int(s["Count"]) for pos in (f["to"], f["rear"])
                         for s in contents(at(snapshot, f["id"], pos))
                         if s.get("tag", {}).get("AdvWorksWitness") == f["id"])
            if tagged != 7: errors.append("custom item NBT lost")
            drill = at(snapshot, f["id"], f["to"])
            if "facing=" + f["facing"] not in drill["state"]: errors.append("facing changed")
            if drill["energy"][f["energyType"]] <= 0: errors.append("no reconnected energy")
            result[f["id"]] = {"status": "FAIL" if errors else "PASS", "failures": errors, "items": dict(inventory)}
        return result

    def target_plane(self, fixture, block):
        center = offset(fixture["to"], fixture["direction"])
        if fixture["energyType"] == "steam":
            self.block(center, block)
        else:
            axis = next(i for i, value in enumerate(fixture["direction"]) if value)
            perpendicular = [i for i in range(3) if i != axis]
            for a in (-1, 0, 1):
                for b in (-1, 0, 1):
                    pos = center.copy(); pos[perpendicular[0]] += a; pos[perpendicular[1]] += b
                    self.block(pos, block)

    def midblock(self):
        # Obsidian lasts long enough that completion cannot explain a progress reset.
        for f in self.definition:
            if "to" in f: self.target_plane(f, "minecraft:obsidian")
        time.sleep(5)
        before = self.probe("snapshot", "midblock-before-chunk")
        for f in self.definition:
            if "to" in f: require(parse(at(before, f["id"], f["to"])).get("progress", 0) >= 20,
                                   "Mid-block fixture did not begin mining: " + f["id"])
        self.unload_reload("midblock-chunk")
        time.sleep(0.25)
        after = self.probe("snapshot", "midblock-after-chunk")
        chunk_progress = progress_results(self.definition, before, after)
        time.sleep(5)
        before_restart = self.probe("snapshot", "midblock-before-restart")
        self.command("save-all flush"); self.close()
        self.boot("midblock-restart")
        time.sleep(0.25)
        after_restart = self.probe("snapshot", "midblock-after-restart")
        restart_progress = progress_results(self.definition, before_restart, after_restart)
        return {"chunk_reload": chunk_progress, "full_restart": restart_progress}

    def save_read_guards(self):
        before = self.probe("snapshot", "save-read-before")
        legacy = self.probe("legacy-read", "save-read-legacy")
        changed = self.probe("changed-target-read", "save-read-changed")
        for f in self.definition:
            if "to" in f:
                require(contents(at(before, f["id"], f["to"])) == contents(at(changed, f["id"], f["to"])),
                        "Save-read guard changed drill inventory")
        return {"legacy_read": progress_results(self.definition, before, legacy),
                "changed_target_read": changed_target_results(self.definition, legacy, changed)}


def progress_results(definition, before, after):
    result = {}
    for f in definition:
        if "to" not in f: continue
        old, new = parse(at(before, f["id"], f["to"])), parse(at(after, f["id"], f["to"]))
        previous, current = old.get("progress", 0), new.get("progress", 0)
        target_keys = ("targetX", "targetY", "targetZ")
        same_target = all(old.get(key) == new.get(key) for key in target_keys)
        result[f["id"]] = {"status": "PASS" if previous > 0 and current >= previous and same_target else "FAIL",
                           "before": previous, "after": current, "same_target": same_target}
    return result


def changed_target_results(definition, before, after):
    result = {}
    for f in definition:
        if "to" not in f: continue
        old, new = parse(at(before, f["id"], f["to"])), parse(at(after, f["id"], f["to"]))
        previous, current = old.get("progress", 0), new.get("progress", 0)
        cleared = all(key not in new for key in ("targetX", "targetY", "targetZ"))
        result[f["id"]] = {"status": "PASS" if previous > 0 and current == 0 and cleared else "FAIL",
                           "before": previous, "after": current, "target_cleared": cleared}
    return result


def run(args):
    with socket.socket() as port: port.bind(("127.0.0.1", args.port))
    profile = profiles(POWER.parents[1])["target113"]
    manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
    for mod in manifest["mods"]:
        if "sha256" in mod: require(digest((RIG / mod["path"]).resolve()) == mod["sha256"], "Artifact pin mismatch: " + mod["file"])
    definition = fixtures()
    args.output.mkdir(parents=True, exist_ok=False)
    source_hashes = fingerprint(args.migrated / WORLD)
    report = {"status": "RUNNING", "started_at_utc": datetime.now(timezone.utc).isoformat(), "profiles": {},
              "artifacts": [{"file": p.name, "sha256": digest(p)} for p in profile[2]],
              "protocol": "Real chunk events; native inventory setters; finite movement energy; no tile NBT injection"}
    runner = None
    try:
        for index, name in enumerate(("fresh113", "original110-chained113")):
            runtime = args.output / name
            launcher = stage(runtime, profile)
            if index: copy_fixture(args.migrated, runtime)
            properties = runtime / "server.properties"
            properties.write_text(properties.read_text().replace("server-port=25584", f"server-port={args.port}"))
            for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
                (runtime / marker).touch()
            write_json(runtime / "lifecycle-fixture.json", definition)
            runner = LifecycleRun(runtime, args.java8, launcher, definition)
            runner.boot("lifecycle")
            runner.build()
            moved = runner.wait_moves()
            movements = {m["id"]: m for m in moved["movements"]}
            movement_results = {f["id"]: move_result(f, movements[f["id"]]) for f in definition if "from" in f}
            report["profiles"][name] = {"status": "RUNNING", "move_cases": movement_results, "chunk_cycles": 0}
            write_json(args.output / "lifecycle-results.json", report)
            time.sleep(60)
            runner.processing("commissioned", 1000)
            for cycle in range(1, 4):
                runner.unload_reload(f"chunk-cycle-{cycle}")
                runner.replenish()
                runner.probe("offer", f"fluid-offer-{cycle}")
                time.sleep(60)
                runner.processing(f"chunk-resumed-{cycle}", (cycle + 1) * 1000)
                report["profiles"][name]["chunk_cycles"] = cycle
                write_json(args.output / "lifecycle-results.json", report)
                print(f"{name}: chunk unload/reload {cycle} and resumed processing PASS", flush=True)
            before = runner.probe("snapshot", "before-restart")
            runner.command("save-all flush"); runner.close()
            runner.boot("lifecycle-restart")
            time.sleep(1)  # Derived active flags/redstone caches settle through normal power updates.
            after = runner.probe("snapshot", "after-restart")
            for f in definition:
                if "to" not in f: continue
                old, new = at(before, f["id"], f["to"]), at(after, f["id"], f["to"])
                require(contents(old) == contents(new) and old["block"] == new["block"]
                        and "facing=" + f["facing"] in new["state"], "Moved drill changed across full restart")
            runner.processing("restart-retained", 4000)
            runner.replenish(); runner.probe("offer", "restart-fluid-offer")
            runner.mine()
            time.sleep(60)
            mined = runner.processing("restart-resumed", 5000)
            mining_results = runner.mining_results(mined)
            runner.unload_reload("mining-chunk-reload")
            time.sleep(5)
            mining_after = runner.mining_results(runner.probe("snapshot", "mining-retained"))
            midblock_results = runner.midblock()
            save_read_results = runner.save_read_guards()
            runner.command("save-all flush"); runner.close()
            result = {"status": "PASS", "chunk_cycles": 5, "move_cases": movement_results,
                      "full_restart": "PASS", "network_processing": "PASS", "fluid_mB": 5000,
                      "mining": mining_results, "mining_after_chunk_reload": mining_after,
                      "mid_block_progress": midblock_results, "save_read_guards": save_read_results}
            if any(r["status"] != "PASS" for group in (movement_results, mining_results, mining_after,
                                                       midblock_results["chunk_reload"], midblock_results["full_restart"],
                                                       save_read_results["legacy_read"], save_read_results["changed_target_read"])
                   for r in group.values()): result["status"] = "FAIL"
            report["profiles"][name] = result
            write_json(args.output / "lifecycle-results.json", report)
            print(f"{name}: {result['status']}; chunks/restart PASS; movement and mining results recorded", flush=True)
        require(fingerprint(args.migrated / WORLD) == source_hashes, "Source fixture changed")
        report["source_fixture_unchanged"] = True
        report["status"] = "PASS" if all(p["status"] == "PASS" for p in report["profiles"].values()) else "FAIL"
    except BaseException as error:
        report["status"], report["failure"], report["traceback"] = "FAIL", str(error), traceback.format_exc()
        raise
    finally:
        try:
            if runner: runner.close()
        except BaseException as error:
            report["status"], report["shutdown_error"] = "FAIL", str(error)
        report["source_fixture_unchanged"] = fingerprint(args.migrated / WORLD) == source_hashes
        if not report["source_fixture_unchanged"]: report["status"] = "FAIL"
        for runtime in sorted(args.output.glob("*/input-jars.json")):
            inputs = json.loads(runtime.read_text())
            require(all(digest(runtime.parent / "mods" / jar["file"]) == jar["sha256"] for jar in inputs), "Staged jar changed")
            diagnostics = []
            for log in sorted(runtime.parent.glob("console-*.log")):
                text = log.read_text(encoding="utf-8")
                diagnostics.append({"file": log.name, "errors": [line for line in text.splitlines() if "/ERROR]" in line or "/FATAL]" in line],
                                    "keep_up_warnings": text.count("Can't keep up!")})
            report.setdefault("log_diagnostics", {})[runtime.parent.name] = diagnostics
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(args.output / "lifecycle-results.json", report)
    print(f"{report['status']}: {args.output}", flush=True)
    require(report["status"] == "PASS", "Lifecycle defects found; inspect retained results")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--migrated", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25586)
    run(parser.parse_args())
