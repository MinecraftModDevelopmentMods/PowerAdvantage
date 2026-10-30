"""Pure campaign status builder, staged for a cleared writer-only repair.

Adding this module does not change any live runner. The independent offline
auditor remains authoritative for raw checks, pins, and shutdown evidence.
"""

from copy import deepcopy


PROFILES = {"packaged-current", "packaged-decoupled"}
PHASES = {"initial", "first", "operation", "restarted", "persistence"}


def _profile_complete(name, result):
    if not isinstance(result, dict) or result.get("profile") != name or result.get("status") != "PASS":
        return False
    if result.get("error") or result.get("shutdownError"):
        return False
    phases = result.get("phases", {})
    if set(phases) != PHASES or any(not isinstance(row, dict) or row.get("status") != "PASS" for row in phases.values()):
        return False
    before, after = result.get("beforeRestart"), result.get("afterRestart")
    if not isinstance(before, dict) or len(before) != 12 or before != after:
        return False
    if len(phases["initial"].get("drills", {})) != 12:
        return False
    if phases["operation"].get("drills") != 12 or phases["operation"].get("moved", 0) < 2:
        return False
    if phases["persistence"].get("drills") != 12 or phases["persistence"].get("exactEnergy") is not True:
        return False
    for phase in ("first", "restarted"):
        counts = phases[phase].get("assertions", {})
        if (not isinstance(counts, dict) or not counts or set(counts) - {"PASS", "SKIP"}
                or any(type(value) is not int or value < 0 for value in counts.values()) or counts.get("PASS", 0) <= 0):
            return False
        manual = phases[phase].get("manualRequired")
        if not isinstance(manual, list) or len(manual) != 21 or len(set(manual)) != 21:
            return False
    return phases["first"] == phases["restarted"]


def profile_complete(name, result):
    try:
        return _profile_complete(name, result)
    except (TypeError, KeyError, ValueError, AttributeError):
        return False


def campaign_status(requested, results, finished=False):
    requested = tuple(requested)
    if not requested or len(set(requested)) != len(requested) or not set(requested) <= PROFILES:
        raise ValueError("An explicit, unique requested profile set is required")
    if not isinstance(results, dict) or set(results) - set(requested):
        return "FAIL"
    if any(not profile_complete(name, result) for name, result in results.items()):
        return "FAIL"
    if set(results) != set(requested):
        return "FAIL" if finished else "RUNNING"
    return "PASS"


def campaign_document(requested, results, metadata, finished=False):
    """Only status is recomputed; immutable metadata and profile evidence remain exact."""
    if set(metadata) & {"status", "profiles"}:
        raise ValueError("Metadata may not supply status or profile results")
    document = deepcopy(metadata)
    document.update(status=campaign_status(requested, results, finished), profiles=deepcopy(results))
    return document
