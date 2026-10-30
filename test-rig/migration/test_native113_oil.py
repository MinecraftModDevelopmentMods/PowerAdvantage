"""Native 1.13 oil protocol negative controls; no Minecraft process is mocked as gameplay proof."""

from copy import deepcopy
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import native113_oil as native
import oil_transfer


class NativeOilTests(unittest.TestCase):
    def runner(self):
        runner = native.NativeOilRun(Path("unit-runtime"), Path("unit-java-not-executed"),
                                     native.LAUNCHER, native.fixtures(dict(jars=[])), {}, {})
        runner.server = Mock()
        runner.boots = [native.BOOT_LABELS[0]]
        return runner

    def test_exact_original_geometry_and_no_implicit_mineralogy(self):
        self.assertEqual(native.fixtures(dict(jars=[])), oil_transfer.fixtures())
        with self.assertRaises(ValueError):
            native.fixtures(dict(jars=[dict(id="mineralogy")]))

    def test_bounds_overlap_and_disconnected_pipes_are_rejected(self):
        original = native.fixtures(dict(jars=[]))
        for mutate in (lambda lanes: lanes.append(deepcopy(lanes[0])),
                       lambda lanes: lanes[0]["pipes"][0].__setitem__(0, 0),
                       lambda lanes: lanes[0]["max"].__setitem__(1, 255)):
            lanes = deepcopy(original)
            mutate(lanes)
            with self.assertRaises(AssertionError):
                native.require_bounds(lanes)

    def test_no_bucket_before_empty_baseline_or_after_final_restart(self):
        runner = self.runner()
        with self.assertRaises(AssertionError):
            runner.command("advlifecycle place-buckets premature")
        runner.pending_construction.clear()
        runner.seal_construction()
        runner.permit_buckets()
        runner.command("advlifecycle place-buckets first")
        with self.assertRaises(AssertionError):
            runner.command("advlifecycle place-buckets duplicate")
        runner.boots = list(native.BOOT_LABELS)
        with self.assertRaises(AssertionError):
            runner.permit_buckets()

    def test_no_world_repair_or_late_reconstruction(self):
        runner = self.runner()
        command = runner.pending_construction[0]
        runner.pending_construction.clear()
        runner.seal_construction()
        for forbidden in (command, "data merge block 1 2 3 {Tank:{Amount:1000}}",
                          "advlifecycle build hidden", "advworks reset all"):
            with self.subTest(command=forbidden), self.assertRaises(AssertionError):
                runner.command(forbidden)

    def test_save_acknowledgement_is_required(self):
        runner = self.runner()
        runner.command("save-all flush")
        runner.server.send.assert_called_once_with("save-all flush")
        runner.server.wait.assert_called_once_with("Saved the game", 120)

    def test_wrong_engine_or_out_of_order_boot_never_starts(self):
        runner = self.runner()
        runner.server = None
        runner.launcher = "forge-1.14.4-28.2.26.jar"
        with self.assertRaises(AssertionError):
            runner.boot(native.BOOT_LABELS[1])

    def test_staged_code_must_match_sealed_inventory(self):
        with patch.object(native, "verify_code", side_effect=AssertionError("changed code")) as check:
            with self.assertRaisesRegex(AssertionError, "changed code"):
                native.verify_staged(Path("runtime"), dict(serverCode=dict(files={})), {})
            check.assert_called_once()

    def test_missing_duplicate_witnesses_fail_before_quantity_oracle(self):
        lanes = native.fixtures(dict(jars=[]))
        document = dict(cases={lane["id"]: [dict(pos=p) for p in lane["witnesses"]] for lane in lanes})
        native.require_document(document, lanes)
        document["cases"][lanes[0]["id"]].pop()
        with self.assertRaises(AssertionError):
            native.require_document(document, lanes)


if __name__ == "__main__":
    unittest.main()
