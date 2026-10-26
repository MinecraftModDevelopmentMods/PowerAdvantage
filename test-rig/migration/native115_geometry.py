"""Forge 31 geometry from exact installed sources, checked against live engine data."""

FORCED_LEVEL = 31
FULL_LEVEL = 33
MAX_DISTANCE = 11
LAST_HOLDER_LEVEL = FULL_LEVEL + MAX_DISTANCE
HOLDER_RADIUS = LAST_HOLDER_LEVEL - FORCED_LEVEL
SOURCE_SHA256 = "937F0B7D7098D4D1A12EA306108122B1E443CA638D120D9040FF2B577E9A0314"


def require_geometry(snapshot, forced=False):
    geometry = snapshot.get("ticketGeometry", {})
    expected = dict(fullLevel=FULL_LEVEL, entityTickingLevel=FORCED_LEVEL,
                    maxDistance=MAX_DISTANCE, lastHolderLevel=LAST_HOLDER_LEVEL,
                    holderRadius=HOLDER_RADIUS)
    if any(geometry.get(key) != value for key, value in expected.items()):
        raise AssertionError("Live native115 ticket geometry differs from the verified engine: " + str(geometry))
    levels = geometry.get("observedForcedLevels")
    if levels not in ([], [FORCED_LEVEL]) or forced and levels != [FORCED_LEVEL]:
        raise AssertionError("Actual native forced-ticket level was not verified: " + str(levels))
    return geometry
