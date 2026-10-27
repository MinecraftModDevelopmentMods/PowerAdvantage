"""Separate, pinned native-wolf difference assessment; raw strict FAIL stays FAIL."""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import uuid

import dimensions
from native116_dimension_io import semantic_modifier_representation


CONTROL_LOG_SHA256 = "30DD31976514057A5F68C0FF115A7B66A936FFE962B82D8FA67BBAEADAA9CE5D"
HEALTH_PATH = ("entities", "passenger-wolf", "Health")
HEALTH_DIFFERENCE = {"path": ".entities.passenger-wolf.Health", "expected": 8.0, "actual": 20.0}


def load_native_proof(path):
    raw = Path(path).read_bytes()
    checksum = hashlib.sha256(raw).hexdigest().upper()
    if checksum != CONTROL_LOG_SHA256:
        raise AssertionError("Native wolf proof differs from the independently reviewed control log")
    text = raw.decode("utf-8")
    if "POWER_LEGACY_CLASS_ABSENT true" not in text or "WOLF_CONTROL_DONE no_server=true no_ticks=true native_entity_methods_unmodified=true" not in text:
        raise AssertionError("Native no-Power control attestation missing")
    records = {}
    for line in text.splitlines():
        if line.startswith("WOLF_CONTROL "):
            row = json.loads(line.removeprefix("WOLF_CONTROL "))
            if row["label"] in records:
                raise AssertionError("Duplicate controlled wolf observation")
            records[row["label"]] = row
    expected = {"tame-health-8": (8.0, 20.0, 20.0), "tame-health-20": (20.0, 20.0, 20.0),
                "wild-health-8": (8.0, 8.0, 8.0)}
    if set(records) != set(expected):
        raise AssertionError("Incomplete native wolf controls")
    for name, health in expected.items():
        row = records[name]
        if (row.get("currentWorldDataVersion") != 2586 or row.get("legacyDfuInvoked") is not False
                or row.get("directNativeEntityLoadSave") is not True or row.get("uuidPreserved") is not True
                or row.get("ownerPreserved") is not True
                or tuple(row.get(key) for key in ("beforeHealth", "afterHealth", "restartHealth")) != health):
            raise AssertionError("Native wolf control values differ")
    return {"sha256": checksum, "worldDataVersion": 2586, "forgeVersion": "36.2.34",
            "scope": "NATIVE_TAMED_WOLF_LOAD_8_TO_20_NO_POWER"}


def exact_except_native_health(expected, actual, path=()):
    if path == HEALTH_PATH:
        return type(expected) in (int, float) and type(actual) in (int, float) and expected == 8 and actual == 20
    if type(expected) is not type(actual):
        return False
    if isinstance(expected, dict):
        return set(expected) == set(actual) and all(exact_except_native_health(value, actual[key], path + (key,))
                                                  for key, value in expected.items())
    if isinstance(expected, list):
        return len(expected) == len(actual) and all(exact_except_native_health(old, new, path + (index,))
                                                   for index, (old, new) in enumerate(zip(expected, actual)))
    return expected == actual


def semantic_player_spawn_defaults(state):
    """Expose only the proven absent player respawn dimension's Overworld default."""
    result = copy.deepcopy(state)
    for root in ("file", "adapter"):
        player = result.get(root)
        if (isinstance(player, dict) and "SpawnDimension" not in player
                and all(type(player.get(key)) is int and -(1 << 31) <= player[key] < (1 << 31)
                        for key in ("SpawnX", "SpawnY", "SpawnZ"))):
            player["SpawnDimension"] = 0
    return result


def _scope(value, scope, adapter):
    if scope == "exact":
        result = copy.deepcopy(value)
    elif scope == "strict":
        result = dimensions.strict_expected(value, adapter)
    elif scope == "survival":
        result = dimensions.survival_expected(value)
        if not adapter:
            result.pop("adapter", None)
            result.pop("playerWorldDimension", None)
    else:
        raise ValueError("Unknown historical comparison scope")
    return semantic_player_spawn_defaults(semantic_modifier_representation(result))


def assess(expected, actual, strict, proof_path=None, scope="exact", adapter=True):
    recomputed = dimensions.verdict(expected, actual)
    if strict != recomputed:
        raise AssertionError("Supplied raw strict result differs from the unchanged historical oracle")
    result = {"status": strict["status"], "rawStrictStatus": strict["status"],
              "rawStrictResult": copy.deepcopy(strict), "nativeDifferences": []}
    if strict["status"] == "PASS":
        return result
    if strict.get("differences") != [HEALTH_DIFFERENCE] or proof_path is None:
        return result
    before = expected.get("entities", {}).get("passenger-wolf", {})
    after = actual.get("entities", {}).get("passenger-wolf", {})
    if before.get("id") != "minecraft:wolf" or after.get("id") != "minecraft:wolf":
        return result
    for key in ("UUIDMost", "UUIDLeast"):
        old, new = before.get(key), after.get(key)
        if (type(old) is not int or type(new) is not int or not -(1 << 63) <= old < (1 << 63)
                or not -(1 << 63) <= new < (1 << 63) or old != new):
            return result
    try:
        owner = str(uuid.UUID(before["OwnerUUID"]))
        if before["OwnerUUID"] != owner or after.get("OwnerUUID") != owner:
            return result
    except (KeyError, ValueError, TypeError, AttributeError):
        return result
    if before.get("Attributes", {}).get("generic.maxHealth", {}).get("Base") != 20:
        return result
    old = _scope(expected, scope, adapter)
    new = _scope(actual, scope, adapter)
    if not exact_except_native_health(old, new):
        return result
    proof = load_native_proof(proof_path)
    result.update(status="PASS_WITH_NATIVE_DIFFERENCE", allOtherFieldsStrictMatch=True,
                  comparisonScope=scope, controlledProof=proof, nativeDifferences=[dict(HEALTH_DIFFERENCE, entityUuid={
                      key: before[key] for key in ("UUIDMost", "UUIDLeast")}, ownerUuid=owner)])
    return result


SEMANTIC_CHECKS = frozenset(("conversion", "adapter", "restart", "idempotence", "runtime", "runtime-restart"))
DIMENSION_CHECKS = SEMANTIC_CHECKS | {"conversion-ticks", "runtime-ticks", "restart-ticks", "backup-stability"}


def summarize_checks(checks, assessments):
    raw = "PASS" if checks and all(value.get("status") == "PASS" for value in checks.values()) else "FAIL"
    result = {"status": "FAIL", "rawStrictStatus": raw, "allOtherFieldsStrictMatch": False}
    if set(checks) != DIMENSION_CHECKS or set(assessments) != SEMANTIC_CHECKS:
        return result
    differences = []
    for key in sorted(checks):
        check = checks[key]
        assessment = assessments.get(key)
        if assessment is not None:
            if assessment.get("rawStrictResult") != check:
                return result
            if assessment.get("status") == "PASS_WITH_NATIVE_DIFFERENCE":
                native = assessment.get("nativeDifferences", [])
                if (check.get("status") != "FAIL" or assessment.get("allOtherFieldsStrictMatch") is not True
                        or assessment.get("controlledProof", {}).get("sha256") != CONTROL_LOG_SHA256
                        or len(native) != 1 or {key: native[0].get(key) for key in HEALTH_DIFFERENCE} != HEALTH_DIFFERENCE
                        or any(type(native[0].get("entityUuid", {}).get(key)) is not int
                               or not -(1 << 63) <= native[0]["entityUuid"][key] < (1 << 63)
                               for key in ("UUIDMost", "UUIDLeast"))):
                    return result
                differences.extend(dict(difference, check=key) for difference in assessment["nativeDifferences"])
            elif assessment.get("status") != "PASS" or check.get("status") != "PASS":
                return result
        elif check.get("status") != "PASS":
            return result
    result.update(status="PASS_WITH_NATIVE_DIFFERENCE" if differences else "PASS",
                  allOtherFieldsStrictMatch=True, nativeDifferences=differences)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("expected", "actual", "strict", "proof", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--scope", choices=("exact", "strict", "survival"), default="exact")
    parser.add_argument("--without-adapter", action="store_true")
    args = parser.parse_args(argv)
    if args.output.exists():
        raise FileExistsError("Never overwrite strict results or retained evidence")
    if any(args.output.resolve() == path.resolve() for path in (args.expected, args.actual, args.strict, args.proof)):
        raise ValueError("Assessment output overlaps a protected input")
    read = lambda path: json.loads(path.read_text(encoding="utf-8"))
    result = assess(read(args.expected), read(args.actual), read(args.strict), args.proof,
                    args.scope, not args.without_adapter)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(result["status"] + "; raw strict result remains " + result["rawStrictStatus"])
    return int(result["status"] == "FAIL")


if __name__ == "__main__":
    raise SystemExit(main())
