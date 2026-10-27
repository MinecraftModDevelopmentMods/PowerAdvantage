"""All eighteen extended drill routes through mining, reload and saved-read guards."""

from pathlib import Path
import shutil
import time

import extended_lifecycle as historical
from matrix import digest
from native116_partial import campaign, common_parser


def exercise(runner, progress):
    protocol = Path(__file__)
    original_hash = digest(protocol)
    archive = runner.runtime / "native116-extended-reads-protocol.py"
    shutil.copy2(protocol, archive)
    runner.boot("extended-reads")
    runner.build()
    result = dict(status="RUNNING", protocolSha256=original_hash)
    for step in range(4):
        runner.probe("prime", f"prime-step-{step + 1}")
        runner.release_drills(step)
        moved = runner.wait_step(step + 1)
    result["moves"] = historical.movement_results(runner.definition, moved)
    runner.mine()
    time.sleep(45)
    result["mining"] = runner.mining_results(runner.probe("snapshot", "reads-mining-complete"))
    progress(result)
    print(runner.runtime.name + ": four moves and normal mining observed", flush=True)
    result["mid_block_progress"] = runner.midblock()
    progress(result)
    result["saved_read_guards"] = runner.save_read_guards()
    runner.command("save-all flush")
    runner.close()
    groups = [result["moves"], result["mining"], *result["mid_block_progress"].values(),
              *result["saved_read_guards"].values()]
    result["status"] = "PASS" if all(c["status"] == "PASS" for group in groups for c in group.values()) else "FAIL"
    result["own_protocol_unchanged"] = digest(protocol) == original_hash == digest(archive)
    if not result["own_protocol_unchanged"]:
        result["status"] = "FAIL"
    progress(result)
    return result


if __name__ == "__main__":
    args = common_parser(__doc__).parse_args()
    raise SystemExit(campaign(args, "extended-reads", historical.fixtures(), exercise))
