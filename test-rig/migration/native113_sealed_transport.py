"""Current native code custody for fresh recipe and bay campaigns."""
from pathlib import Path
import matrix
from native113 import pinned_profile
from native113_code import INVENTORY, Server as CustodyServer, verify_code
from native113_lifecycle import read_json, require_native_boot

_profile = None
_pins = None


def configure(manifest):
    global _profile, _pins
    _profile = pinned_profile(Path(manifest), "target113")
    _pins = read_json(Path(manifest))["target113"]


def stage(runtime, profile):
    if _profile is None or profile[:2] != _profile[:2] or not set(profile[2]) <= set(_profile[2]):
        raise AssertionError("Only an explicit subset of the current native profile may be staged")
    launcher = matrix.stage(runtime, profile)
    verify_code(runtime, _pins["serverCode"])
    matrix.write_json(runtime / INVENTORY, _pins["serverCode"])
    return launcher


class Server(CustodyServer):
    def close(self):
        try:
            return super().close()
        finally:
            require_native_boot(Path(self.log.name))
