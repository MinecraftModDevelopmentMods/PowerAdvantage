"""Empty-bay control for native partial-unload fixture ticket interference."""

import time

import extended_lifecycle
from native116_partial import campaign, common_parser, orders


def exercise(runner, progress):
    runner.boot("partial-empty-control")
    runner.probe("load", "loaded")
    for f in runner.definition:
        runner.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
    runner.probe("trace-loads", "trace-enabled")
    result = dict(status="RUNNING", purpose="empty bays; no Advantage networks or machines constructed", partial_networks=[])
    for cycle, group in enumerate(("machine", "supply"), 1):
        result["partial_networks"].append(runner.partial(group, cycle))
        progress(result)
    result["reload_orders"] = orders(runner)
    result["status"] = "PASS" if all(c["status"] == "PASS" for r in
        result["partial_networks"] + result["reload_orders"] for c in r["cases"].values()) else "FAIL"
    runner.command("save-all flush")
    runner.close()
    progress(result)
    return result


if __name__ == "__main__":
    args = common_parser(__doc__).parse_args()
    raise SystemExit(campaign(args, "empty-control", [f for f in extended_lifecycle.fixtures() if "groups" in f], exercise))
