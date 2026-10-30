"""Static Forge 36 ticket isolation, never a runtime qualification or log waiver.

Input is a complete, single-dimension ticket-map export, not fixture bounds or
observedForcedLevels alone. Each ticket records its map-key chunk position,
Ticket.getType().toString(), getTicketLevel(), and whether it remains active.
An optional forceTicks flag does not change its chunk dependency influence.
No ticket types, fixtures, namespaces, or distant ticket records are filtered.

Schema: format=1, dimension, ticketMapComplete=true, ticketGeometry (the existing
native six-field observation), tickets=[{pos:[x,z], type, level, active}].
G lacks the complete ticket records and therefore cannot pass this input gate.
Tests can supply explicitly modelled records; they do not become live evidence.

Native source: TicketManager.func_219364_a installs FORCED at 31;
ChunkDistanceGraph.func_215480_b adds one per eight-neighbour step; ChunkManager
retains holders through 33 + ChunkStatus.maxDistance() = 44. Thus distance is
Chebyshev, and a level-31 ticket influences a holder through distance 13 inclusive.
"""

import json
from pathlib import Path
import re


FORCED_LEVEL = 31
FULL_LEVEL = 33
LAST_HOLDER_LEVEL = 44
HOLDER_RADIUS = LAST_HOLDER_LEVEL - FORCED_LEVEL
_INT_MIN, _INT_MAX = -(2 ** 31), 2 ** 31 - 1
_GEOMETRY = dict(fullLevel=33, entityTickingLevel=31, maxDistance=11,
                 lastHolderLevel=44, holderRadius=13)


def _integer(value, low, high, label):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Invalid " + label)
    return value


def _position(value):
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError("Expected a two-integer chunk position")
    return tuple(_integer(n, _INT_MIN, _INT_MAX, "chunk coordinate") for n in value)


def _dimension(value):
    if not isinstance(value, str) or re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", value) is None:
        raise ValueError("Expected a namespaced dimension key")
    return value


def validate_ticket_snapshot(snapshot):
    """Fail closed on incomplete exports or unsupported native geometry."""
    fields = {"format", "dimension", "ticketMapComplete", "ticketGeometry", "tickets"}
    if not isinstance(snapshot, dict) or set(snapshot) != fields:
        raise ValueError("Expected exact complete native ticket-map schema")
    if type(snapshot["format"]) is not int or snapshot["format"] != 1:
        raise ValueError("Unsupported ticket-map format")
    _dimension(snapshot["dimension"])
    if snapshot["ticketMapComplete"] is not True:
        raise ValueError("Complete global dimension ticket map required")
    geometry = snapshot["ticketGeometry"]
    if not isinstance(geometry, dict) or set(geometry) != set(_GEOMETRY) | {"observedForcedLevels"}:
        raise ValueError("Missing native geometry observation")
    if any(type(geometry[k]) is not int or geometry[k] != v for k, v in _GEOMETRY.items()):
        raise ValueError("Native ticket geometry differs from Forge 36 contract")
    records = snapshot["tickets"]
    if not isinstance(records, list):
        raise ValueError("Expected a ticket record list")
    forced = set()
    required = {"pos", "type", "level", "active"}
    for record in records:
        if (not isinstance(record, dict) or not required <= set(record)
                or set(record) - required - {"forceTicks"}):
            raise ValueError("Invalid native ticket record fields")
        _position(record["pos"])
        if not isinstance(record["type"], str) or not record["type"].strip():
            raise ValueError("Missing ticket type; unknown types must not be discarded")
        _integer(record["level"], 0, _INT_MAX, "ticket level")
        if type(record["active"]) is not bool:
            raise ValueError("Explicit ticket active state required")
        if "forceTicks" in record and type(record["forceTicks"]) is not bool:
            raise ValueError("Invalid forceTicks state")
        if record["type"] == "forced":
            if record["level"] != FORCED_LEVEL:
                raise ValueError("Native FORCED ticket must have level 31")
            if record["active"]:
                forced.add(record["level"])
    observed = geometry["observedForcedLevels"]
    if (not isinstance(observed, list) or any(type(n) is not int for n in observed)
            or observed != sorted(forced)):
        raise ValueError("Observed forced levels disagree with complete ticket records")
    return snapshot


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field: " + key)
        result[key] = value
    return result


def _invalid_constant(value):
    raise ValueError("Non-finite JSON constant: " + value)


def read_ticket_snapshot(path):
    """Read actual exported records without inferring absent tickets or expiry."""
    with Path(path).open(encoding="utf-8") as stream:
        value = json.load(stream, object_pairs_hook=_unique_object, parse_constant=_invalid_constant)
    return validate_ticket_snapshot(value)


def isolation_precondition(snapshot, witnesses, dimension):
    """Evaluate every active ticket globally; ISOLATED is not an unload witness."""
    validate_ticket_snapshot(snapshot)
    if _dimension(dimension) != snapshot["dimension"]:
        raise ValueError("Witness dimension differs from ticket map")
    if not isinstance(witnesses, (list, tuple)) or not witnesses:
        raise ValueError("At least one explicit released witness is required")
    positions = [_position(pos) for pos in witnesses]
    if len(set(positions)) != len(positions):
        raise ValueError("Duplicate released witness")
    blockers = []
    for position in positions:
        for index, record in enumerate(snapshot["tickets"]):
            if not record["active"]:
                continue
            distance = max(abs(a - b) for a, b in zip(position, record["pos"]))
            effective = record["level"] + distance
            if effective <= LAST_HOLDER_LEVEL:
                blockers.append(dict(witness=list(position), ticketIndex=index,
                    ticketPos=list(record["pos"]), ticketType=record["type"],
                    ticketLevel=record["level"], distance=distance, effectiveLevel=effective,
                    fullDependency=effective <= FULL_LEVEL))
    return dict(status="BLOCKED" if blockers else "ISOLATED",
                scope="STATIC_TICKET_PRECONDITION_ONLY_NOT_RUNTIME_PASS",
                dimension=dimension, witnesses=[list(p) for p in positions], blockers=blockers)


def require_isolation(snapshot, witnesses, dimension):
    result = isolation_precondition(snapshot, witnesses, dimension)
    if result["blockers"]:
        raise AssertionError("Native holder isolation precondition failed: "
                             + json.dumps(result["blockers"], sort_keys=True))
    return result
