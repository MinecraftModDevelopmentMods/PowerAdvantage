"""Packaged, hash-checked migration qualification on disposable worlds only."""

import argparse
import base64
import gzip
import hashlib
import io
import json
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time
import zipfile

import nbtlib


POWER = Path(__file__).resolve().parents[2]
RIG = POWER / "test-rig"
WORLD = "AdvantageWorksMigration"
ORIGINAL_HASHES = {
    "PowerAdvantage_1.10.2-2.3.0.jar": "47CD509C1141095BFE1C8C7DD689C2ECD3F99C89195E34A88CC9E0B5FC12413E",
    "SteamAdvantage_1.10.2-2.2.0.jar": "34A0C46C7B36DEF313F35720BD788539BDEECAD3C30880D30D82BC84E35AD9A5",
    "ElectricAdvantage_1.10.2-2.2.0.jar": "61AE5343A79F518F54F1717D508EA3F6DA7209EE69B0C4433E0B7A9722213EE0",
}
LEGACY_112_ELECTRIC_HASH = "CFF96BDCD60770D370E16BDCF34E8E08DF8139804437CA1D368D7080996E7537"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def fingerprint(root):
    return {str(p.relative_to(root)): digest(p) for p in sorted(root.rglob("*")) if p.is_file()}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True), encoding="utf-8")


class Server:
    def __init__(self, runtime, java, launcher, label):
        self.lines = queue.Queue()
        self.log = (runtime / f"console-{label}.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(
            [str(java), "-Xms512M", "-Xmx2G", "-Dfml.queryResult=confirm", "-jar", launcher, "nogui"],
            cwd=runtime, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()
        try:
            self.wait("Done (", 300)
        except BaseException:
            self.close()
            raise

    def read(self):
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            self.lines.put(line)

    def wait(self, token, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = self.lines.get(timeout=0.5)
                if token in line:
                    return
                if any(failure in line for failure in ("Migration probe failed", "Actor probe failed", "Recipe probe failed", "Connected probe failed", "ADVRECIPES FAIL", "ADVPROGRESSION FAIL")):
                    raise RuntimeError(line.strip())
            except queue.Empty:
                if self.process.poll() is not None:
                    raise RuntimeError(f"Server exited ({self.process.returncode}); see {self.log.name}")
        raise TimeoutError(f"Waiting for {token!r}; see {self.log.name}")

    def send(self, command):
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def snapshot(self, label, build=False):
        self.send(f"advprobe {'build' if build else 'snapshot'} {label}")
        self.wait("ADVPROBE COMPLETE", 120)

    def close(self):
        if self.process.poll() is None:
            try:
                self.send("stop")
                self.process.wait(timeout=90)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=15)
        self.thread.join(timeout=5)
        self.log.close()
        if self.process.returncode != 0:
            raise RuntimeError(f"Unclean shutdown ({self.process.returncode}): {self.log.name}")


def profiles(workspace):
    old110 = workspace / "_tmp/pa110-migration-server"
    old112 = workspace / "_tmp/pa112-smoke-server"
    maintained110 = workspace / "PA110/PowerAdvantage/run-advantage-works/packaged-current-20260907-223929"
    seed113 = POWER / "run-advantage-works/packaged-decoupled-20261005-184456"
    current = json.loads((RIG / "profiles/packaged-current.json").read_text())
    modern_jars = [(RIG / mod["path"]).resolve() for mod in current["mods"]]
    # Keep the qualified pre-repair source separate from a rebuilt bridge jar.
    legacy_electric = POWER / "run-migration-matrix/legacy-release-jars/ElectricAdvantage-2.2.1.112021.jar"
    if not legacy_electric.is_file():
        legacy_electric = workspace / "PA112/ElectricAdvantage/ElectricAdvantage/build/libs/ElectricAdvantage-2.2.1.112021.jar"
    if digest(legacy_electric) != LEGACY_112_ELECTRIC_HASH:
        raise AssertionError(f"Qualified 1.12 Electric source jar changed: {legacy_electric}")
    maintained112 = [
        workspace / "PA112/PowerAdvantage/build/libs/PowerAdvantage-2.4.1.112021.jar",
        workspace / "PA112/SteamAdvantage/SteamAdvantage/build/libs/SteamAdvantage-2.2.1.112021.jar",
        legacy_electric,
    ]
    old112_jars = [p for p in (old112 / "mods").glob("*.jar") if "AdditionalLootTables" not in p.name]
    dependencies112 = [p for p in old112_jars if "Advantage" not in p.name]
    probe = RIG / "migration-probe/build"
    result = {
        "early110": (maintained110, "forge-1.10.2-12.18.3.2511-universal.jar",
                        sorted((old110 / "mods").glob("*.jar")) + [probe / "110/libs/AdvantageMigrationProbe-110-1.0.0.jar"]),
        "maintained110": (maintained110, "forge-1.10.2-12.18.3.2511-universal.jar",
                          [p for p in (maintained110 / "mods").glob("*.jar") if "TestHarness" not in p.name]
                          + [probe / "110/libs/AdvantageMigrationProbe-110-1.0.0.jar"]),
        "legacy112": (old112, "forge-1.12.2-14.23.5.2859.jar", old112_jars
                      + [probe / "112/libs/AdvantageMigrationProbe-112-1.0.0.jar"]),
        "maintained112": (old112, "forge-1.12.2-14.23.5.2859.jar", maintained112 + dependencies112
                          + [probe / "112/libs/AdvantageMigrationProbe-112-1.0.0.jar"]),
        "target113": (seed113, "forge-1.13.2-25.0.223.jar", modern_jars),
    }
    sylvester = workspace.parent / "MinecraftMineralogy/1 Sylvester/mods"
    originals = [sylvester / "PowerAdvantage_1.10.2-2.3.0.jar",
                 sylvester / "SteamAdvantage_1.10.2-2.2.0.jar",
                 POWER / "run-migration-matrix/original-release-jars/ElectricAdvantage_1.10.2-2.2.0.jar"]
    if all(p.is_file() for p in originals):
        for jar in originals:
            if digest(jar) != ORIGINAL_HASHES[jar.name]:
                raise AssertionError(f"Original-release hash mismatch: {jar}")
            with zipfile.ZipFile(jar) as archive:
                if not any(name.startswith("cyano/") and name.endswith(".class") for name in archive.namelist()):
                    raise AssertionError(f"Not an original Cyano release: {jar}")
        seed, launcher, jars = result["early110"]
        result["original110"] = (seed, launcher, originals + [p for p in jars if "Advantage" not in p.name]
                                 + [probe / "110/libs/AdvantageMigrationProbe-110-1.0.0.jar"])
    return result


def selected_profiles(args):
    if args.profile_manifest is None: return profiles(args.workspace)
    manifest = json.loads(args.profile_manifest.read_text())
    result = {}
    for name, entry in manifest.items():
        jars = []
        for jar in entry["jars"]:
            path = Path(jar["path"])
            if not path.is_absolute(): path = args.profile_manifest.parent / path
            if digest(path) != jar["sha256"]:
                raise AssertionError(f"Profile jar hash mismatch: {path}")
            jars.append(path.resolve())
        seed = Path(entry["seed"])
        if not seed.is_absolute(): seed = args.profile_manifest.parent / seed
        result[name] = (seed.resolve(), entry["launcher"], jars)
    return result


def intermediate_profile(available):
    """An explicit bridge override must not replace the historical source profile."""
    return available["bridge112"] if "bridge112" in available else available["maintained112"]


def stage(runtime, profile):
    seed, launcher, mods = profile
    runtime.mkdir(parents=True, exist_ok=False)
    shutil.copytree(seed / "libraries", runtime / "libraries")
    for p in seed.glob("*.jar"):
        if "installer" not in p.name:
            shutil.copy2(p, runtime / p.name)
    (runtime / "mods").mkdir()
    identities = []
    for jar in mods:
        shutil.copy2(jar, runtime / "mods" / jar.name)
        identities.append({"file": jar.name, "source": str(jar), "sha256": digest(jar)})
    write_json(runtime / "input-jars.json", identities)
    (runtime / "eula.txt").write_text("eula=true\n")
    (runtime / "advantage-migration.allow").touch()
    (runtime / "server.properties").write_text(
        f"level-name={WORLD}\nlevel-type=FLAT\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25584\n"
        "spawn-protection=0\nview-distance=4\nmax-tick-time=60000\nallow-nether=false\n"
        "generator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;\n"
    )
    return launcher


def parse_nbt(entry):
    return nbtlib.File.parse(io.BytesIO(gzip.decompress(base64.b64decode(entry["nbtBase64"])))).unpack()


def plain(value):
    if isinstance(value, dict): return {k: plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)): return [plain(v) for v in value]
    if hasattr(value, "tolist"): return value.tolist()
    if hasattr(value, "item"): return value.item()
    return value


def normalized(value, enchantments=None):
    if isinstance(value, dict):
        result = {k: normalized(v, enchantments) for k, v in value.items() if not (k == "ForgeCaps" and not v)}
        if "Empty" in result: result["Empty"] = True
        for key in ("FluidName", "FluidID"):
            if key in result and ":" not in result[key]:
                result[key] = ("poweradvantage:" if result[key] in ("crude_oil", "refined_oil") else "minecraft:") + result[key]
        identity = result.get("id", "")
        if not isinstance(identity, str):
            if "lvl" in result and enchantments is not None:
                result["id"] = enchantments.get(identity, identity)
            return result
        if "advantage." in identity and "Count" not in result:
            result["id"] = identity.replace("minecraft:", "").replace("advantage.", "advantage:", 1)
        if identity == "Chest": result["id"] = "minecraft:chest"
        if result.get("id") == "electricadvantage:l_e_d": result["id"] = "electricadvantage:led"
        if "Count" in result:
            if identity == "forge:bucketfilled" and "tag" in result:
                fluid = result["tag"].get("FluidName")
                if fluid in ("poweradvantage:crude_oil", "poweradvantage:refined_oil") and result["tag"].get("Amount") == 1000:
                    result["id"] = fluid + "_bucket"
                    result["tag"].pop("FluidName", None)
                    result["tag"].pop("Amount", None)
                    if not result["tag"]: result.pop("tag")
            damage = result.pop("Damage", 0)
            if damage:
                result.setdefault("tag", {})["Damage"] = damage
            if "tag" in result and "ench" in result["tag"]:
                result["tag"]["Enchantments"] = result["tag"].pop("ench")
        return result
    if isinstance(value, list): return [normalized(v, enchantments) for v in value]
    return value


def differences(expected, actual, path=""):
    result = []
    if isinstance(expected, dict) and isinstance(actual, dict):
        for key, value in expected.items():
            if key not in actual: result.append({"path": f"{path}.{key}", "expected": value, "actual": "<missing>"})
            else: result.extend(differences(value, actual[key], f"{path}.{key}"))
    elif isinstance(expected, list) and isinstance(actual, list):
        if len(expected) != len(actual): result.append({"path": path, "expected": expected, "actual": actual})
        else:
            for i, value in enumerate(expected): result.extend(differences(value, actual[i], f"{path}[{i}]"))
    elif expected != actual:
        result.append({"path": path, "expected": expected, "actual": actual})
    return result


def compare(source, target):
    before = json.loads(source.read_text())
    after = json.loads(target.read_text())
    findings = []
    if len(before) != len(after): raise AssertionError("Witness count changed")
    counts = {"blocks": len(before), "tiles": 0, "items": 0}
    def enchantment_ids(snapshot):
        level = nbtlib.load(snapshot.parent / WORLD / "level.dat")
        entries = level.get("FML", {}).get("Registries", {}).get("minecraft:enchantments", {}).get("ids", [])
        return {int(e["V"]): str(e["K"]) for e in entries}
    old_enchantments = enchantment_ids(source)
    new_enchantments = enchantment_ids(target)
    for old, new in zip(before, after):
        pos = f"{old['name']}@{old['x']},{old['y']},{old['z']}"
        if old["actualName"] != new["actualName"]:
            findings.append({"path": pos, "expected": old["actualName"], "actual": new["actualName"]})
        findings.extend(differences(old["properties"], new["properties"], pos + ".state"))
        if "nbt" in old:
            counts["tiles"] += 1
            old_nbt = normalized(plain(parse_nbt(old)), old_enchantments)
            counts["items"] += sum(int(v.get("Count", 0)) for v in old_nbt.get("Items", []))
            if "nbt" not in new: findings.append({"path": pos + ".tile", "expected": "tile", "actual": "<missing>"})
            else: findings.extend(differences(old_nbt, normalized(plain(parse_nbt(new)), new_enchantments), pos + ".nbt"))
    return {"status": "FAIL" if findings else "PASS", "witnesses": counts, "differences": findings}


def start_snapshot(runtime, java, launcher, label, build=False):
    server = Server(runtime, java, launcher, label)
    try:
        server.snapshot(label, build)
        server.send("save-all flush")
        time.sleep(2)
    finally:
        server.close()


def copy_fixture(source, destination):
    shutil.copytree(source / WORLD, destination / WORLD)
    shutil.copy2(source / "migration-fixture.json", destination / "migration-fixture.json")
    if (source / "config").exists(): shutil.copytree(source / "config", destination / "config")


def run(args):
    available = selected_profiles(args)
    if args.sources and not set(args.sources).issubset(available):
        raise ValueError(f"Unavailable source profiles: {set(args.sources) - set(available)}")
    root = args.output or POWER / "run-migration-matrix" / time.strftime("%Y%m%d-%H%M%S")
    root.mkdir(parents=True, exist_ok=False)
    write_json(root / "profiles.json", {key: {"seed": str(p[0]), "launcher": p[1], "jars": [{"path": str(j), "sha256": digest(j)} for j in p[2]]}
                                         for key, p in available.items()})
    sources = {}
    results = {}
    for name in ("original110", "early110", "maintained110", "legacy112", "maintained112"):
        if name not in available: continue
        if args.sources and name not in args.sources: continue
        print(f"{'Reusing immutable' if args.fixtures else 'Building saved'} baseline: {name}", flush=True)
        runtime = root / name
        if args.fixtures:
            runtime = args.fixtures / name
            expected = json.loads((runtime / "fixture-hashes.json").read_text())
            if fingerprint(runtime / WORLD) != expected: raise AssertionError(f"Source hash mismatch: {runtime}")
        else:
            launcher = stage(runtime, available[name])
            start_snapshot(runtime, args.java8, launcher, "source", build=True)
            write_json(runtime / "fixture-hashes.json", fingerprint(runtime / WORLD))
        sources[name] = runtime
    for name, source in sources.items():
        routes = ["direct"] + (["chained"] if name.endswith("110") else [])
        for route in routes:
            label = name + "-" + route
            print(f"Qualifying {label}", flush=True)
            fixture = source
            intermediate_result = None
            if route == "chained":
                intermediate = root / (label + "-112")
                launcher = stage(intermediate, intermediate_profile(available))
                copy_fixture(source, intermediate)
                start_snapshot(intermediate, args.java8, launcher, "intermediate")
                fixture = intermediate
                intermediate_result = compare(source / "migration-source.json", intermediate / "migration-intermediate.json")
                write_json(intermediate / "state-comparison.json", intermediate_result)
            target = root / (label + "-113")
            launcher = stage(target, available["target113"])
            copy_fixture(fixture, target)
            start_snapshot(target, args.java8, launcher, "converted")
            first = compare(source / "migration-source.json", target / "migration-converted.json")
            start_snapshot(target, args.java8, launcher, "restarted")
            second = compare(source / "migration-source.json", target / "migration-restarted.json")
            stable = compare(target / "migration-converted.json", target / "migration-restarted.json")
            results[label] = {"conversion": first, "restart": second, "idempotence": stable}
            if intermediate_result is not None: results[label]["intermediate"] = intermediate_result
            write_json(target / "state-comparison.json", results[label])
            write_json(root / "matrix-results.json", results)
            print(f"{label}: conversion={first['status']} restart={second['status']} idempotence={stable['status']}", flush=True)
        expected = json.loads((source / "fixture-hashes.json").read_text())
        if fingerprint(source / WORLD) != expected: raise AssertionError(f"Immutable source changed: {source}")
    print(f"Reports: {root}", flush=True)
    if not results: raise AssertionError("No migration routes ran")
    return 1 if any(r[stage]["status"] != "PASS" for r in results.values() for stage in r) else 0


def operate(args):
    """Commission the existing Works stations in a copy of a converted world."""
    root = args.output
    launcher = stage(root, selected_profiles(args)["target113"])
    copy_fixture(args.operate, root)
    (root / "migration-operate.allow").touch()
    (root / "advantage-works.allow").touch()
    stations = ["W-01", "W-04", "S-01", "S-04", "S-06", "E-01", "E-02", "E-03", "P-API-01"]
    checked = stations + ["S-05", "E-06"]
    source_hashes = fingerprint(args.operate / WORLD)
    for phase in ("operation", "operation-restart"):
        server = Server(root, args.java8, launcher, phase)
        try:
            if phase == "operation":
                server.send("advworks build all")
                server.wait("Advantage Works built", 120)
                for station in stations:
                    server.send(f"advworks start {station}")
                    server.wait("Started " + station)
                    time.sleep(5)
                    server.send(f"advworks stop {station}")
                    server.wait("Stopped " + station)
            time.sleep(5)
            for station in checked:
                server.send(f"advworks check {station}")
                server.wait("Advantage Works check:")
            server.send("save-all flush")
            time.sleep(2)
        finally:
            server.close()
        reports = root / "advantage-works-results"
        statuses = {station: json.loads((reports / (station + "-latest.json")).read_text())["status"] for station in checked}
        write_json(root / (phase + "-results.json"), statuses)
        if any(s in ("FAIL", "EXPECTED_FAIL", "UNEXPECTED_PASS") for s in statuses.values()):
            raise AssertionError(f"Station failure: {statuses}")
        print(f"{phase}: automated assertions accepted for {len(statuses)} stations", flush=True)
    if fingerprint(args.operate / WORLD) != source_hashes: raise AssertionError("Converted source fixture changed")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, help="Hash-pinned input profiles; paths may be relative to this file")
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--sources", nargs="+")
    parser.add_argument("--fixtures", type=Path, help="Reuse an existing hash-checked immutable fixture set")
    parser.add_argument("--operate", type=Path, help="Commission a disposable copy of this converted runtime")
    args = parser.parse_args()
    if args.operate and args.output is None: parser.error("--operate requires --output")
    raise SystemExit(operate(args) if args.operate else run(args))
