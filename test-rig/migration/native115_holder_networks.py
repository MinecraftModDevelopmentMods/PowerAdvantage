"""Real holder-unload fixtures outside Forge's full generation dependency halo."""

from pathlib import Path
import shutil

import extended_lifecycle as historical
from matrix import digest
import native115_extended
from native115_partial import campaign, common_parser, exercise_bridge, exercise_partial
from native115_ticket_networks import NativeTicketExtendedRun, NativeTicketNetworkRun


from native115_geometry import FORCED_LEVEL, LAST_HOLDER_LEVEL, HOLDER_RADIUS
ANCHOR_DISTANCE = 32


def holder_halo(fixture, group):
    return [[x, z] for x in range(fixture["min"][0] >> 4, (fixture["max"][0] >> 4) + 1)
            for z in range(fixture["min"][2] >> 4, (fixture["max"][2] >> 4) + 1)
            if any(max(abs(x-a), abs(z-b)) <= HOLDER_RADIUS for a, b in fixture["groups"][group])]


def linear_fixtures():
    result = []
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 32768 + index * 1024
        origin, sink = [base + 8, 80, 2056], [base + 520, 80, 2056]
        pipes = [[x, 80, 2056] for x in range(base + 9, base + 520)]
        f = dict(id=name, nativeTicketNetwork=True, min=[base, 72, 2048], max=[base + 543, 90, 2079],
                 witnesses=[origin, *pipes[:3], sink, *pipes[3:]], pipes=pipes,
                 groups=dict(supply=[[base >> 4, 128]], machine=[[(base >> 4) + ANCHOR_DISTANCE, 128]]))
        for group in ("supply", "machine"):
            f["groups"][group + "halo"] = holder_halo(f, group)
        result.append(f)
    return result


def bridge_fixtures():
    result = []
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 36864 + index * 1024
        origin, sink = [base + 13, 80, 2056], [base + 9, 80, 2056]
        path = [[base + 13, 80, z] for z in range(2057, 2569)]
        path += [[base + x, 80, 2568] for x in range(12, 8, -1)]
        path += [[base + 9, 80, z] for z in range(2567, 2056, -1)]
        f = dict(id=name, nativeTicketNetwork=True, min=[base, 72, 2048], max=[base + 31, 90, 2591],
                 witnesses=[origin, sink, *path], pipes=path,
                 groups=dict(bridge=[[base >> 4, 160]], endpoints=[[base >> 4, 128]]))
        f["groups"]["bridgehalo"] = holder_halo(f, "bridge")
        result.append(f)
    return result


def extended_fixtures():
    return [f for f in historical.fixtures() if "route" in f] + linear_fixtures()


def main():
    parser = common_parser(__doc__)
    parser.add_argument("--campaign", choices=("partial", "bridge", "extended"), required=True)
    args = parser.parse_args()
    definitions = {"partial": linear_fixtures, "bridge": bridge_fixtures, "extended": extended_fixtures}
    operations = {"partial": exercise_partial, "bridge": exercise_bridge, "extended": native115_extended.exercise}
    runner_type = NativeTicketNetworkRun if args.campaign == "bridge" else NativeTicketExtendedRun
    own, own_hash = Path(__file__), digest(Path(__file__))
    transport = Path(__file__).with_name("native115_ticket_networks.py")
    transport_hash = digest(transport)
    def exercise(runner, progress):
        shutil.copy2(own, runner.runtime / own.name)
        shutil.copy2(transport, runner.runtime / transport.name)
        result = operations[args.campaign](runner, progress)
        result.update(holder_protocol_sha256=own_hash, holder_transport_sha256=transport_hash,
                      holder_protocol_unchanged=(digest(own) == own_hash == digest(runner.runtime / own.name)
                        and digest(transport) == transport_hash == digest(runner.runtime / transport.name)))
        if not result["holder_protocol_unchanged"]:
            result["status"] = "FAIL"
        progress(result)
        return result
    return campaign(args, "holder-" + args.campaign, definitions[args.campaign](), exercise, runner_type)


if __name__ == "__main__":
    raise SystemExit(main())
