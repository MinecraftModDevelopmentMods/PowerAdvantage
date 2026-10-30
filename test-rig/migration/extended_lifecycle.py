"""Partial network lifecycles and repeated drill operation on disposable worlds."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import time
import traceback

from endurance import require
from lifecycle import FACINGS, LifecycleRun, at, contents, counts, move_result, offset, parse
from matrix import POWER, RIG, WORLD, copy_fixture, digest, fingerprint, profiles, stage, write_json


def fixtures():
    result = []
    for mod in ("steamadvantage", "electricadvantage"):
        specs = [(name, direction, (0, 0, 1) if direction[0] else (1, 0, 0), False)
                 for name, direction in FACINGS.items()]
        specs += [("vertical-up", FACINGS["north"], FACINGS["up"], False),
                  ("vertical-down", FACINGS["north"], FACINGS["down"], False),
                  ("junction", FACINGS["north"], FACINGS["down"], True)]
        for name, direction, track, junction in specs:
            base = 6144 + len(result) * 64
            origin = [base + 14, 80, 2062]
            route = [offset(origin, track, n) for n in range(5)]
            axis = next(i for i in range(3) if not direction[i] and not track[i])
            control = tuple(-1 if i == axis else 0 for i in range(3))
            facing = next(k for k, v in FACINGS.items() if v == direction)
            steam = mod == "steamadvantage"
            entry = {"id": ("S-EXT-" if steam else "E-EXT-") + name,
                     "min": [base, 72, 2048], "max": [base + 31, 90, 2079],
                     "from": origin, "to": route[-1], "route": route,
                     "controls": [offset(pos, control) for pos in route], "hold": offset(route[-1], control),
                     "block": mod + (":steam_drill" if steam else ":electric_drill"),
                     "energyType": "steam" if steam else "electricity", "initialEnergy": 40 if steam else 400,
                     "facing": facing, "direction": direction, "track": track, "junction": junction,
                     "rear": offset(route[-1], direction, -1), "source": offset(origin, control, -1)}
            if junction:
                entry["alternatives"] = [offset(origin, FACINGS["up"]), offset(origin, FACINGS["east"])]
            witnesses = route + entry["controls"] + [offset(pos, direction) for pos in route]
            witnesses += [entry["rear"], entry["source"], offset(route[-1], direction, 2)]
            center = offset(route[-1], direction)
            plane_axes = [i for i, value in enumerate(direction) if not value]
            if not steam:
                for a in (-1, 0, 1):
                    for b in (-1, 0, 1):
                        pos = center.copy(); pos[plane_axes[0]] += a; pos[plane_axes[1]] += b
                        witnesses.append(pos)
            witnesses += entry.get("alternatives", [])
            entry["witnesses"] = list({tuple(p): p for p in witnesses}.values())
            result.append(entry)
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 8192 + index * 64
        result.append({"id": name, "min": [base, 72, 2048], "max": [base + 31, 90, 2079],
                       "witnesses": [[base + x, 80, 2056] for x in (13, 14, 15, 16, 17)],
                       "groups": {"supply": [[base >> 4, 2056 >> 4]],
                                  "machine": [[(base >> 4) + 1, 2056 >> 4]]}})
    return result


def verdict(errors, **details):
    return {"status": "FAIL" if errors else "PASS", "failures": errors, **details}


def continuous_absence(old_chunks, samples, selected):
    """Prove one event-stable absence segment ending at the confirmed sample."""
    anchor_tick, anchor_counts, previous_tick = None, None, None
    errors = []
    for index, sample in enumerate(samples):
        tick = sample.get("worldTime")
        if type(tick) is not int or tick < 0 or (previous_tick is not None and tick < previous_tick):
            errors.append("invalid or backwards native observation clock")
            continue
        previous_tick = tick
        states = {tuple(c["pos"]): c for c in sample["chunks"]}
        if len(states) != len(sample["chunks"]) or any(p not in states for p in selected):
            errors.append("missing or duplicate selected chunk observation")
            continue
        counters = tuple((states[p].get("loads"), states[p].get("unloads")) for p in selected)
        if any(type(value) is not int or value < 0 for pair in counters for value in pair):
            errors.append("invalid native chunk event counter")
            continue
        absent = all(states[p].get("loaded") is False
                     and states[p]["unloads"] > old_chunks[p]["unloads"]
                     and states[p]["loads"] >= old_chunks[p]["loads"] for p in selected)
        if anchor_tick is None and absent:
            anchor_tick, anchor_counts = tick, counters
        if anchor_tick is not None and (not absent or counters != anchor_counts):
            errors.append("selected endpoint reloaded during observed absence")
        if sample.get("confirmed") and index != len(samples) - 1:
            errors.append("absence confirmation is not the terminal observation")
    terminal = samples[-1] if samples else {}
    tick = terminal.get("worldTime")
    stable = (not errors and anchor_tick is not None and terminal.get("confirmed") is True
              and type(tick) is int and tick - anchor_tick >= 40)
    return stable, errors


def partial_result(definition, before, samples, loaded, group):
    result = {}
    old_chunks = {tuple(c["pos"]): c for c in before["chunks"]}
    new_chunks = {tuple(c["pos"]): c for c in loaded["chunks"]}
    for f in definition:
        if "groups" not in f: continue
        selected = [tuple(p) for p in f["groups"][group]]
        other = "machine" if group == "supply" else "supply"
        retained = [tuple(p) for p in f["groups"][other]]
        stable, errors = continuous_absence(old_chunks, samples, selected)
        transient = False
        for sample in samples:
            states = {tuple(c["pos"]): c for c in sample["chunks"]}
            transient |= any(type(states.get(p, {}).get("unloads")) is int
                             and states[p]["unloads"] > old_chunks[p]["unloads"] for p in selected)
            if not all(states.get(p, {}).get("loaded") is True
                       and states[p].get("loads") == old_chunks[p]["loads"]
                       and states[p].get("unloads") == old_chunks[p]["unloads"] for p in retained):
                errors.append("retained endpoint also unloaded")
            for entry in before["cases"][f["id"]]:
                if (entry["pos"][0] >> 4, entry["pos"][2] >> 4) not in retained or "tileIdentity" not in entry: continue
                now = at(sample, f["id"], entry["pos"])
                if now.get("tileIdentity") != entry["tileIdentity"]: errors.append("retained tile identity changed")
        if not stable: errors.append("selected endpoint did not stay genuinely unloaded for two seconds")
        for p in selected:
            observed_loads = samples[-1]["chunks"] if samples else []
            last_loads = next((c.get("loads") for c in observed_loads if tuple(c["pos"]) == p), old_chunks[p]["loads"])
            minimum_loads = max(old_chunks[p]["loads"], last_loads) if type(last_loads) is int else old_chunks[p]["loads"]
            if not new_chunks[p]["loaded"] or new_chunks[p]["loads"] <= minimum_loads:
                errors.append("missing real endpoint reload event")
        for entry in before["cases"][f["id"]]:
            now = at(loaded, f["id"], entry["pos"])
            if entry["block"] != now.get("block") or entry["state"] != now.get("state"):
                errors.append("network block/state changed")
            if "Tank" in parse(entry) and parse(entry)["Tank"] != parse(now).get("Tank"):
                errors.append("fluid contents changed")
        result[f["id"]] = verdict(sorted(set(errors)), stable_unload=stable, real_unload_event=transient)
    return result


def movement_results(definition, snapshot):
    result = {}
    for f in definition:
        if "route" not in f: continue
        records = sorted((m for m in snapshot["movements"] if m["id"] == f["id"]), key=lambda m: m["step"])
        errors, steps = [], []
        if [m["step"] for m in records] != [1, 2, 3, 4]: errors.append("four consecutive moves were not witnessed")
        for m in records:
            n = m["step"]
            local = {**f, "from": f["route"][n - 1], "to": f["route"][n]}
            checked = move_result(local, m)
            errors.extend(checked["failures"])
            if m["before"]["pos"] != local["from"]: errors.append("wrong movement origin")
            steps.append(checked)
        result[f["id"]] = verdict(sorted(set(errors)), steps=steps)
    return result


def reserve_results(definition, before, after):
    result = {}
    for f in definition:
        if "route" not in f: continue
        a, b = at(before, f["id"], f["from"]), at(after, f["id"], f["from"])
        errors = []
        expected_reserve = 9 if f["energyType"] == "steam" else 249
        if a.get("energy", {}).get(f["energyType"]) != expected_reserve:
            errors.append("insufficient-energy fixture did not have its declared finite reserve")
        if not 0 <= b.get("energy", {}).get(f["energyType"], -1) <= expected_reserve:
            errors.append("stalled reserve increased or became invalid")
        if a["block"] != f["block"] or b.get("block") != f["block"]: errors.append("drill moved with insufficient energy")
        if contents(a) != contents(b): errors.append("inventory changed while stalled")
        if any(m["id"] == f["id"] for m in after["movements"]): errors.append("unpaid movement was observed")
        for pos in f["route"][1:]:
            if at(after, f["id"], pos).get("block") != f["block"].replace("_drill", "_track"):
                errors.append("track consumed without sufficient energy")
        result[f["id"]] = verdict(errors, reserve_before=a["energy"][f["energyType"]], reserve_after=b["energy"][f["energyType"]])
    return result


def expected_items(f):
    return Counter({"minecraft:dirt": 7, "minecraft:apple": 13,
                    "minecraft:gravel" if f["energyType"] == "steam" else "minecraft:cobblestone":
                    1 if f["energyType"] == "steam" else 9})


def output_results(definition, snapshot, blocked):
    result = {}
    for f in definition:
        if "route" not in f: continue
        drill, chest = (at(snapshot, f["id"], pos) for pos in (f["to"], f["rear"]))
        expected = expected_items(f)
        errors = []
        ballast = 27 * 64 if blocked else 24 * 64
        total = counts([drill, chest]); total.subtract({"minecraft:stone": ballast}); total += Counter()
        if total != expected: errors.append("item loss, duplication or wrong mining yield")
        if counts([chest])["minecraft:stone"] != ballast: errors.append("full-chest ballast changed")
        if blocked and counts([drill]) != expected: errors.append("blocked output did not remain in the drill")
        if not blocked and counts([drill]): errors.append("output did not resume after three slots were freed")
        tagged = sum(int(s["Count"]) for e in (drill, chest) for s in contents(e)
                     if s.get("tag", {}).get("AdvWorksWitness") == f["id"])
        if tagged != 7: errors.append("custom item tag lost")
        result[f["id"]] = verdict(errors, conserved=dict(total), ballast=ballast)
    return result


def redstone_results(definition, before, held, after):
    result = {}
    for f in definition:
        if "route" not in f: continue
        a, b, c = (at(s, f["id"], f["to"]) for s in (before, held, after))
        values = [parse(e).get("progress", 0) for e in (a, b, c)]
        errors = []
        if values[0] <= 0: errors.append("fixture was not mid-block before interruption")
        if values[1] != 0: errors.append("redstone failed to cancel mining progress")
        if values[2] <= 0: errors.append("mining failed to resume after redstone release")
        if not (contents(a) == contents(b) == contents(c)): errors.append("redstone changed retained inventory")
        if any(e.get("block") != f["block"] or "facing=" + f["facing"] not in e.get("state", "") for e in (a, b, c)):
            errors.append("drill position or facing changed")
        result[f["id"]] = verdict(errors, progress=values)
    return result


def restart_results(definition, before, after):
    result = {}
    for f in definition:
        positions = [f["to"], f["rear"]] if "route" in f else f["witnesses"]
        errors = []
        for pos in positions:
            a, b = at(before, f["id"], pos), at(after, f["id"], pos)
            if a["block"] != b.get("block") or a["state"] != b.get("state"): errors.append("block state changed")
            if contents(a) != contents(b): errors.append("inventory changed")
            if "Tank" in parse(a) and parse(a)["Tank"] != parse(b).get("Tank"): errors.append("tank changed")
            if "route" in f and pos == f["to"]:
                if [parse(b).get(k) for k in ("x", "y", "z")] != f["to"]: errors.append("moved tile position changed")
        result[f["id"]] = verdict(errors)
    return result


class ExtendedRun(LifecycleRun):
    def build(self):
        self.probe("load", "loaded")
        for f in self.definition:
            self.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
            if "route" in f:
                self.block(f["controls"][0], "minecraft:redstone_block")
                self.block(f["from"], f["block"] + "[facing=" + f["facing"] + "]")
                track = f["block"].replace("_drill", "_track")
                for pos in f["route"][1:] + f.get("alternatives", []): self.block(pos, track)
                for pos in f["route"]: self.block(offset(pos, f["direction"]), "minecraft:bedrock")
                self.inventory(f["from"], 0, 'minecraft:dirt{AdvWorksWitness:"' + f["id"] + '"}', 7)
                self.inventory(f["from"], 1, "minecraft:apple", 13)
            elif f["id"] == "C-FLUID":
                for pos in f["witnesses"][:4]: self.block(pos, "poweradvantage:fluid_pipe")
                self.block(f["witnesses"][4], "poweradvantage:fluid_metal_tank")
            else:
                steam = f["id"] == "C-STEAM"
                self.block(f["witnesses"][0], "poweradvantage:infinite_" + ("steam" if steam else "electricity"))
                for pos in f["witnesses"][1:4]: self.block(pos, "steamadvantage:steam_pipe" if steam else "electricadvantage:electric_conduit")
                self.block(f["witnesses"][4], "steamadvantage:steam_furnace" if steam else "electricadvantage:electric_furnace")
                if steam: self.inventory(f["witnesses"][4], 0, "minecraft:coal", 64)
                self.inventory(f["witnesses"][4], 1 if steam else 0, "minecraft:iron_ore", 4)
        self.probe("offer", "commission-fluid")
        self.probe("watch", "watching")

    def release_drills(self, step):
        for f in self.definition:
            if "route" in f: self.block(f["controls"][step], "minecraft:air")

    def hold_drills(self, step):
        for f in self.definition:
            if "route" in f: self.block(f["controls"][step], "minecraft:redstone_block")

    def wait_step(self, step):
        for attempt in range(30):
            snap = self.probe("snapshot", f"step-{step}-wait-{attempt:02}")
            if all(snap.get("routeSteps", {}).get(f["id"], 0) == step for f in self.definition if "route" in f):
                return self.probe("snapshot", f"step-{step}-complete")
            time.sleep(0.5)
        raise AssertionError(f"Native drill step {step} did not complete; raw evidence retained")

    def partial(self, group, cycle):
        label = f"partial-{cycle}-{group}"
        before = self.probe("snapshot", label + "-before")
        self.command("save-all flush")
        self.probe("release-" + group, label + "-released")
        samples = []
        # Bounded observation window includes real event counts even if a live source reloads its neighbour.
        for n in range(8):
            time.sleep(1)
            samples.append(self.probe("snapshot", f"{label}-sample-{n}"))
        time.sleep(2)
        confirmed = self.probe("snapshot", label + "-confirmed")
        # Absence must span both of the final observations, not just one lucky instant.
        confirmed["confirmed"] = True
        samples.append(confirmed)
        loaded = self.probe("load-" + group, label + "-loaded")
        result = partial_result(self.definition, before, samples, loaded, group)
        return {"label": label, "group": group, "cases": result}

    def install_blocked_output(self):
        for f in self.definition:
            if "route" not in f: continue
            self.block(f["rear"], "minecraft:chest")
            for slot in range(27): self.inventory(f["rear"], slot, "minecraft:stone", 64)
            self.block(f["source"], "poweradvantage:infinite_" + f["energyType"])
            self.target_plane(f, "minecraft:cobblestone")
            self.block(offset(f["to"], f["direction"], 2), "minecraft:bedrock")
        self.release_drills(4)

    def freed_output(self):
        for f in self.definition:
            if "route" in f:
                for slot in range(3): self.inventory(f["rear"], slot, "minecraft:air", 1)


def run(args):
    with socket.socket() as port: port.bind(("127.0.0.1", args.port))
    profile = profiles(POWER.parents[1])["target113"]
    manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
    for mod in manifest["mods"]:
        if "sha256" in mod: require(digest((RIG / mod["path"]).resolve()) == mod["sha256"], "Artifact pin mismatch: " + mod["file"])
    definition = fixtures()
    args.output.mkdir(parents=True, exist_ok=False)
    source_hashes = fingerprint(args.migrated / WORLD)
    write_json(args.output / "source-fingerprint-before.json", source_hashes)
    report = {"status": "RUNNING", "profiles": {}, "started_at_utc": datetime.now(timezone.utc).isoformat(),
              "artifacts": [{"file": p.name, "sha256": digest(p)} for p in profile[2]],
              "protocol": "Native repeated moves and chunk events; finite public reserve priming; external controls; no tile NBT injection"}
    runner = None
    try:
        for index, name in enumerate(("fresh113", "original110-chained113")):
            runtime = args.output / name
            launcher = stage(runtime, profile)
            if index: copy_fixture(args.migrated, runtime)
            props = runtime / "server.properties"
            props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
            for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"): (runtime / marker).touch()
            write_json(runtime / "lifecycle-fixture.json", definition)
            runner = ExtendedRun(runtime, args.java8, launcher, definition)
            runner.boot("extended")
            runner.build()
            result = report["profiles"][name] = {"status": "RUNNING"}
            low = runner.probe("low-prime", "low-before")
            runner.release_drills(0); time.sleep(2)
            stalled = runner.probe("snapshot", "low-stalled")
            result["insufficient_energy"] = reserve_results(definition, low, stalled)
            runner.hold_drills(0)
            for step in range(4):
                runner.probe("prime", f"prime-step-{step + 1}")
                runner.release_drills(step)
                moved = runner.wait_step(step + 1)
                print(f"{name}: native drill step {step + 1}/4 observed", flush=True)
            result["moves"] = movement_results(definition, moved)
            time.sleep(50); runner.processing("network-commissioned", 1000)
            result["partial_networks"] = []
            for cycle, group in enumerate(("machine", "supply", "supply", "machine"), 1):
                check = runner.partial(group, cycle)
                result["partial_networks"].append(check)
                runner.replenish(); runner.probe("offer", f"partial-fluid-{cycle}")
                time.sleep(50); runner.processing(f"partial-resumed-{cycle}", (cycle + 1) * 1000)
                print(f"{name}: {group}-only unload {cycle}, resumed processing PASS; stability recorded", flush=True)
                write_json(args.output / "extended-results.json", report)
            # Both endpoints absent, then reload in each order while retaining the loaded half.
            result["reload_orders"] = []
            for order in (("machine", "supply"), ("supply", "machine")):
                label = "order-" + "-".join(order)
                before = runner.probe("snapshot", label + "-before")
                runner.probe("release-supply", label + "-release-supply")
                runner.probe("release-machine", label + "-release-machine")
                time.sleep(5)
                absent = runner.probe("snapshot", label + "-absent")
                first = runner.probe("load-" + order[0], label + "-first")
                time.sleep(2)
                settled = runner.probe("snapshot", label + "-settled")
                loaded = runner.probe("load-" + order[1], label + "-loaded")
                result["reload_orders"].append({"label": label, "order": list(order),
                                               "cases": order_results(definition, before, absent, first, settled, loaded, order)})
                runner.replenish(); time.sleep(50); runner.processing(label + "-resumed", 5000)
            runner.install_blocked_output(); time.sleep(45)
            blocked = runner.probe("snapshot", "blocked-output")
            result["blocked_output"] = output_results(definition, blocked, True)
            runner.command("save-all flush"); runner.close(); runner.boot("blocked-restart")
            time.sleep(1)
            retained = runner.probe("snapshot", "blocked-retained")
            result["restart"] = restart_results(definition, blocked, retained)
            result["blocked_after_restart"] = output_results(definition, retained, True)
            runner.freed_output(); time.sleep(20)
            result["resumed_output"] = output_results(definition, runner.probe("snapshot", "freed-output"), False)
            for f in definition:
                if "route" in f: runner.target_plane(f, "minecraft:obsidian")
            time.sleep(4)
            before = runner.probe("snapshot", "redstone-before")
            runner.hold_drills(4); time.sleep(2)
            held = runner.probe("snapshot", "redstone-held")
            runner.release_drills(4); time.sleep(4)
            after = runner.probe("snapshot", "redstone-resumed")
            result["redstone"] = redstone_results(definition, before, held, after)
            runner.hold_drills(4)
            runner.command("save-all flush"); runner.close()
            result["network_processing"] = "PASS"
            result["status"] = qualification(result)
            write_json(args.output / "extended-results.json", report)
            print(f"{name}: {result['status']}; all observations retained", flush=True)
        report["status"] = "PASS" if all(p["status"] == "PASS" for p in report["profiles"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        raise
    finally:
        try:
            if runner: runner.close()
        except BaseException as error: report.update(status="FAIL", shutdown_error=str(error))
        after_hashes = fingerprint(args.migrated / WORLD)
        write_json(args.output / "source-fingerprint-after.json", after_hashes)
        report["source_fixture_unchanged"] = source_hashes == after_hashes
        if not report["source_fixture_unchanged"]: report["status"] = "FAIL"
        for path in args.output.glob("*/input-jars.json"):
            inputs = json.loads(path.read_text())
            require(all(digest(path.parent / "mods" / p["file"]) == p["sha256"] for p in inputs), "Staged jar changed")
            report.setdefault("log_diagnostics", {})[path.parent.name] = [
                {"file": log.name, "errors": [line for line in log.read_text(encoding="utf-8").splitlines() if "/ERROR]" in line or "/FATAL]" in line],
                 "keep_up_warnings": log.read_text(encoding="utf-8").count("Can't keep up!")}
                for log in sorted(path.parent.glob("console-*.log"))]
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(args.output / "extended-results.json", report)
    print(f"{report['status']}: {args.output}", flush=True)
    require(report["status"] == "PASS", "Extended lifecycle acceptance failures; inspect retained reports")


def order_results(definition, before, absent, first, settled, loaded, order):
    result = {}
    old = {tuple(c["pos"]): c for c in before["chunks"]}
    for f in definition:
        if "groups" not in f: continue
        errors = []
        for group in order:
            for p in f["groups"][group]:
                a = next(c for c in absent["chunks"] if c["pos"] == p)
                b = next(c for c in loaded["chunks"] if c["pos"] == p)
                if a["loaded"] or a["unloads"] <= old[tuple(p)]["unloads"]: errors.append("both ends were not genuinely absent")
                if not b["loaded"] or b["loads"] <= old[tuple(p)]["loads"]: errors.append("missing endpoint reload event")
        for p in f["groups"][order[1]]:
            state = next(c for c in settled["chunks"] if c["pos"] == p)
            if state["loaded"]: errors.append("first loaded endpoint automatically loaded the other endpoint")
        for entry in first["cases"][f["id"]]:
            if (entry["pos"][0] >> 4, entry["pos"][2] >> 4) not in map(tuple, f["groups"][order[0]]) or "tileIdentity" not in entry: continue
            if at(loaded, f["id"], entry["pos"]).get("tileIdentity") != entry["tileIdentity"]:
                errors.append("first endpoint was replaced while loading the second")
        result[f["id"]] = verdict(sorted(set(errors)))
    return result


def qualification(result):
    groups = [result[k] for k in ("insufficient_energy", "moves", "blocked_output", "restart", "blocked_after_restart", "resumed_output", "redstone")]
    groups += [entry["cases"] for k in ("partial_networks", "reload_orders") for entry in result[k]]
    return "PASS" if result["network_processing"] == "PASS" and all(c["status"] == "PASS" for g in groups for c in g.values()) else "FAIL"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--migrated", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25587)
    run(parser.parse_args())
