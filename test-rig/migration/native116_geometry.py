"""Forge 36 geometry contract; live agreement is required before any qualification."""

FORCED_LEVEL = 31
FULL_LEVEL = 33
MAX_DISTANCE = 11
LAST_HOLDER_LEVEL = FULL_LEVEL + MAX_DISTANCE
HOLDER_RADIUS = LAST_HOLDER_LEVEL - FORCED_LEVEL
SOURCE_SHA256 = "A1C8999617D792843C879C04E49F8658C299E5A423B4F44468F8F70961A68104"


def require_geometry(snapshot, forced=False):
    geometry = snapshot.get("ticketGeometry", {})
    expected = dict(fullLevel=FULL_LEVEL, entityTickingLevel=FORCED_LEVEL,
                    maxDistance=MAX_DISTANCE, lastHolderLevel=LAST_HOLDER_LEVEL,
                    holderRadius=HOLDER_RADIUS)
    if any(geometry.get(key) != value for key, value in expected.items()):
        raise AssertionError("Live native116 ticket geometry differs from the verified engine: " + str(geometry))
    levels = geometry.get("observedForcedLevels")
    if levels not in ([], [FORCED_LEVEL]) or forced and levels != [FORCED_LEVEL]:
        raise AssertionError("Actual native forced-ticket level was not verified: " + str(levels))
    return geometry
