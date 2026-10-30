"""Observe native chunk load completion, never infer readiness from machine output."""

from pathlib import Path
import time
import json


def validate(document, label, low, high):
    expected = {(x, z) for x in range(low[0] >> 4, (high[0] >> 4) + 1)
                for z in range(low[2] >> 4, (high[2] >> 4) + 1)}
    rows = document.get("chunks", [])
    if (not expected or document.get("label") != label or type(document.get("worldTime")) is not int
            or len(rows) != len(expected) or {(r["x"], r["z"]) for r in rows} != expected
            or any(type(r.get("loaded")) is not bool for r in rows)
            or type(document.get("ready")) is not bool
            or document["ready"] != all(r["loaded"] for r in rows)):
        raise AssertionError("Invalid or incomplete native chunk-readiness witness")
    return document["ready"]


def acquire(rig):
    bounds = {key: (rig.pos(key, value["boundsMin"]), rig.pos(key, value["boundsMax"]))
              for key, value in rig.definitions.items()}
    for low, high in bounds.values():
        rig.command(f"forceload add {low[0]} {low[2]} {high[0]} {high[2]}")
    pending = set(bounds)
    deadline = time.monotonic() + 30
    for attempt in range(80):
        for key in sorted(pending):
            low, high = bounds[key]
            label = f"prebuild-{key}-{attempt:02}"
            rig.command(f"advchunks {label} {low[0]} {low[2]} {high[0]} {high[2]}", "ADVCHUNKS OBSERVED " + label)
            path = rig.runtime / "advantage-works-results" / ("chunk-readiness-" + label + ".json")
            if validate(json.loads(path.read_text(encoding="utf-8")), label, low, high): pending.remove(key)
        if not pending: return
        if time.monotonic() >= deadline: break
        time.sleep(.05)
    raise TimeoutError("Native chunks did not finish loading before construction: " + str(sorted(pending)))
