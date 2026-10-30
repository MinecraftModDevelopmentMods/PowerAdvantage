"""Fail-closed per-source orchestration controls; no servers or world mutation."""
import copy
from pathlib import Path
import unittest

import legacy_bucket_connected as wrapper
import native110_per_source_connected as matrix
from test_legacy_bucket_connected import qualified, target, primary


def table():
    rows = []
    for key in sorted(matrix.SOURCES):
        base = matrix.BETA4 if key == "maintained110" else matrix.BASE24
        rows.append(dict(sourceKey=key, sourceBaseSha256=base,
                         noticePolicy="strict" if base == matrix.BETA4 else "base24-proof",
                         targetManifest=key + ".json", targetManifestSha256="a" * 64))
    return dict(routes=rows, bucketProof="proof.json", bucketProofSha256="b" * 64)


def results():
    routes, boots = [], []
    for row in table()["routes"]:
        routes.append(dict(sourceKey=row["sourceKey"], status="PASS_ROUTE", sourceUnchanged=True,
            sourceBaseSha256=row["sourceBaseSha256"],
            targetManifest=row["targetManifest"], targetManifestSha256=row["targetManifestSha256"],
            intervals={phase: dict(status="PASS", requestedTicks=2048, advancedTicks=2048)
                       for phase in ("converted", "restarted")}))
        for phase in ("converted", "restarted"):
            boots.append(dict(sourceKey=row["sourceKey"], phase=phase, status="PASS",
                sourceBaseSha256=row["sourceBaseSha256"], targetBaseSha256=row["sourceBaseSha256"]))
    return routes, boots


class PerSourceConnectedTest(unittest.TestCase):
    def test_exact_three_source_full_manifest_table(self):
        matrix.table_contract(table())

    def test_missing_duplicate_and_unknown_routes_are_rejected(self):
        for mutation in ("missing", "duplicate", "unknown"):
            doc = table()
            if mutation == "missing": doc["routes"].pop()
            elif mutation == "duplicate": doc["routes"][-1] = copy.deepcopy(doc["routes"][0])
            else: doc["routes"][0]["sourceKey"] = "not-a-qualified-source"
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                matrix.table_contract(doc)

    def test_every_source_requires_an_explicit_full_manifest_and_hash(self):
        for field in ("targetManifest", "targetManifestSha256"):
            doc = table(); doc["routes"][0].pop(field)
            with self.subTest(field=field), self.assertRaises(AssertionError):
                matrix.table_contract(doc)

    def test_wrong_or_missing_bucket_proof_is_rejected(self):
        for field in ("bucketProof", "bucketProofSha256"):
            doc = table(); doc.pop(field)
            with self.subTest(field=field), self.assertRaises(AssertionError):
                matrix.table_contract(doc)

    def test_beta4_cannot_receive_a_legacy_notice_permission(self):
        doc = table()
        next(r for r in doc["routes"] if r["sourceKey"] == "maintained110")["noticePolicy"] = "base24-proof"
        with self.assertRaises(AssertionError): matrix.table_contract(doc)

    def test_legacy_base_cannot_silently_become_an_unknown_identity(self):
        doc = table(); doc["routes"][0]["sourceBaseSha256"] = "c" * 64
        with self.assertRaises(AssertionError): matrix.table_contract(doc)

    def test_base_identity_must_match_source_and_target_before_a_boot(self):
        proof = qualified()
        row = dict(sourceBaseSha256=matrix.BASE24, noticePolicy="base24-proof")
        source = dict(baseSha256=matrix.BASE24)
        destination = target(proof)
        matrix.bind_source(row, source, destination, proof)
        for identity in (matrix.BETA4, "unknown"):
            bad = copy.deepcopy(destination)
            next(m for m in bad["mods"] if m["id"] == "basemetals")["sha256"] = identity
            with self.subTest(targetBase=identity), self.assertRaises(AssertionError):
                matrix.bind_source(row, source, bad, proof)

    def test_a_maintained_beta4_source_is_never_downgraded_to_base24(self):
        proof = qualified()
        row = dict(sourceBaseSha256=matrix.BETA4, noticePolicy="strict")
        source = dict(baseSha256=matrix.BETA4)
        with self.assertRaises(AssertionError):
            matrix.bind_source(row, source, target(proof), proof)
        correct = target(proof)
        next(m for m in correct["mods"] if m["id"] == "basemetals")["sha256"] = matrix.BETA4
        matrix.bind_source(row, source, correct, proof)

    def test_wrong_core_proof_cannot_authorize_the_legacy_target(self):
        proof = qualified(); destination = target(proof)
        next(m for m in destination["mods"] if m["id"] == "advantageworkstest")["sha256"] = "different-harness"
        with self.assertRaises(AssertionError):
            matrix.bind_source(dict(sourceBaseSha256=matrix.BASE24, noticePolicy="base24-proof"),
                               dict(baseSha256=matrix.BASE24), destination, proof)

    def test_explicit_singleton_primary_keeps_all_six_phase_checks(self):
        document, processes = primary()
        key = "original110"
        document["routes"] = [r for r in document["routes"] if r["sourceKey"] == key]
        processes = {k: p for k, p in processes.items() if p["sourceKey"] == key}
        wrapper.require_primary(document, processes, (key,))
        with self.assertRaises(AssertionError): wrapper.require_primary(document, processes)
        document["routes"][0]["phases"]["restarted-completed-work"]["status"] = "FAIL"
        with self.assertRaises(AssertionError): wrapper.require_primary(document, processes, (key,))

    def test_empty_duplicate_or_unknown_declared_source_selection_fails(self):
        for keys in ((), ("unknown",), ("early110", "early110")):
            with self.subTest(keys=keys), self.assertRaises(AssertionError):
                wrapper.declared_sources(keys)
        self.assertEqual(wrapper.SOURCES, wrapper.declared_sources())

    def test_aggregate_requires_exact_three_routes_six_boots_and_intervals(self):
        routes, boots = results()
        matrix.require_matrix(routes, boots)
        for mutation in ("route", "boot", "interval", "ticks", "custody", "manifest", "downgrade", "source-only", "unknown"):
            routes, boots = results()
            if mutation == "route": routes.pop()
            elif mutation == "boot": boots.pop()
            elif mutation == "interval": routes[0]["intervals"].pop("restarted")
            elif mutation == "ticks": routes[0]["intervals"]["converted"]["advancedTicks"] = 2047
            elif mutation == "custody": routes[0]["sourceUnchanged"] = False
            elif mutation == "manifest": routes[0].pop("targetManifest")
            elif mutation == "downgrade": next(b for b in boots if b["sourceKey"] == "maintained110")["targetBaseSha256"] = matrix.BASE24
            elif mutation == "source-only": routes[0]["status"] = "PASS_SOURCE"
            else: routes[0]["sourceKey"] = "unknown"
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                matrix.require_matrix(routes, boots)

    def test_only_fixture_selection_is_patched_not_the_operating_oracle(self):
        source = Path(matrix.__file__).read_text()
        self.assertIn('patch.object(connected, "SOURCE_KEYS", (key,))', source)
        self.assertNotIn("patch.object(connected.oracle", source)
        self.assertIn("match_source_metals=False", source)
        self.assertIn("connected.strict_snapshot(previous, loaded)", source)
        self.assertIn("connected.oracle.operation(loaded, worked, 2048)", source)
        self.assertIn("connected.semantic_machine_audit.connected_interval(loaded, worked, definition, 2048)", source)


if __name__ == "__main__":
    unittest.main()
