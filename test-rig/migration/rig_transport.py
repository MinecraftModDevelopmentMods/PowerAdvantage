"""Disposable-world transport shared with the archived migration harness.

No platform defaults or historical profile discovery are exported here.
"""
import hashlib
import json
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time

WORLD = "AdvantageWorksMigration"


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


def stage(runtime, profile):
    seed, launcher, mods = profile
    runtime.mkdir(parents=True, exist_ok=False)
    shutil.copytree(seed / "libraries", runtime / "libraries")
    for p in seed.glob("*.jar"):
        # Preserve the complete sealed root-jar inventory; only the pinned launcher is executed.
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


def copy_fixture(source, destination):
    shutil.copytree(source / WORLD, destination / WORLD)
    shutil.copy2(source / "migration-fixture.json", destination / "migration-fixture.json")
    if (source / "config").exists(): shutil.copytree(source / "config", destination / "config")
