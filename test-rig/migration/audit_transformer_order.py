"""Read-only replay of raw packaged transformer-order qualification evidence."""

import argparse
import json
from pathlib import Path

import nbtlib

from actors import (PLAYER_KEYS, capture, compare as compare_actors, enchantments, freeze_result,
                    qualified_baselines, semantic)
from matrix import WORLD, compare as compare_blocks, digest, fingerprint, write_json
from transformer_order import (COREMODS, METAL_BLOCKS, assert_staged, block_semantics,
                               backup_provenance, migration_backups, order_evidence, strict_equal,
                               target_profile, validate_patch)


def audit(root):
    scope = json.loads((root / "scope.json").read_text())
    profile = target_profile(Path(scope["profile_manifest"]))
    if digest(Path(scope["profile_manifest"])) != scope["profile_manifest_sha256"]:
        raise AssertionError("Archived input manifest changed")
    patched = root / "coremods-order-test.jar"
    patch = validate_patch(profile[0] / COREMODS, patched)
    reported_build = json.loads((root / "probe-build.json").read_text())
    if any(reported_build[key] != value for key, value in patch.items()):
        raise AssertionError("Patched-library evidence changed")
    sources = json.loads((root / "immutable-source-hashes.json").read_text())
    for source in sources.values():
        if fingerprint(Path(source["path"]) / WORLD) != source["hashes"]:
            raise AssertionError("Immutable source world changed")
    published = json.loads((root / "results.json").read_text())
    results, states = {}, {}
    for route, verdict in published.items():
        name, mode = route.split("/")
        fixture, runtime = root / (name + "-mixed-input"), root / (name + "-" + mode)
        if fingerprint(fixture / WORLD) != json.loads((fixture / "fixture-hashes.json").read_text()):
            raise AssertionError("Mixed input world changed")
        assert_staged(runtime)
        if digest(runtime / COREMODS) != digest(patched):
            raise AssertionError("Staged test library changed")
        # Reconstruct the oracle from source actor NBT and the actual mixed input file.
        expected = capture(Path(sources[name]["path"]), "source")
        player_uuid = json.loads((fixture / "actor-fixture.json").read_text())["playerUuid"]
        expected["file"] = semantic(nbtlib.load(fixture / WORLD / "playerdata" / (player_uuid + ".dat")).unpack(),
                                    PLAYER_KEYS, enchantments(fixture))
        expected["embedded"] = expected["file"]
        expected["adapter"] = expected["file"]
        if expected != json.loads((fixture / "semantic-source.json").read_text()):
            raise AssertionError("Saved semantic oracle disagrees with raw input")
        converted = capture(runtime, "converted", runtime / "ordering-evidence/converted")
        adapter = capture(runtime, "converted-adapter", runtime / "ordering-evidence/converted-adapter")
        adapter.pop("embedded", None)
        restarted = capture(runtime, "restarted", runtime / "ordering-evidence/restarted")
        restarted.pop("embedded", None)
        replayed = {"conversion": compare_actors({k: v for k, v in expected.items() if k != "adapter"}, converted),
                    "adapter": compare_actors({k: v for k, v in expected.items() if k != "embedded"}, adapter),
                    "restart": compare_actors({k: v for k, v in expected.items() if k != "embedded"}, restarted),
                    "idempotence": strict_equal(adapter, restarted),
                    "backup_provenance": backup_provenance(fixture, runtime)}
        for label, control in (("converted", "converted_controls"), ("restarted", "restart_controls")):
            proof = order_evidence(runtime, mode, label)
            if proof != verdict[control]["order"]:
                raise AssertionError("Replayed actual order differs from published order")
            freeze = freeze_result(json.loads((runtime / f"actors-{label}.json").read_text()))
            if freeze["status"] != "PASS" or freeze != verdict[control]["freeze"]:
                raise AssertionError("Unproven zero-tick checkpoint")
            replayed["blocks_" + label] = compare_blocks(qualified_baselines()[name] / "migration-source.json",
                                                       runtime / f"migration-stock-{label}.json")
            full = json.loads((runtime / f"migration-{label}.json").read_text())
            stock = json.loads((runtime / f"migration-stock-{label}.json").read_text())
            if full[:-len(METAL_BLOCKS)] != stock:
                raise AssertionError("Stock witness subset is not the original raw snapshot")
            replayed["metals_" + label] = strict_equal(list(METAL_BLOCKS), [e["actualName"] for e in full[-len(METAL_BLOCKS):]])
        adapter_freeze = freeze_result(json.loads((runtime / "actors-converted-adapter.json").read_text()))
        if adapter_freeze["status"] != "PASS" or adapter_freeze != verdict["converted_controls"]["adapter_freeze"]:
            raise AssertionError("Unproven adapter zero-tick checkpoint")
        for key, value in replayed.items():
            if value != verdict[key]:
                raise AssertionError(f"Replay disagrees: {route}/{key}")
        backups = json.loads((runtime / "ordering-backups.json").read_text())
        if strict_equal(backups, migration_backups(runtime)) != verdict["backup_stability"]:
            raise AssertionError("Backup-stability replay disagrees")
        results[route] = replayed
        states.setdefault(name, {})[mode] = {"actors": adapter, "actors_restart": restarted,
                                           "blocks": block_semantics(runtime, "converted"),
                                           "blocks_restart": block_semantics(runtime, "restarted")}
    cross = {name: {mode: strict_equal(next(iter(modes.values())), state) for mode, state in modes.items()}
             for name, modes in states.items()}
    if cross != json.loads((root / "cross-order.json").read_text()):
        raise AssertionError("Replayed cross-order semantics disagree")
    semantic_pass = all(v["status"] == "PASS" for route in results.values() for v in route.values())
    cross_pass = all(v["status"] == "PASS" for modes in cross.values() for v in modes.values())
    return {"status": "PASS", "meaning": "Evidence integrity; semantic qualification is reported separately",
            "semantic_status": "PASS" if semantic_pass and cross_pass else "FAIL",
            "runtimes": len(results), "replayed": results, "cross_order": cross, "patch": patch,
            "immutable_source_count": len(sources)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run.resolve())
    write_json(args.output, result)
    print(f"{result['status']}: {result['runtimes']} runtimes; semantic={result['semantic_status']}")
    raise SystemExit(result["semantic_status"] != "PASS")
