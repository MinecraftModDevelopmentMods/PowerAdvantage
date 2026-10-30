"""Exact, code-only Forge seed custody; worlds and configuration are not code."""

import json
from pathlib import Path
from rig_transport import Server as HistoricalServer, digest

INVENTORY = "native112-code-inventory.json"


def code_files(root):
    root = Path(root)
    paths = list((root / "libraries").rglob("*")) + list(root.glob("*.jar"))
    return {path.relative_to(root).as_posix(): {"sha256": digest(path), "size": path.stat().st_size}
            for path in sorted(paths) if path.is_file()}


def verify_code(root, expected=None):
    root = Path(root)
    receipt = root / INVENTORY
    if expected is None:
        expected = json.loads(receipt.read_text(encoding="utf-8"))
    files = expected.get("files")
    if not isinstance(files, dict) or not files:
        raise ValueError("Missing sealed native112 code inventory")
    if code_files(root) != files:
        raise AssertionError("Native112 server code differs from its exact sealed inventory: " + str(root))
    return expected


def verify_if_sealed(root):
    if (Path(root) / INVENTORY).is_file():
        verify_code(root)


class CodeCustody:
    """Adds pre-boot and post-shutdown checks without changing server commands."""
    def __init__(self, runtime, *args, **kwargs):
        self.code_runtime = Path(runtime)
        verify_if_sealed(self.code_runtime)
        super().__init__(runtime, *args, **kwargs)

    def close(self):
        try:
            return super().close()
        finally:
            verify_if_sealed(self.code_runtime)


class Server(CodeCustody, HistoricalServer):
    pass
