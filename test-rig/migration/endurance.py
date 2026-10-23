"""Bounded repeat-batch endurance and restart checks on disposable Works worlds."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import socket
import struct
import time
import traceback

import nbtlib
from matrix import POWER, RIG, WORLD, Server, copy_fixture, digest, fingerprint, plain, profiles, selected_profiles, stage, write_json


ACTIVE = ["W-01", "W-03", "W-04", "S-01", "S-02", "S-03", "S-04",
          "E-01", "E-02", "E-03", "E-05", "P-API-01"]
CHECKED = ACTIVE + ["S-06", "S-05", "E-04", "E-06"]
CAPACITIES = {"lead_acid_battery": 1000, "nickel_hydride_battery": 2000,
              "alkaline_battery": 4000, "lithium_battery": 8000}
PROCESSING = [
    ("steam-furnace", "S-04", (3, 1, 7), [(2, 1, 7), (4, 1, 7)], "iron_ore", "iron_ingot", 1, 4),
    ("steam-crusher", "S-04", (3, 1, 13), [(2, 1, 13), (4, 1, 13)], "cobblestone", "gravel", 0, 4),
    ("electric-furnace", "E-03", (6, 1, 6), [], "iron_ore", "iron_ingot", 0, 4),
    ("electric-crusher", "E-03", (3, 1, 6), [], "cobblestone", "gravel", 0, 4),
    ("electric-oven", "E-03", (9, 1, 6), [], "beef", "cooked_beef", 0, 4),
]
SLOTS = {"steam-furnace": 7, "steam-crusher": 6, "electric-furnace": 10,
         "electric-crusher": 9, "electric-oven": 2}


def require(condition, detail):
    if not condition: raise AssertionError(detail)


def parse_snapshot(document):
    result = {}
    for entry in document["snapshot"]:
        result[tuple(entry["pos"])] = {**entry, "data": plain(nbtlib.parse_nbt(entry.get("nbt", "{}")))}
    return result


def item_counts(entries):
    counts = Counter()
    for entry in entries:
        for stack in entry["data"].get("Items", []):
            counts[stack["id"]] += int(stack["Count"])
    return counts


class Rig:
    def __init__(self, name, runtime, java, launcher, definitions):
        self.name, self.runtime, self.java, self.launcher = name, runtime, java, launcher
        self.definitions, self.server = definitions, None
        self.documents, self.snapshots, self.ledger = {}, {}, []
        self.static_batteries = None

    def pos(self, station, relative):
        return tuple(a + b for a, b in zip(self.definitions[station]["origin"], relative))

    def entry(self, station, relative):
        return self.snapshots[station][self.pos(station, relative)]

    def boot(self, phase):
        self.server = Server(self.runtime, self.java, self.launcher, phase)

    def command(self, command, response=None):
        self.server.send(command)
        if response: self.server.wait(response, 120)

    def checkpoint(self, label):
        for station in CHECKED:
            self.command(f"advworks checkpoint {station} {label}", "Checkpoint written for " + station)
            document = json.loads((self.runtime / "advantage-works-results" /
                                   f"{station}-checkpoint-{label}.json").read_text())
            self.documents[station], self.snapshots[station] = document, parse_snapshot(document)

    def inventory(self, station, relative, items):
        block = self.entry(station, relative)["block"]
        slots = 27 if block == "minecraft:chest" else 1
        for name, candidate, pos, *_ in PROCESSING:
            if station == candidate and tuple(relative) == pos: slots = SLOTS[name]
        # replaceitem uses IInventory setters, not TileEntity.read(), which would
        # refresh conduit caches and could conceal a loaded-network defect.
        x, y, z = self.pos(station, relative)
        wanted = {slot: (name, count) for slot, name, count in items if count}
        for slot in range(slots):
            name, count = wanted.pop(slot, ("minecraft:air", 1))
            self.command(f"replaceitem block {x} {y} {z} container.{slot} {name} {count}")
        for slot, (name, count) in wanted.items():
            self.command(f"replaceitem block {x} {y} {z} container.{slot} {name} {count}")

    def start(self, build=False):
        if build: self.command("advworks build all", "Advantage Works built")
        for station in ACTIVE:
            if station == "W-03": continue  # Its first measured fill happens in seed().
            if not build and station == "P-API-01":
                # Its initial API offers deliberately fill the tank. Repeating
                # those offers requires a reset after checking retained water.
                self.command("advworks reset P-API-01", "Reset 1 station(s).")
            self.command(f"advworks start {station}", "Started " + station)
        # Warmed furnace fuel is independent of the steam bonus. The old station's
        # input chest alone cannot commission a furnace without its fuel slot.
        self.checkpoint("setup")
        if build:
            self.static_batteries = self.entry("E-02", (3, 1, 12))["data"].get("Items", [])
        self.seed(initial=True)

    def seed(self, initial=False):
        for name, station, relative, neighbours, input_id, output_id, slot, count in PROCESSING:
            for neighbour in neighbours: self.inventory(station, neighbour, [])
            contents = []
            if name == "steam-furnace":
                fuel = 64 if initial else sum(int(s["Count"]) for s in self.entry(station, relative)["data"].get("Items", [])
                                              if int(s["Slot"]) == 0 and s["id"] == "minecraft:coal")
                contents.append((0, "minecraft:coal", fuel))
            contents.append((slot, "minecraft:" + input_id, count))
            self.inventory(station, relative, contents)
        lanes = [(6, "dirt", 16), (9, "dirt", 8), (15, "dirt", 8), (18, "dirt", 8)]
        if initial:
            for x in (3, 4): self.inventory("W-04", (x, 1, 6), [])
            for z in (9, 15, 18):
                filters = [(int(s["Slot"]), s["id"], int(s["Count"]))
                           for s in self.entry("W-04", (3, 1, z))["data"].get("Items", [])
                           if int(s["Slot"]) != 0]
                self.inventory("W-04", (3, 1, z), filters)
        for z, _, _ in lanes:
            destinations = [(5, 1, z)] if z == 6 else [(4, 1, z), (3, 0, z)]
            for destination in destinations: self.inventory("W-04", destination, [])
            source = [(0, "minecraft:dirt", 16)] if z == 6 else [(0, "minecraft:dirt", 8), (1, "minecraft:apple", 8)]
            self.inventory("W-04", (2, 1, z), source)
        # Start actions drain/refill through fluid handlers and IInventory.
        self.command("advworks start W-03", "Started W-03")

    def counts(self, station, positions):
        return item_counts([self.entry(station, p) for p in positions])

    def batch(self, label):
        result = {"label": label, "outputs": {}, "world_time": self.documents["W-01"]["worldTime"]}
        for name, station, relative, neighbours, input_id, output_id, _, count in PROCESSING:
            counts = self.counts(station, [relative] + neighbours)
            require(counts["minecraft:" + input_id] == 0, f"{self.name}/{name}: input remains: {counts}")
            require(counts["minecraft:" + output_id] == count, f"{self.name}/{name}: incorrect yield: {counts}")
            allowed = {"minecraft:" + output_id}
            if name == "steam-furnace": allowed.add("minecraft:coal")
            require(not (set(counts) - allowed), f"{self.name}/{name}: unexpected items: {counts}")
            result["outputs"][name] = count
        plain_conveyor = self.counts("W-04", [(2, 1, 6), (3, 1, 6), (4, 1, 6), (5, 1, 6)])
        require(plain_conveyor == {"minecraft:dirt": 16}, f"{self.name}: conveyor conservation {plain_conveyor}")
        require(self.counts("W-04", [(5, 1, 6)]) == {"minecraft:dirt": 16}, f"{self.name}: conveyor throughput")
        result["outputs"]["conveyor"] = 16
        for z, matched_dirt in ((9, True), (15, False), (18, True)):
            above = self.counts("W-04", [(4, 1, z)])
            below = self.counts("W-04", [(3, 0, z)])
            require(above == ({"minecraft:apple": 8} if matched_dirt else {"minecraft:apple": 8, "minecraft:dirt": 8}),
                    f"{self.name}: filter {z} forward {above}")
            require(below == ({"minecraft:dirt": 8} if matched_dirt else {}), f"{self.name}: filter {z} lower {below}")
            require(not self.counts("W-04", [(2, 1, z)]), f"{self.name}: filter {z} source not exhausted")
            require(not any(int(s["Slot"]) == 0 for s in self.entry("W-04", (3, 1, z))["data"].get("Items", [])),
                    f"{self.name}: filter {z} held input remains")
            result["outputs"][f"filter-{z}"] = 16
        # Shared bidirectional pipes may return refined oil to the supply tank.
        outputs = [self.entry("W-03", p)["data"].get("Tank", {}) for p in ((4, 1, 8), (6, 1, 8), (8, 1, 8))]
        outputs.append(self.entry("W-03", (6, 1, 8))["data"].get("InputTank", {}))
        input_amount = sum(t.get("Amount", 0) for t in outputs if t.get("FluidName") == "poweradvantage:crude_oil")
        require(input_amount == 0, f"{self.name}: oil input remaining {input_amount}")
        require(sum(t.get("Amount", 0) for t in outputs) == 1000, f"{self.name}: oil output {outputs}")
        require(all(t.get("FluidName") == "poweradvantage:refined_oil" for t in outputs if t.get("Amount", 0)),
                f"{self.name}: wrong oil output identity")
        result["outputs"]["distillation-mB"] = 1000
        self.invariants()
        self.ledger.append(result)
        write_json(self.runtime / "endurance-ledger.json", self.ledger)
        return result

    def invariants(self):
        for station, relative, amount in (("W-01", (13, 1, 7), 2000), ("S-06", (20, 5, 20), 1000), ("P-API-01", (8, 1, 7), 4000)):
            tank = self.entry(station, relative)["data"].get("Tank", {})
            require(tank.get("Amount") == amount and tank.get("FluidName") == "minecraft:water",
                    f"{self.name}/{station}: closed water witness changed: {tank}")
        require(self.entry("S-01", (12, 1, 7))["data"].get("Temperature", 0) > 0,
                f"{self.name}: geothermal temperature reset")
        require(self.entry("E-02", (3, 1, 12))["data"].get("Items", []) == self.static_batteries,
                f"{self.name}: stored battery items or their charge changed")
        for station in CHECKED:
            for entry in self.snapshots[station].values():
                for bits in entry["data"].get("Energy", []):
                    energy = struct.unpack(">f", struct.pack(">I", int(bits) & 0xFFFFFFFF))[0]
                    require(math.isfinite(energy) and energy >= 0, f"{self.name}: invalid energy at {entry['pos']}: {energy}")
                for stack in entry["data"].get("Items", []):
                    require(0 < int(stack["Count"]) <= 64, f"{self.name}: invalid stack size: {stack}")
                    name = stack["id"].split(":")[-1]
                    if name in CAPACITIES and "energy" in stack.get("tag", {}):
                        charge = stack["tag"]["energy"]
                        require(math.isfinite(charge) and 0 <= charge <= CAPACITIES[name],
                                f"{self.name}: out-of-range battery charge {name}: {charge}")
        charge = self.entry("E-01", (4, 4, 12))["data"].get("Items", [{}])[0].get("tag", {}).get("energy", 0)
        require(charge > 0, f"{self.name}: hydroelectric battery has not charged")

    def structure(self):
        statuses = {}
        for station in CHECKED:
            self.command(f"advworks check {station}", "Advantage Works check:")
            status = json.loads((self.runtime / "advantage-works-results" / f"{station}-latest.json").read_text())["status"]
            statuses[station] = status
            require(status in ("PASS", "MANUAL_REQUIRED"), f"{self.name}: station {station}: {status}")
        return statuses

    def shutdown(self):
        if self.server:
            server, self.server = self.server, None
            server.close()


def run(args):
    require(args.interval >= 60, "Batch interval must be at least 60 seconds")
    require(args.seconds >= args.interval and args.seconds % args.interval == 0, "Duration must contain whole batch intervals")
    current = json.loads((RIG / "profiles/packaged-current.json").read_text())
    for mod in current["mods"]:
        if "sha256" in mod:
            require(digest((RIG / mod["path"]).resolve()) == mod["sha256"], "Current artifact pin mismatch: " + mod["file"])
    definition = json.loads((RIG / "src/main/resources/assets/advantageworkstest/works/works-1.13.2.json").read_text())
    definitions = {s["id"]: s for s in definition["stations"]}
    require(sum(bool(definitions[s].get("exclusive")) for s in ACTIVE) == 1, "Exclusive stations would interfere")
    require((args.migrated / WORLD / "level.dat").is_file(), "Missing converted source world")
    for port in (args.port, args.port + 1):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", port))
    if args.profile_manifest:
        args.workspace = POWER.parents[1]
        profile = selected_profiles(args)["target113"]
    else:
        profile = profiles(POWER.parents[1])["target113"]
    args.output.mkdir(parents=True, exist_ok=False)
    rigs, source_hashes = [], fingerprint(args.migrated / WORLD)
    report = {"status": "RUNNING", "duration_seconds": args.seconds, "interval_seconds": args.interval,
              "feeding_protocol": "IInventory replaceitem and fluid handlers; no inventory NBT writes",
              "active_stations": ACTIVE, "checked_stations": CHECKED, "profiles": {},
              "artifacts": [{"file": jar.name, "sha256": digest(jar)} for jar in profile[2]],
              "manual_checks": "Remain MANUAL_REQUIRED; no GUI, moving drill, lift, or weapon qualification"}
    for index, name in enumerate(("fresh113", "original110-chained113")):
        runtime = args.output / name
        launcher = stage(runtime, profile)
        if index: copy_fixture(args.migrated, runtime)
        properties = runtime / "server.properties"
        properties.write_text(properties.read_text().replace("server-port=25584", f"server-port={args.port + index}"))
        (runtime / "migration-operate.allow").touch()
        (runtime / "advantage-works.allow").touch()
        rigs.append(Rig(name, runtime, args.java8, launcher, definitions))
    try:
        for rig in rigs:
            rig.boot("endurance")
            rig.start(build=True)
        starting_ticks = {rig.name: rig.documents["W-01"]["worldTime"] for rig in rigs}
        started = time.monotonic()
        report["started_at_utc"] = datetime.now(timezone.utc).isoformat()
        for cycle in range(1, args.seconds // args.interval + 1):
            deadline = started + cycle * args.interval
            while time.monotonic() < deadline:
                for rig in rigs: require(rig.server.process.poll() is None, f"{rig.name}: server died")
                time.sleep(min(1, max(0, deadline - time.monotonic())))
            for rig in rigs:
                rig.checkpoint(f"cycle-{cycle:03}")
                rig.batch(f"cycle-{cycle:03}")
                if cycle < args.seconds // args.interval: rig.seed()
                report["profiles"][rig.name] = {"status": "RUNNING", "completed_batches": len(rig.ledger),
                                             "latest_world_time": rig.documents["W-01"]["worldTime"]}
            write_json(args.output / "endurance-results.json", report)
            print(f"{cycle * args.interval}s: both profiles passed batch {cycle}", flush=True)
        elapsed = time.monotonic() - started
        ending_ticks = {rig.name: rig.documents["W-01"]["worldTime"] for rig in rigs}
        for rig in rigs:
            require(ending_ticks[rig.name] - starting_ticks[rig.name] >= args.seconds * 18,
                    f"{rig.name}: fewer than 18 game ticks/second during endurance")
        for rig in rigs:
            rig.structure()
            rig.checkpoint("before-restart")
            rig.batch("before-restart")
            rig.command("save-all flush")
            rig.shutdown()
            rig.boot("endurance-restart")
            rig.checkpoint("after-restart")
            rig.batch("after-restart")
            rig.structure()
            # Keep the saved sources/pipes intact: restarting the stations would
            # replace power blocks and mask a failure to reconnect after load.
            rig.seed()
        time.sleep(args.interval)
        for rig in rigs:
            rig.checkpoint("restart-resumed")
            rig.batch("restart-resumed")
            statuses = rig.structure()
            rig.command("save-all flush")
            rig.shutdown()
            report["profiles"][rig.name] = {"status": "PASS", "elapsed_seconds": elapsed,
                                         "endurance_batches": args.seconds // args.interval,
                                         "total_verified_checkpoints": len(rig.ledger), "station_statuses": statuses,
                                         "game_ticks": ending_ticks[rig.name] - starting_ticks[rig.name],
                                         "restart": "PASS", "resumed_processing": "PASS"}
            totals = Counter()
            for batch in rig.ledger:
                if batch["label"].startswith("cycle-"): totals.update(batch["outputs"])
            report["profiles"][rig.name]["endurance_outputs"] = dict(totals)
            diagnostics = []
            for log in sorted(rig.runtime.glob("console-*.log")):
                text = log.read_text(encoding="utf-8")
                errors = [line for line in text.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
                diagnostics.append({"file": log.name, "error_lines": errors,
                                    "keep_up_warnings": text.count("Can't keep up!")})
            report["profiles"][rig.name]["log_diagnostics"] = diagnostics
        require(fingerprint(args.migrated / WORLD) == source_hashes, "Converted source fixture changed")
        report["source_fixture_unchanged"] = True
        report["status"] = "PASS" if args.seconds >= 1800 else "SMOKE_PASS_NOT_ENDURANCE"
    except BaseException as error:
        report["status"], report["failure"] = "FAIL", str(error)
        report["traceback"] = traceback.format_exc()
        raise
    finally:
        shutdown_errors = []
        for rig in rigs:
            try: rig.shutdown()
            except BaseException as error: shutdown_errors.append(f"{rig.name}: {error}")
        if shutdown_errors:
            report["status"], report["shutdown_errors"] = "FAIL", shutdown_errors
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(args.output / "endurance-results.json", report)
    require(report["status"] != "FAIL", "Endurance qualification failed; see the saved report")
    print(f"{report['status']}: {args.output}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--migrated", type=Path, required=True, help="Immutable, already converted 1.13 runtime")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seconds", type=int, default=1800)
    parser.add_argument("--interval", type=int, default=60)
    parser.add_argument("--port", type=int, default=25584)
    parser.add_argument("--profile-manifest", type=Path, help="Pinned matrix manifest with target113 launcher and jars")
    run(parser.parse_args())
