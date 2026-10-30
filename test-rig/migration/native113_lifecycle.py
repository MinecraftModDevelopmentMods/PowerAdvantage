"""Disposable-runtime transport for expanded 1.13 checks; no expected-state writes."""

import json
from pathlib import Path
from matrix import copy_fixture, digest, stage, write_json
from native113 import LAUNCHER
from native113_diagnostics import audit_lines


def read_json(path): return json.loads(path.read_text(encoding="utf-8"))


def require_native_boot(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    diagnostics = audit_lines(text.splitlines())
    diagnostics.update(logSha256=digest(path), nativeProcessExit="Required separately by Server.close")
    write_json(path.with_suffix(".diagnostics.json"), diagnostics)
    failures = [line for line in text.splitlines() if "/ERROR]" in line or any(marker in line for marker in (
        "NoSuchFieldError", "NoSuchMethodError", "Failed to reload data packs", "Encountered an unexpected exception"))]
    if failures: raise AssertionError("Native runtime errors: " + str(failures[:5]))
    for marker in ("Starting minecraft server version 1.13.2", "Forge mod loading, version 25.0.223, for MC 1.13.2"):
        if marker not in text: raise AssertionError("Missing genuine native113 boot proof: " + marker)


def verify_staged(runtime, profile):
    expected = {jar.name: digest(jar) for jar in profile[2]}
    actual = {jar.name: digest(jar) for jar in (runtime / "mods").glob("*.jar")}
    if actual != expected: raise AssertionError("Staged mod bytes differ from pinned inputs")
    if digest(runtime / LAUNCHER) != digest(profile[0] / LAUNCHER): raise AssertionError("Staged launcher differs")


def prepare(runtime, profile, definition, port, source=None):
    if profile[1] != LAUNCHER: raise ValueError("Wrong engine for native113 test")
    launcher = stage(runtime, profile)
    if source is not None: copy_fixture(source, runtime)
    properties = runtime / "server.properties"
    lines = [line for line in properties.read_text().splitlines() if not line.startswith("server-port=")]
    properties.write_text("\n".join(lines) + f"\nserver-port={port}\n", encoding="utf-8")
    write_json(runtime / "lifecycle-fixture.json", definition)
    verify_staged(runtime, profile)
    return launcher
