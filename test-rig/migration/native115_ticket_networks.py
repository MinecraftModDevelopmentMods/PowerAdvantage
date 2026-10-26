"""Ticket-halo-aware native network layouts; historical oracles remain unchanged."""

from pathlib import Path
import json
import shutil
import time

import extended_lifecycle as historical
from matrix import digest, write_json
from native115 import RIG, jar_identity, output_path, pinned_profile, require_disjoint
import native115_extended
from native115_partial import (NativeExtendedRun, NativePartialRun, campaign,
                               common_parser, exercise_bridge, exercise_partial,
                               selected_chunks)


def halo_chunks(fixture, group):
    cores = fixture["groups"][group]
    return [[x, z] for x in range(fixture["min"][0] >> 4, (fixture["max"][0] >> 4) + 1)
            for z in range(fixture["min"][2] >> 4, (fixture["max"][2] >> 4) + 1)
            if any(max(abs(x - a), abs(z - b)) <= 2 for a, b in cores)]


def linear_fixtures():
    result = []
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 20480 + index * 512
        origin, sink = [base + 8, 80, 2056], [base + 136, 80, 2056]
        pipes = [[x, 80, 2056] for x in range(base + 9, base + 136)]
        fixture = dict(id=name, nativeTicketNetwork=True, min=[base, 72, 2048], max=[base + 159, 90, 2079],
                       witnesses=[origin, *pipes[:3], sink, *pipes[3:]], pipes=pipes,
                       groups=dict(supply=[[base >> 4, 128]], machine=[[(base >> 4) + 8, 128]]))
        for group in ("supply", "machine"):
            fixture["groups"][group + "halo"] = halo_chunks(fixture, group)
        result.append(fixture)
    return result


def bridge_fixtures():
    result = []
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 24576 + index * 512
        origin, sink = [base + 13, 80, 2056], [base + 9, 80, 2056]
        path = [[base + 13, 80, z] for z in range(2057, 2185)]
        path += [[base + x, 80, 2184] for x in range(12, 8, -1)]
        path += [[base + 9, 80, z] for z in range(2183, 2056, -1)]
        fixture = dict(id=name, nativeTicketNetwork=True, min=[base, 72, 2048], max=[base + 31, 90, 2207],
                       witnesses=[origin, sink, *path], pipes=path,
                       groups=dict(bridge=[[base >> 4, 136]], endpoints=[[base >> 4, 128]]))
        fixture["groups"]["bridgehalo"] = halo_chunks(fixture, "bridge")
        result.append(fixture)
    return result


def extended_fixtures():
    return [f for f in historical.fixtures() if "route" in f] + linear_fixtures()


def archive_harness(base_manifest, harness, output):
    """Generate a new immutable input set, never editing its parent manifest."""
    output = output_path(output)
    require_disjoint(output, base_manifest)
    require_disjoint(output, harness)
    pinned_profile(base_manifest.resolve(), "target115")
    definition = json.loads(base_manifest.read_text())
    entry = next(j for j in definition["target115"]["jars"] if j["id"] == "advantageworkstest")
    jar_identity(harness, entry["id"], entry["version"])
    checksum = digest(harness)
    archive = RIG / "build/native115-artifacts" / checksum / harness.name
    archive.parent.mkdir(parents=True, exist_ok=True)
    if archive.exists():
        if digest(archive) != checksum:
            raise AssertionError("Immutable harness archive changed")
    else:
        shutil.copy2(harness, archive)
    if digest(archive) != checksum or digest(harness) != checksum:
        raise AssertionError("Harness changed during archive staging")
    entry.update(path=str(archive.resolve()), sha256=checksum, originPath=str(harness.resolve()))
    definition["target115"].update(sourceManifest=str(base_manifest.resolve()), sourceManifestSha256=digest(base_manifest))
    write_json(output, definition)
    pinned_profile(output, "target115")
    return output


def fill_commands(definition, command):
    parts = command.split()
    if len(parts) != 9 or parts[0] != "fill" or parts[7:] != ["minecraft:air", "replace"]:
        return None
    minimum, maximum = list(map(int, parts[1:4])), list(map(int, parts[4:7]))
    f = next((f for f in definition if f.get("nativeTicketNetwork") and f["min"] == minimum and f["max"] == maximum), None)
    if f is None:
        return None
    result = []
    for x in range(minimum[0], maximum[0] + 1, 16):
        for z in range(minimum[2], maximum[2] + 1, 16):
            low = [x, minimum[1], z]
            high = [min(x + 15, maximum[0]), maximum[1], min(z + 15, maximum[2])]
            result.append("fill " + " ".join(map(str, low + high)) + " minecraft:air replace")
    return result


class NativeTicketNetworkRun(NativePartialRun):
    def command(self, command):
        pieces = fill_commands(self.definition, command)
        for piece in pieces if pieces is not None else [command]:
            super().command(piece)

    def probe(self, operation, label):
        if operation.startswith("release-"):
            group = operation[8:]
            if selected_chunks(self.definition, group + "halo"):
                before = self._raw_probe("snapshot", label + "-native-before")
                self._raw_probe("release-" + group + "halo", label + "-native-halo")
                result = self._raw_probe(operation, label)
                self._released_groups[group] = dict(before=before, selected=selected_chunks(self.definition, group),
                                                    deadline=time.monotonic() + 45)
                return result
        if operation.startswith("load-") and selected_chunks(self.definition, operation[5:] + "halo"):
            self._raw_probe(operation + "halo", label + "-native-halo")
        return super().probe(operation, label)


class NativeTicketExtendedRun(NativeTicketNetworkRun, NativeExtendedRun):
    def build(self):
        self.probe("load", "loaded")
        for f in self.definition:
            self.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
            if "route" in f:
                self.block(f["controls"][0], "minecraft:redstone_block")
                self.block(f["from"], f["block"] + "[facing=" + f["facing"] + "]")
                for pos in f["route"][1:] + f.get("alternatives", []):
                    self.block(pos, f["block"].replace("_drill", "_track"))
                for pos in f["route"]:
                    self.block(historical.offset(pos, f["direction"]), "minecraft:bedrock")
                self.inventory(f["from"], 0, 'minecraft:dirt{AdvWorksWitness:"' + f["id"] + '"}', 7)
                self.inventory(f["from"], 1, "minecraft:apple", 13)
                continue
            fluid, steam = f["id"] == "C-FLUID", f["id"] == "C-STEAM"
            pipe = "poweradvantage:fluid_pipe" if fluid else "steamadvantage:steam_pipe" if steam else "electricadvantage:electric_conduit"
            self.block(f["witnesses"][0], pipe if fluid else "poweradvantage:infinite_" + ("steam" if steam else "electricity"))
            for pos in f["pipes"]:
                self.block(pos, pipe)
            self.block(f["witnesses"][4], "poweradvantage:fluid_metal_tank" if fluid else
                       "steamadvantage:steam_furnace" if steam else "electricadvantage:electric_furnace")
            if not fluid:
                if steam:
                    self.inventory(f["witnesses"][4], 0, "minecraft:coal", 64)
                self.inventory(f["witnesses"][4], 1 if steam else 0, "minecraft:iron_ore", 4)
        self.probe("offer", "commission-fluid")
        self.probe("watch", "watching")


def main():
    parser = common_parser(__doc__)
    parser.add_argument("--campaign", choices=("partial", "bridge", "extended"), required=True)
    args = parser.parse_args()
    definition = {"partial": linear_fixtures, "bridge": bridge_fixtures, "extended": extended_fixtures}[args.campaign]()
    operation = {"partial": exercise_partial, "bridge": exercise_bridge, "extended": native115_extended.exercise}[args.campaign]
    runner_type = NativeTicketNetworkRun if args.campaign == "bridge" else NativeTicketExtendedRun
    own_path, own_hash = Path(__file__), digest(Path(__file__))
    def exercise(runner, progress):
        archive = runner.runtime / "native115-ticket-network-protocol.py"
        shutil.copy2(own_path, archive)
        result = operation(runner, progress)
        result["native_ticket_protocol_unchanged"] = digest(own_path) == own_hash == digest(archive)
        result["native_ticket_protocol_sha256"] = own_hash
        if not result["native_ticket_protocol_unchanged"]:
            result["status"] = "FAIL"
        progress(result)
        return result
    return campaign(args, "halo-" + args.campaign, definition, exercise, runner_type)


if __name__ == "__main__":
    raise SystemExit(main())
