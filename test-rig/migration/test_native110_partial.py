import copy
import unittest
from native110_partial import REQUIRED, validate_partial


def evidence():
    ids = sorted(REQUIRED)
    catalog = {"cases": [{"id": key} for key in ids]}
    measured = {"held": True, "cases": []}
    for key in ids:
        endpoint = key.endswith(("-source", "-consumer"))
        import base64, gzip, io, nbtlib
        tag = nbtlib.File(nbtlib.parse_nbt('{Energy:[I;1140457472],id:"fixture:tile",Count:1b}'))
        output = io.BytesIO(); tag.write(output)
        encoded = base64.b64encode(gzip.compress(output.getvalue())).decode()
        saved = [{"pos": [50000, 84, 50000], "observedNbtBase64": encoded}] if endpoint else []
        block = [{"pos": [50000, 84, 50000], "state": "fixture:block"}]
        measured["cases"].append({"id": key, "status": "PASS", "partial.completed": True,
            "partial.cleanupCalled": True, "partial.measuredTicks": 1100, "startTick": 10, "finishTick": 1110,
            "partial.absentTicks": 240, "partial.selectedChunk": [3125, 3125], "partial.restoreTick": 481,
            "partial.events": [
                {"operation": "unload", "tick": 241, "chunk": [3125, 3125], "unloadState": saved, "networkBlocks": block},
                {"operation": "load", "tick": 482, "chunk": [3125, 3125], "typedStateEqual": True,
                 "explicitRecovery": True, "unloadState": copy.deepcopy(saved), "networkBlocks": copy.deepcopy(block)}],
            "partial.observations": [
                {"tick": tick, "selectedPresent": False, "explicitRecovery": False, "retainedLoaded": True}
                for tick in (260, 480)]})
    fixture = {"cases": [{"id": key} for key in ids]}
    for row in measured["cases"]:
        applicable = not row["id"].endswith("-source")
        row["partial.isolationApplicable"] = applicable
        if not applicable:
            continue
        steam = row["id"].split("/")[-1].split("-")[0] == "steam"
        samples = [{"tick": tick, "source": 5000 - (tick - 241) * (0.0625 if steam else 0),
                    "expectedSource": 5000 - (tick - 241) * (0.0625 if steam else 0),
                    "sourceLoaded": True, "selectedAbsent": True} for tick in range(241, 482)]
        row.update({"partial.isolationSamples": samples, "partial.isolationAnchorTick": 241,
            "partial.isolationLastTick": 481, "partial.isolationAnchorSource": 5000,
            "partial.isolationLastSource": samples[-1]["source"], "partial.isolationStableTicks": 240})
    cold = {"held": True, "constructionCallsThisProcess": 0, "cases": [{"id": key} for key in ids]}
    return catalog, measured, fixture, cold


class NativePartialOracleTest(unittest.TestCase):
    def test_ghost_transfer_fails_even_when_global_conservation_and_all_flags_pass(self):
        for kind in ("steam", "electric", "fluid"):
            values = evidence()
            row = next(r for r in values[1]["cases"] if r["id"] == "power/partial/" + kind)
            for sample in row["partial.isolationSamples"][1:]:
                sample["source"] -= 1
            row["partial.isolationLastSource"] -= 1
            with self.assertRaises(AssertionError): validate_partial(*values)

    def test_unobserved_late_ghost_flow_cannot_hide_in_equal_anchor_and_final_totals(self):
        values = evidence()
        row = next(r for r in values[1]["cases"] if r["id"] == "power/partial/electric")
        row["partial.isolationSamples"][120]["source"] -= 4
        with self.assertRaises(AssertionError): validate_partial(*values)

    def test_isolation_must_cover_every_absent_tick_not_only_a_passing_subset(self):
        for change in (lambda r: r.pop("partial.isolationSamples"),
                lambda r: r["partial.isolationSamples"].pop(80),
                lambda r: r["partial.isolationSamples"][0].update(sourceLoaded=False),
                lambda r: r.update({"partial.isolationStableTicks": 220}),
                lambda r: r["partial.isolationSamples"].pop()):
            values = evidence()
            row = next(r for r in values[1]["cases"] if r["id"] == "power/partial/fluid-consumer")
            change(row)
            with self.assertRaises(AssertionError): validate_partial(*values)

    def test_all_nine_cases_and_actual_witnesses_are_required(self):
        values = evidence()
        self.assertEqual("PASS", validate_partial(*values)["status"])
        self.assertEqual(9, len(validate_partial(*values)["cases"]))

    def test_missing_or_duplicate_case_is_not_a_passing_subset(self):
        for index in range(4):
            values = evidence()
            values[index]["cases"].pop()
            with self.assertRaises(AssertionError): validate_partial(*values)
            values = evidence()
            values[index]["cases"].append(copy.deepcopy(values[index]["cases"][0]))
            with self.assertRaises(AssertionError): validate_partial(*values)

    def test_flags_timing_and_native_events_cannot_be_manufactured(self):
        changes = [
            lambda r: r.update({"partial.completed": False}),
            lambda r: r.update({"partial.absentTicks": 1}),
            lambda r: r.update({"partial.measuredTicks": 1099}),
            lambda r: r["partial.events"].pop(),
            lambda r: r["partial.events"].append(copy.deepcopy(r["partial.events"][0])),
            lambda r: r["partial.events"][1].update(typedStateEqual=False),
            lambda r: r["partial.events"][1].update(explicitRecovery=False),
            lambda r: r["partial.events"][0].update(tick=480),
            lambda r: r["partial.observations"][0].update(retainedLoaded=False),
            lambda r: r["partial.observations"].pop(),
            lambda r: r["partial.events"][1].update(failure="hidden"),
            lambda r: r["partial.events"][1].update(networkBlocks=[])]
        for change in changes:
            values = evidence(); change(values[1]["cases"][0])
            with self.assertRaises(AssertionError): validate_partial(*values)

    def test_cold_proof_cannot_be_missing_rebuilt_or_unfrozen(self):
        for change in (lambda r: r.update(held=False), lambda r: r.update(constructionCallsThisProcess=1)):
            values = evidence(); change(values[3])
            with self.assertRaises(AssertionError): validate_partial(*values)

    def test_actual_callback_nbt_is_compared_not_just_pass_flags(self):
        values = evidence()
        row = next(r for r in values[1]["cases"] if r["id"].endswith("-source"))
        row["partial.events"][1]["unloadState"] = []
        with self.assertRaises(AssertionError): validate_partial(*values)


if __name__ == "__main__":
    unittest.main()
