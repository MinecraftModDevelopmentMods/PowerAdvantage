"""Offline, fail-closed audit of a complete native broad campaign.

The historical writer can say PASS after only its first requested profile.
Never use that progress status alone as a campaign result. This auditor does
not import, modify, launch, or repair a broad runner or a Minecraft world.
"""

import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import re
import struct
import zipfile


PROFILES = ("packaged-current", "packaged-decoupled")
VERSIONS = {"113": ("1.13.2", "25.0.223"), "114": ("1.14.4", "28.2.26"),
            "115": ("1.15.2", "31.2.57"), "116": ("1.16.5", "36.2.34")}
STATIONS = tuple(["W-%02d" % n for n in range(1, 5)]
                 + ["S-%02d" % n for n in range(1, 9)]
                 + ["E-%02d" % n for n in range(1, 9)] + ["P-API-01"])
RESOURCE = "assets/advantageworkstest/works/works-1.13.2.json"
DIRECTIONS = ("down", "up", "north", "south", "west", "east")
REAR = ((0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1), (1, 0, 0), (-1, 0, 0))
PHASES = {"initial", "first", "operation", "restarted", "persistence"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result


def decode(data):
    return json.loads(data.decode("utf-8-sig"), object_pairs_hook=unique_object)


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


class Evidence:
    def __init__(self):
        self.hashes = {}

    def read(self, path):
        path = Path(path).resolve()
        data = path.read_bytes()
        digest = sha(data)
        previous = self.hashes.setdefault(str(path), digest)
        require(previous == digest, "Evidence changed while auditing: " + str(path))
        return data

    def json(self, path):
        return decode(self.read(path))

    def unchanged(self):
        for path, expected in self.hashes.items():
            require(sha(Path(path).read_bytes()) == expected, "Evidence changed while auditing: " + path)


def indexed(rows, key, label):
    require(isinstance(rows, list), label + ": missing list")
    result = {}
    for row in rows:
        require(isinstance(row, dict) and key in row, label + ": malformed row")
        require(row[key] not in result, label + ": duplicate " + str(row[key]))
        result[row[key]] = row
    return result


def require_profiles(actual, requested):
    require(set(actual) == set(requested),
            "Requested profile set incomplete or unexpected: requested=" + str(sorted(requested))
            + "; reported=" + str(sorted(actual)))


def raw_checks(records, definitions, mods, minecraft, forge):
    require(set(records) == set(STATIONS), "Raw station set must contain all 21 bays")
    counts, manual = Counter(), []
    for sid in STATIONS:
        spec, row = definitions[sid], records[sid]
        require(row["station"] == sid and row["minecraft"] == minecraft and row["forge"] == forge,
                sid + ": wrong native station/engine identity")
        require(row["mods"] == mods, sid + ": native loaded mod set differs from pins")
        require(row.get("evidenceScope") == "declared_assertions_only; manual procedures are not automated passes",
                sid + ": missing bounded evidence scope")
        require(row["manualChecks"] == spec.get("manualChecks", []), sid + ": manual checks omitted or changed")
        expected = indexed(spec["assertions"], "id", sid + " definition")
        actual = indexed(row["assertions"], "id", sid + " raw assertions")
        require(set(actual) == set(expected), sid + ": raw assertion set incomplete")
        require(not set(spec.get("requiredMods", [])) - set(mods), sid + ": required station mod absent")
        passed = 0
        for aid, assertion in expected.items():
            result = actual[aid]
            require(result["type"] == assertion["type"], sid + "/" + aid + ": assertion type changed")
            missing = set(assertion.get("requiredMods", [])) - set(mods)
            status = "SKIP" if missing else "PASS"
            require(result["status"] == status, sid + "/" + aid + ": wrong raw result or unexplained skip")
            detail = "optional integration is absent" if missing else "matched"
            require(result.get("detail") == detail, sid + "/" + aid + ": raw check detail contradicts result")
            counts[status] += 1
            passed += status == "PASS"
        classification = ("MANUAL_REQUIRED" if spec.get("manualChecks") else "PASS") if passed else "SKIP"
        require(row["status"] == classification, sid + ": manual/skip classification masked")
        if spec.get("manualChecks"):
            manual.append(sid)
        snapshots = row.get("snapshot")
        require(isinstance(snapshots, list) and snapshots, sid + ": missing raw snapshot")
        positions = [tuple(item["pos"]) for item in snapshots]
        require(len(set(positions)) == len(positions), sid + ": duplicate raw snapshot position")
    return dict(status="PASS", assertions=dict(counts), manualRequired=manual)


def drill_snapshot(records, definitions, initial=False):
    # Recompute the twelve witnesses from native SNBT, not summary PASS counts.
    import nbtlib
    result = {}
    for sid, block, track, capacity in (
            ("S-05", "steamadvantage:steam_drill", "steamadvantage:steam_track", 50),
            ("E-06", "electricadvantage:electric_drill", "electricadvantage:electric_track", 500)):
        spec = definitions[sid]
        snapshot = records[sid]["snapshot"]
        rows = {tuple(row["pos"]): row for row in snapshot}
        require(len(rows) == len(snapshot), sid + ": duplicate drill position")
        stock = Counter()
        for row in snapshot:
            data = nbtlib.parse_nbt(row["nbt"]) if "nbt" in row else {}
            for item in data.get("Items", []):
                marker = item.get("tag", {}).get("AdvBayStock")
                if marker:
                    require(str(item["id"]) == "minecraft:diamond", sid + ": witness item identity changed")
                    stock[str(marker)] += int(item["Count"])
        found, facings = set(), set()
        for assertion in spec["assertions"]:
            if assertion.get("block") != block:
                continue
            facing = (DIRECTIONS[assertion["meta"]] if "meta" in assertion
                      else assertion.get("expectedText", "down"))
            direction = DIRECTIONS.index(facing)
            origin = tuple(a + b for a, b in zip(spec["origin"], assertion["pos"]))
            rear = tuple(a + b for a, b in zip(origin, REAR[direction]))
            matches = [p for p in (origin, rear) if rows.get(p, {}).get("block") == block]
            key = sid + ":" + assertion["id"]
            require(len(matches) == 1, key + ": missing/duplicate native drill")
            pos = matches[0]
            if initial:
                require(pos == origin and rows.get(rear, {}).get("block") == track,
                        key + ": initial origin/track changed")
            row = rows[pos]
            require(re.search(r"(?:\[|,|^)facing=" + facing + r"(?:,|\]|$)", row["state"]),
                    key + ": native facing changed")
            data = nbtlib.parse_nbt(row["nbt"])
            require(str(data.get("ForgeData", {}).get("AdvBayWitness")) == key,
                    key + ": native tile witness missing")
            require(tuple(int(data[a]) for a in ("x", "y", "z")) == pos, key + ": native coordinates changed")
            require(stock[key] == 7, key + ": native stock lost/duplicated")
            bits = [int(value) for value in data["Energy"]]
            require(len(bits) == 1, key + ": missing native energy buffer")
            energy = struct.unpack("!f", struct.pack("!I", bits[0] & 0xffffffff))[0]
            require(math.isfinite(energy) and 0 <= energy <= capacity, key + ": invalid native energy")
            result[key] = dict(pos=list(pos), facing=facing, block=block, tile=str(data["id"]),
                               witness=key, stock=7, energyBits=bits, energy=energy, moved=pos == rear)
            found.add(pos)
            facings.add(facing)
        require(facings == set(DIRECTIONS) and len(found) == 6, sid + ": six facings missing")
        require({p for p, row in rows.items() if row["block"] == block} == found,
                sid + ": extra off-track drill")
        require(set(stock) == {key for key in result if key.startswith(sid + ":")},
                sid + ": unexpected stock witness")
    require(len(result) == 12, "Twelve native drill witnesses required")
    return result


def log_evidence(evidence, path, line, phase, records):
    text = evidence.read(path).decode("utf-8", errors="replace")
    minecraft, forge = VERSIONS[line]
    require("Starting minecraft server version " + minecraft in text
            and "Forge mod loading, version " + forge + ", for MC " + minecraft in text,
            str(path) + ": missing native boot identity")
    require(re.search(r"Done \([^\n]+For help", text), str(path) + ": native boot incomplete")
    summaries = re.findall(r"Advantage Works check: \{([^}]+)\}", text)
    require(len(summaries) == 1, str(path) + ": missing or duplicate native all-station check")
    native_counts = dict((key, int(value)) for key, value in re.findall(r"([A-Z_]+)=(\d+)", summaries[0]))
    require(native_counts == dict(Counter(row["status"] for row in records.values())),
            str(path) + ": native check summary differs from raw station classifications")
    if phase == "first":
        require("Advantage Works built 21 station(s); skipped 0" in text, str(path) + ": native bay construction incomplete")
        for sid in STATIONS:
            for marker in ("Started " + sid + " -", "Stopped " + sid + " -", sid + "-checkpoint-operated.json"):
                require(text.count(marker) == 1, str(path) + ": missing/duplicate native station activity " + marker)
    else:
        require(not any(marker in text for marker in ("Advantage Works built", "Modified block data of"))
                and not re.search(r"(?:Started|Reset) (?:W-|S-|E-|P-API-)", text),
                str(path) + ": construction/refill/start activity after cold restart")
    saved = text.rfind("Saved the game")
    stopped = text.find("Stopping server", max(saved, text.find("Done (")))
    require(stopped >= 0 and (line != "113" or saved >= 0), str(path) + ": missing native save and shutdown")
    require(text.find("Done (") < text.find("Advantage Works check:") < stopped,
            str(path) + ": native check is outside the running-server interval")
    # Forge 25 uses an explicit flush. Later broad protocols rely on the native
    # stop path, which logs per-dimension chunk-storage completion instead.
    shutdown = text[stopped:]
    require("Saving players" in shutdown and "Saving worlds" in shutdown, str(path) + ": incomplete shutdown saves")
    for dimension in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"):
        require(any("Saving chunks for level" in row and dimension in row for row in shutdown.splitlines()),
                str(path) + ": missing saved dimension " + dimension)
    if line != "113":
        completed = set(re.findall(r"ThreadedAnvilChunkStorage \(([^)]+)\): All chunks are saved", shutdown))
        require({"DIM-1", "DIM1"} <= completed and bool(completed - {"DIM-1", "DIM1"}),
                str(path) + ": missing completed chunk-storage shutdown")
    errors = ("NoSuchFieldError", "NoSuchMethodError", "Failed to reload data packs",
              "Encountered an unexpected exception", "Exception in server tick loop")
    for row in text.splitlines():
        require("/ERROR]" not in row and not any(marker in row for marker in errors),
                str(path) + ": native error: " + row)
    # Reuse only existing, narrow engine diagnostics; never trust a diagnostic
    # PASS without validating its raw log and classified line identities.
    if line == "113":
        from native113_diagnostics import audit_lines
        diagnostics = audit_lines(text.splitlines())
        recorded = evidence.json(path.with_suffix(".diagnostics.json"))
        require(recorded.get("status") == "PASS" and recorded.get("logSha256") == sha(text.encode("utf-8"))
                and recorded.get("classifiedDiagnostics") == diagnostics["classifiedDiagnostics"],
                str(path) + ": stale or masked native diagnostics")
    elif line == "114":
        notice = "[ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!"
        require(not any("/FATAL]" in row and not row.endswith(notice) for row in text.splitlines()),
                str(path) + ": unclassified fatal")
    else:
        require("/FATAL]" not in text, str(path) + ": fatal native runtime error")
    return dict(status="PASS", saveAndAllDimensionShutdown=True, explicitSaveCommand=saved >= 0,
                processExit="NOT_INDEPENDENTLY_RECORDED_BY_HISTORICAL_BROAD_WRITER")


def staged_pins(evidence, runtime, pins, name):
    jars = [row for row in pins["jars"] if name != "packaged-decoupled" or row["id"] != "basemetals"]
    expected = {Path(row["path"]).name: row["sha256"].upper() for row in jars}
    require(len(expected) == len(jars), "Duplicate pinned jar filenames")
    actual = {path.name: sha(evidence.read(path)) for path in (runtime / "mods").glob("*.jar")}
    require(actual == expected, name + ": staged mod bytes differ from pins")
    recorded = indexed(evidence.json(runtime / "input-jars.json"), "file", name + " staged input receipt")
    require({key: row["sha256"].upper() for key, row in recorded.items()} == expected,
            name + ": staged input receipt differs from pins")
    require(sha(evidence.read(runtime / pins["launcher"])) == pins["launcher_sha256"].upper(),
            name + ": staged launcher changed")
    sealed = pins.get("serverCode", {}).get("files")
    if sealed:
        paths = list(runtime.glob("*.jar")) + list((runtime / "libraries").rglob("*"))
        actual = {path.relative_to(runtime).as_posix(): dict(sha256=sha(evidence.read(path)), size=path.stat().st_size)
                  for path in paths if path.is_file()}
        require(actual == sealed, name + ": sealed native engine bytes changed")
    return dict(modJars=expected, sealedEngine=bool(sealed))


def profile_audit(evidence, runtime, name, report, pins, definitions, line):
    local = evidence.json(runtime / "broad-results.json")
    require(local == report, name + ": local result differs from top-level result")
    require(report["profile"] == name and report["status"] == "PASS", name + ": profile did not pass")
    require(not report.get("error") and not report.get("shutdownError"), name + ": failure masked by status")
    for key in ("exitCode", "processExitCode", "returnCode"):
        if key in report:
            require(report[key] == 0, name + ": recorded process exit failed")
    require(set(report["phases"]) == PHASES, name + ": required phases incomplete")
    require(all(row.get("status") == "PASS" for row in report["phases"].values()), name + ": phase failure masked")
    minecraft, forge = VERSIONS[line]
    mods = {row["id"]: row["version"] for row in pins["jars"]
            if name != "packaged-decoupled" or row["id"] != "basemetals"}
    mods.update(minecraft=minecraft, forge=forge)
    require(report["expectedMods"] == mods, name + ": expected mod identities changed")
    custody = staged_pins(evidence, runtime, pins, name)
    directory = runtime / "advantage-works-results"
    checks, records = {}, {}
    for phase in ("first", "restarted"):
        raw = evidence.json(directory / (phase + "-all-stations.json"))
        records[phase] = raw
        checked = raw_checks(raw, definitions, mods, minecraft, forge)
        require(report["phases"][phase] == checked, name + ": summary counts/manual list differs from raw " + phase)
        checks[phase] = checked
    for sid in STATIONS:
        operated = evidence.json(directory / (sid + "-checkpoint-operated.json"))
        require(operated["station"] == sid and operated["minecraft"] == minecraft and operated["forge"] == forge
                and operated["mods"] == mods and operated["checkpoint"] == "operated" and operated["snapshot"],
                sid + ": operated checkpoint identity/snapshot missing")
        require(operated["worldTime"] <= records["first"][sid]["worldTime"] <= records["restarted"][sid]["worldTime"],
                sid + ": raw checkpoints are not ordered across operation and cold restart")
    initial_records = {sid: evidence.json(directory / (sid + "-checkpoint-initial.json"))
                       for sid in ("S-05", "E-06")}
    initial = drill_snapshot(initial_records, definitions, initial=True)
    before = drill_snapshot(records["first"], definitions)
    after = drill_snapshot(records["restarted"], definitions)
    require(report["phases"]["initial"] == dict(status="PASS", drills=initial), name + ": initial drill summary masked")
    require(report["beforeRestart"] == before and report["afterRestart"] == after,
            name + ": drill summary differs from native snapshots")
    for key in initial:
        require(all(initial[key][field] == before[key][field] for field in ("facing", "block", "tile", "witness", "stock")),
                key + ": moved identity changed")
    require(before["S-05:down-facing"]["moved"] and before["E-06:up-facing"]["moved"], "Required native track movement absent")
    require(report["phases"]["operation"] == dict(status="PASS", drills=12, moved=sum(row["moved"] for row in before.values())),
            name + ": drill movement count masked")
    require(before == after, name + ": native cold restart changed drill state")
    require(report["phases"]["persistence"] == dict(status="PASS", drills=12, exactEnergy=True),
            name + ": persistence summary changed")
    shutdown = {phase: log_evidence(evidence, runtime / ("console-" + phase + ".log"), line, phase, records[phase])
                for phase in ("first", "restarted")}
    return dict(status="PASS", stations=21, checks=checks, pins=custody, shutdown=shutdown,
                manualProcedures="NOT_AUTOMATED_BY_BROAD_SWEEP")


def audit(runtime, line, requested=PROFILES):
    runtime, line = Path(runtime).resolve(), str(line)
    evidence = Evidence()
    result = dict(status="FAIL", line=line, runtime=str(runtime), requestedProfiles=list(requested),
                  profiles={}, errors=[], writerWarning="Historical broad writer can emit PASS for a completed subset; require this independent audit.")
    try:
        require(line in VERSIONS, "Unsupported native line")
        require(requested and len(set(requested)) == len(requested) and set(requested) <= set(PROFILES), "Invalid requested profile set")
        report = evidence.json(runtime / ("native" + line + "-broad-results.json"))
        require_profiles(report["profiles"], requested)
        require(report["status"] == "PASS", "Top-level campaign did not finish PASS")
        require(not report.get("error") and not report.get("shutdownError"), "Top-level failure masked by PASS")
        for key in ("exitCode", "processExitCode", "returnCode"):
            if key in report:
                require(report[key] == 0, "Top-level recorded process exit failed")
        manifest_path = Path(report["targetManifest"])
        manifest_data = evidence.read(manifest_path)
        require(sha(manifest_data) == report["targetManifestSha256"], "Pinned target manifest changed")
        pins = decode(manifest_data)["target" + line]
        require(sha(evidence.read(runtime / "runner-source.py")) == report["runnerSha256"], "Archived broad runner changed")
        jars = indexed(pins["jars"], "id", "target mod pins")
        require(set(jars) == {"poweradvantage", "steamadvantage", "electricadvantage", "advantageworkstest", "basemetals", "orespawn"},
                "Complete pinned current-profile inputs required")
        harness = Path(jars["advantageworkstest"]["path"])
        harness_bytes = evidence.read(harness)
        require(sha(harness_bytes) == jars["advantageworkstest"]["sha256"], "Pinned harness bytes changed")
        with zipfile.ZipFile(harness) as archive:
            definition = archive.read(RESOURCE)
        # Gradle normalizes line endings; only that byte normalization is allowed.
        lf = definition.replace(b"\r\n", b"\n")
        require(report["stationDefinitionSha256"] in (sha(definition), sha(lf), sha(lf.replace(b"\n", b"\r\n"))),
                "Reported station definition differs from pinned harness resource")
        definitions = indexed(decode(definition)["stations"], "id", "station definitions")
        require(set(definitions) == set(STATIONS), "Pinned definition does not describe all 21 stations")
        for name in requested:
            try:
                result["profiles"][name] = profile_audit(evidence, runtime / name, name, report["profiles"][name], pins, definitions, line)
            except (AssertionError, OSError, ValueError, KeyError, TypeError, IndexError, ImportError) as error:
                result["profiles"][name] = dict(status="FAIL", error=str(error))
                result["errors"].append(name + ": " + str(error))
        evidence.unchanged()
        if not result["errors"]:
            result["status"] = "PASS"
    except (AssertionError, OSError, ValueError, KeyError, TypeError, IndexError, zipfile.BadZipFile) as error:
        result["errors"].append(str(error))
    result["evidenceSha256"] = evidence.hashes
    result["scope"] = "Complete requested broad profiles and declared native checks only; not manual acceptance or operating/endurance coverage."
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True, help="Broad output root containing nativeNNN-broad-results.json")
    parser.add_argument("--line", choices=tuple(VERSIONS), required=True)
    parser.add_argument("--profiles", nargs="+", choices=("both",) + PROFILES, default=["both"],
                        help="Explicit requested set; defaults to both, never inferred from existing results")
    args = parser.parse_args(argv)
    requested = PROFILES if args.profiles == ["both"] else tuple(args.profiles)
    if "both" in requested:
        parser.error("both cannot be combined with a named profile")
    result = audit(args.runtime, args.line, requested)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
