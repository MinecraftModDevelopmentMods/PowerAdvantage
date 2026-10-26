"""Read-only native coremod order, actor, block and backup evidence replay."""

import argparse
import json
from pathlib import Path

import actors
import transformer_order as historical
from matrix import compare, digest, fingerprint, write_json
from native114 import LEGACY_POWER, native_normalized, require_clean_log
from native114_transformer_order import COREMODS, native_order_evidence


def replay(root):
    results = json.loads((root / "results.json").read_text())
    sources = json.loads((root / "source-fingerprints.json").read_text())
    build = json.loads((root / "probe-build.json").read_text())
    summary = json.loads((root / "summary.json").read_text())
    findings, evidence, modes = [], {}, {}
    patched = root / "coremods-order-test.jar"
    if digest(patched) != build["patched_sha256"] or digest(patched) != summary["patchedLibrarySha256"]:
        findings.append("Patched observer library changed")
    for name, entry in sources.items():
        if fingerprint(Path(entry["source"])) != entry["fingerprint"]:
            findings.append(name + ": protected source changed")
    for case, recorded in results.items():
        name, mode = case.split("/")
        runtime = root / (name + "-" + mode)
        historical.assert_staged(runtime)
        if digest(runtime / COREMODS) != digest(patched):
            findings.append(case + ": staged observer library changed")
        expected = json.loads((root / (name + "-mixed-input") / "semantic-source.json").read_text())
        actual = {}
        proofs = {}
        for label in ("converted", "converted-adapter", "restarted"):
            actual[label] = actors.capture(runtime, label, runtime / "ordering-evidence" / label)
            if label != "converted":
                actual[label].pop("embedded", None)
        for label in ("converted", "restarted"):
            proof = native_order_evidence(runtime, mode, label)
            if proof != recorded[("converted" if label == "converted" else "restart") + "-controls"]["order"]:
                findings.append(case + "/" + label + ": order proof differs from raw events/dumps")
            proofs[label] = proof
            require_clean_log(runtime / ("console-" + label + ".log"))
            entries = json.loads((runtime / f"migration-{label}.json").read_text())
            stock = runtime / f"migration-stock-{label}.json"
            if json.loads(stock.read_text()) != entries[:-len(historical.METAL_BLOCKS)]:
                findings.append(case + "/" + label + ": stock snapshot differs from raw full snapshot")
            blocks = LEGACY_POWER / "run-migration-matrix" / (
                "original-cyano-20261005-r1/original110" if name == "original110" else "full-20261005-r1/maintained112")
            blocks_result = compare(blocks / "migration-source.json", stock, native_normalized)
            metals_result = historical.strict_equal(list(historical.METAL_BLOCKS),
                [entry["actualName"] for entry in entries[-len(historical.METAL_BLOCKS):]])
            if blocks_result != recorded["blocks-" + label] or blocks_result["status"] != "PASS":
                findings.append(case + "/" + label + ": source block/tile comparison differs/fails")
            if metals_result != recorded["metals-" + label] or metals_result["status"] != "PASS":
                findings.append(case + "/" + label + ": mixed metal comparison differs/fails")
        fresh = {
            "conversion": actors.compare({k: v for k, v in expected.items() if k != "adapter"}, actual["converted"]),
            "adapter": actors.compare({k: v for k, v in expected.items() if k != "embedded"}, actual["converted-adapter"]),
            "restart": actors.compare({k: v for k, v in expected.items() if k != "embedded"}, actual["restarted"]),
            "idempotence": historical.strict_equal(actual["converted-adapter"], actual["restarted"]),
            "backup-provenance": historical.backup_provenance(root / (name + "-mixed-input"), runtime),
        }
        for key, value in fresh.items():
            if value != recorded[key] or value["status"] != "PASS":
                findings.append(case + "/" + key + ": actor or backup replay differs/fails")
        modes.setdefault(name, {})[mode] = {"actors": actual["converted-adapter"], "actorsRestart": actual["restarted"],
            "blocks": historical.block_semantics(runtime, "converted"), "blocksRestart": historical.block_semantics(runtime, "restarted")}
        evidence[case] = {"oracles": fresh, "proofs": proofs}
    cross = {}
    for name, values in modes.items():
        reference = next(iter(values.values()))
        cross[name] = {mode: historical.strict_equal(reference, value) for mode, value in values.items()}
        if cross[name] != summary["crossOrder"][name] or any(result["status"] != "PASS" for result in cross[name].values()):
            findings.append(name + ": cross-order actor/block/tile semantics differ")
    return {"status": "FAIL" if findings else "PASS", "runtimes": len(results), "findings": findings,
            "crossOrder": cross, "evidence": evidence, "backupCaveat": "Individual mod backup original-byte custody depends on callback order"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = replay(args.run.resolve())
    write_json(args.output, result)
    print(result["status"] + ": " + str(result["runtimes"]) + " runtimes; " + str(len(result["findings"])) + " integrity findings")
    raise SystemExit(int(result["status"] != "PASS"))
