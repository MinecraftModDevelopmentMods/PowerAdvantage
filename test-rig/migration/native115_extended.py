"""Four native moves, partial networks, blocked output, restart and redstone tests."""

import time

import extended_lifecycle as historical
from native115_partial import NativeExtendedRun, campaign, common_parser, orders


def exercise(runner, progress):
    definition = runner.definition
    runner.boot("extended")
    runner.build()
    runner.probe("trace-loads", "trace-enabled")
    result = dict(status="RUNNING")
    low = runner.probe("low-prime", "low-before")
    runner.release_drills(0)
    time.sleep(2)
    stalled = runner.probe("snapshot", "low-stalled")
    result["insufficient_energy"] = historical.reserve_results(definition, low, stalled)
    runner.hold_drills(0)
    for step in range(4):
        runner.probe("prime", f"prime-step-{step + 1}")
        runner.release_drills(step)
        moved = runner.wait_step(step + 1)
        print(f"{runner.runtime.name}: native drill step {step + 1}/4 observed", flush=True)
    result["moves"] = historical.movement_results(definition, moved)
    progress(result)
    time.sleep(50)
    runner.processing("network-commissioned", 1000)
    result["partial_networks"] = []
    for cycle, group in enumerate(("machine", "supply", "supply", "machine"), 1):
        result["partial_networks"].append(runner.partial(group, cycle))
        runner.replenish()
        runner.probe("offer", f"partial-fluid-{cycle}")
        time.sleep(50)
        runner.processing(f"partial-resumed-{cycle}", (cycle + 1) * 1000)
        print(f"{runner.runtime.name}: {group}-only unload {cycle} and processing recorded", flush=True)
        progress(result)
    result["reload_orders"] = orders(runner, process=True)
    runner.install_blocked_output()
    time.sleep(45)
    blocked = runner.probe("snapshot", "blocked-output")
    result["blocked_output"] = historical.output_results(definition, blocked, True)
    runner.command("save-all flush")
    runner.close()
    runner.boot("blocked-restart")
    time.sleep(1)
    retained = runner.probe("snapshot", "blocked-retained")
    result["restart"] = historical.restart_results(definition, blocked, retained)
    result["blocked_after_restart"] = historical.output_results(definition, retained, True)
    runner.freed_output()
    time.sleep(20)
    result["resumed_output"] = historical.output_results(definition, runner.probe("snapshot", "freed-output"), False)
    for f in definition:
        if "route" in f:
            runner.target_plane(f, "minecraft:obsidian")
    time.sleep(4)
    before = runner.probe("snapshot", "redstone-before")
    runner.hold_drills(4)
    time.sleep(2)
    held = runner.probe("snapshot", "redstone-held")
    runner.release_drills(4)
    time.sleep(4)
    after = runner.probe("snapshot", "redstone-resumed")
    result["redstone"] = historical.redstone_results(definition, before, held, after)
    runner.hold_drills(4)
    runner.command("save-all flush")
    runner.close()
    result["network_processing"] = "PASS"
    result["status"] = historical.qualification(result)
    progress(result)
    return result


def main():
    parser = common_parser(__doc__)
    args = parser.parse_args()
    return campaign(args, "extended", historical.fixtures(), exercise, NativeExtendedRun)


if __name__ == "__main__":
    raise SystemExit(main())
