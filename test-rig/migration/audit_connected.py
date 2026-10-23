"""Independently recheck connected-world evidence without starting Minecraft."""
import argparse
import json
from pathlib import Path

from connected import SOURCES, cold_state, entries, fixture, operation, read, state
from matrix import WORLD, compare, digest, fingerprint, write_json


def expected_routes(sources):
    return {name + "-" + route for name in sources
            for route in (("direct", "chained") if name.endswith("110") else ("direct",))}


def audit(roots):
    checks, diagnostics, sources, routes = [], [], set(), set()

    def check(name, action):
        try:
            result = action()
            if result is False or isinstance(result, dict) and result.get("status") != "PASS":
                raise AssertionError(str(result))
            checks.append(dict(name=name, status="PASS"))
        except Exception as error:
            checks.append(dict(name=name, status="FAIL", reason=str(error)))

    def equal(actual, expected):
        if actual != expected:
            raise AssertionError(f"Expected {expected}, got {actual}")

    def snapshot(runtime, label, definition, builds):
        document = read(runtime, label)
        equal(document["buildCallsThisProcess"], builds)
        equal(document["held"], True)
        equal(document["snapshot"], json.loads((runtime / ("migration-" + label + ".json")).read_text()))
        rows = entries(document)
        equal(len(rows), len(definition["blocks"]))
        equal(set(rows), {b["id"] for b in definition["blocks"]})
        for expected in definition["blocks"]:
            for key, value in expected.items():
                if key != "seed":
                    equal(rows[expected["id"]][key], value)
        return document

    def commands(runtime, label, expected):
        equal(json.loads((runtime / ("console-" + label + ".commands.json")).read_text()), expected)
        log = (runtime / ("console-" + label + ".log")).read_text(encoding="utf-8")
        if "Done (" not in log or "Stopping server" not in log:
            raise AssertionError("Missing startup or graceful shutdown marker")
        if any(marker in log for marker in ("Connected probe failed", "Encountered an unexpected exception", "A fatal error has been detected")):
            raise AssertionError("Fatal probe/server diagnostic")
        errors = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
        if errors:
            diagnostics.append(dict(runtime=str(runtime), phase=label, lines=errors[:100], total=len(errors)))

    def phase(runtime, label, definition):
        loaded = snapshot(runtime, label + "-loaded", definition, 0)
        worked = snapshot(runtime, label + "-worked", definition, 0)
        equal(loaded["requestedTicks"], 0)
        equal(loaded["advancedTicks"], 0)
        equal(worked["requestedTicks"], 512)
        commands(runtime, label, ["advconnected snapshot " + label + "-loaded",
                                 "advconnected step 512 " + label + "-worked", "save-all flush", "stop"])
        if (runtime / "connected-build.allow").exists() or (runtime / "connected-built.json").exists():
            raise AssertionError("Imported runtime has source construction authority/evidence")
        return operation(loaded, worked, 512)

    for root in roots:
        report = json.loads((root / "connected-results.json").read_text())
        label = root.name
        check(label + ": completed campaign", lambda: equal(report["status"], "PASS"))
        check(label + ": route completeness", lambda: equal(set(report["routes"]), expected_routes(report["sources"])))
        check(label + ": source uniqueness", lambda: not (sources & set(report["sources"])))
        check(label + ": route uniqueness", lambda: not (routes & set(report["routes"])))
        sources.update(report["sources"])
        routes.update(report["routes"])
        profiles = json.loads((root / "profiles.json").read_text())
        provenance = json.loads((root / "input-provenance.json").read_text())
        base = Path(provenance["base"])
        check(label + ": archived base profile", lambda: equal(digest(base), provenance["sha256"]))
        previous = json.loads(base.read_text())
        for name, profile in profiles.items():
            check(label + ": " + name + " jar identities", lambda n=name, p=profile:
                  equal([Path(j["path"]).name for j in p["jars"]], [Path(j["path"]).name for j in previous[n]["jars"]]))
            for old, jar in zip(previous[name]["jars"], profile["jars"]):
                check(label + ": archive " + name + "/" + Path(jar["path"]).name,
                      lambda j=jar: equal(digest(Path(j["path"])), j["sha256"]))
                if not Path(jar["path"]).name.startswith(("AdvantageMigrationProbe-", "AdvantageWorksTestHarness-")):
                    check(label + ": unchanged production/dependency " + name + "/" + Path(jar["path"]).name,
                          lambda a=old, b=jar: equal(a["sha256"], b["sha256"]))
        for runtime in root.iterdir():
            if not runtime.is_dir() or not (runtime / "input-jars.json").exists():
                continue
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                check(runtime.name + ": staged " + jar["file"],
                      lambda r=runtime, j=jar: equal(digest(r / "mods" / j["file"]), j["sha256"]))
        for name in report["sources"]:
            source = root / name
            definition = json.loads((source / "connected-fixture.json").read_text())
            check(name + ": exact finite construction plan", lambda d=definition, n=name: equal(d, fixture(n)))
            check(name + ": immutable saved source", lambda s=source:
                  equal(fingerprint(s / WORLD), json.loads((s / "fixture-hashes.json").read_text())))

            def source_operation():
                cold = snapshot(source, "cold", definition, 1)
                worked = snapshot(source, "source", definition, 1)
                if cold_state(cold)["status"] != "PASS":
                    return cold_state(cold)
                equal(cold["requestedTicks"], 0)
                commands(source, "source", ["advconnected build cold", "advconnected step 803 source", "save-all flush", "stop"])
                if state(worked)["cases"]["battery"]["charge"] <= 0:
                    raise AssertionError("No real source battery charging")
                return operation(cold, worked, 803)

            check(name + ": real source operation from cold finite supplies", source_operation)
            for route in ("direct", "chained") if name.endswith("110") else ("direct",):
                route_name = name + "-" + route
                previous_runtime, checkpoint = source, "source"
                if route == "chained":
                    middle = root / (route_name + "-112")
                    check(route_name + ": intermediate operation", lambda r=middle, d=definition: phase(r, "intermediate", d))
                    check(route_name + ": intermediate preservation", lambda s=source, r=middle:
                          compare(s / "migration-source.json", r / "migration-intermediate-loaded.json"))
                    check(route_name + ": intermediate restart operation", lambda r=middle, d=definition: phase(r, "intermediate-restart", d))
                    check(route_name + ": intermediate restart preservation", lambda r=middle:
                          compare(r / "migration-intermediate-worked.json", r / "migration-intermediate-restart-loaded.json"))
                    previous_runtime, checkpoint = middle, "intermediate-restart-worked"
                target = root / (route_name + "-113")
                check(route_name + ": target operation", lambda r=target, d=definition: phase(r, "converted", d))
                check(route_name + ": target preservation", lambda p=previous_runtime, c=checkpoint, r=target:
                      compare(p / ("migration-" + c + ".json"), r / "migration-converted-loaded.json"))
                check(route_name + ": restart operation", lambda r=target, d=definition: phase(r, "restarted", d))
                check(route_name + ": restart preservation", lambda r=target:
                      compare(r / "migration-converted-worked.json", r / "migration-restarted-loaded.json"))

                def output(runtime=target):
                    first = state(read(runtime, "converted-loaded"))
                    last = state(read(runtime, "restarted-worked"))
                    missing = [n for n, case in first["cases"].items() if n != "battery" and case["remaining"] > 0
                               and last["cases"][n]["produced"] <= case["produced"]]
                    equal(missing, [])
                check(route_name + ": real additional items across both target intervals", output)
    check("All five historical sources tested", lambda: equal(sources, set(SOURCES)))
    check("All eight direct/chained routes tested", lambda: equal(routes, expected_routes(SOURCES)))
    return dict(status="FAIL" if any(c["status"] != "PASS" for c in checks) else "PASS",
                sources=sorted(sources), routes=sorted(routes), checks=checks, diagnostics=diagnostics)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = audit(args.roots)
    write_json(args.output, result)
    print(result["status"], len(result["checks"]), "checks;", len(result["routes"]), "routes")
    raise SystemExit(result["status"] != "PASS")
