"""Run native 1.13 lifecycle oracles with sealed current jars, not old defaults."""

import argparse
from argparse import Namespace
from pathlib import Path
import time
from unittest.mock import patch
from contextlib import ExitStack

import lifecycle
import extended_lifecycle
import pipe_only_lifecycle
import matrix
import native113_chunk_readiness
from native113 import LAUNCHER, output_path, pinned_profile, require_disjoint
from native113_campaign_support import verify_source
from native113_code import INVENTORY, Server as CustodyServer, verify_code
from native113_lifecycle import read_json, require_native_boot


class NativeServer(CustodyServer):
    def close(self):
        try:
            return super().close()
        finally:
            require_native_boot(Path(self.log.name))


def run(args):
    output = output_path(args.output)
    for path in (args.source, args.source_manifest, args.source_hashes, args.target_manifest):
        require_disjoint(output, path)
    source_before, _ = verify_source(args)
    target = pinned_profile(args.target_manifest, "target113")
    pins = read_json(args.target_manifest)["target113"]
    transport = output.with_name(output.name + "-inputs")
    if transport.exists():
        raise FileExistsError(transport)
    matrix.write_json(transport / "profiles/packaged-current.json", dict(mods=[
        dict(path=str(jar), file=jar.name, sha256=matrix.digest(jar)) for jar in target[2]]))
    original_stage = matrix.stage
    original_probe = lifecycle.LifecycleRun.probe

    def stage(runtime, profile):
        if profile != target or profile[1] != LAUNCHER:
            raise AssertionError("Only the sealed native destination may be staged")
        launcher = original_stage(runtime, profile)
        properties = runtime / "server.properties"
        lines = [line for line in properties.read_text().splitlines()
                 if not line.startswith(("server-port=", "generator-settings="))]
        properties.write_text("\n".join(lines) + f"\nserver-port={args.port}\ngenerator-settings=\n", encoding="utf-8")
        verify_code(runtime, pins["serverCode"])
        matrix.write_json(runtime / INVENTORY, pins["serverCode"])
        return launcher

    def probe(runner, operation, label):
        result = original_probe(runner, operation, label)
        if operation != "load":
            return result
        pending = list(runner.definition)
        for attempt in range(80):
            for fixture in list(pending):
                low, high = fixture["min"], fixture["max"]
                key = f"ready-{label}-{fixture['id']}-{attempt:02}"
                runner.command(f"advchunks {key} {low[0]} {low[2]} {high[0]} {high[2]}")
                runner.server.wait("ADVCHUNKS OBSERVED " + key)
                observed = read_json(runner.runtime / "advantage-works-results" / f"chunk-readiness-{key}.json")
                if native113_chunk_readiness.validate(observed, key, low, high):
                    pending.remove(fixture)
            if not pending:
                return result
            time.sleep(.05)
        raise TimeoutError("Lifecycle chunks did not complete native loading before construction")

    module = {"lifecycle": lifecycle, "extended": extended_lifecycle, "pipe": pipe_only_lifecycle}[args.kind]
    helpers = [Path(__file__), Path(module.__file__), Path(lifecycle.__file__),
               Path(native113_chunk_readiness.__file__)]
    protocol = {str(path): matrix.digest(path) for path in helpers}
    settings = Namespace(java8=args.java8, migrated=args.source, output=output, port=args.port)
    status, error = "FAIL", None
    try:
        with ExitStack() as context:
            for item in (lifecycle, extended_lifecycle, pipe_only_lifecycle):
                context.enter_context(patch.object(item, "profiles", lambda _: {"target113": target}))
                context.enter_context(patch.object(item, "stage", stage))
                context.enter_context(patch.object(item, "RIG", transport))
            context.enter_context(patch.object(lifecycle, "Server", NativeServer))
            context.enter_context(patch.object(lifecycle.LifecycleRun, "probe", probe))
            module.run(settings)
        status = "PASS"
    except Exception as failure:
        error = repr(failure)
        raise
    finally:
        intact = matrix.fingerprint(args.source) == source_before
        unchanged = protocol == {str(path): matrix.digest(path) for path in helpers}
        if not intact or not unchanged:
            status = "FAIL"
        matrix.write_json(output / "native113-replay-audit.json", dict(
            status=status, failure=error, kind=args.kind, sourceUnchanged=intact,
            protocolUnchanged=unchanged, protocolSha256=protocol,
            targetManifestSha256=matrix.digest(args.target_manifest)))
        if not intact or not unchanged:
            raise AssertionError("Protected source or replay protocol changed")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("lifecycle", "extended", "pipe"))
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--source-key", default="target113")
    parser.add_argument("--source-hashes", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    raise SystemExit(run(parser.parse_args()))
