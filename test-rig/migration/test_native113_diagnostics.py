import tempfile
from pathlib import Path
import unittest

from native113_diagnostics import audit_lines


class NativeFatalAuditTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.gettempdir()).resolve()
        self.cleanup = ("[12:00:00.000] [Thread-7/FATAL] [ne.mi.fm.pa.ModFileResourcePack/]: "
                        "Failed to clean up tempdir " + str(self.root / "modpacktmp12345"))
        self.shutdown = ["Saved the game", "Stopping the server"]

    def test_exact_post_save_cleanup_is_retained_not_hidden(self):
        result = audit_lines(self.shutdown + [self.cleanup])
        self.assertEqual(1, len(result["classifiedDiagnostics"]))
        self.assertEqual(self.cleanup, result["classifiedDiagnostics"][0]["message"])

    def test_same_notice_during_boot_is_not_excused(self):
        with self.assertRaises(AssertionError): audit_lines([self.cleanup])

    def test_a_stop_without_save_evidence_is_not_excused(self):
        with self.assertRaises(AssertionError): audit_lines(["Stopping server", self.cleanup])

    def test_world_paths_are_never_temp_cleanup(self):
        with self.assertRaises(AssertionError):
            audit_lines(self.shutdown + [self.cleanup.replace(str(self.root), str(self.root / "world"))])

    def test_other_fatal_messages_are_not_excused(self):
        with self.assertRaises(AssertionError):
            audit_lines(self.shutdown + [self.cleanup.replace("Failed to clean up tempdir", "Failed to create temporary directory")])

    def test_other_errors_still_fail_after_classified_cleanup(self):
        with self.assertRaises(AssertionError):
            audit_lines(self.shutdown + [self.cleanup, "[Server thread/ERROR] bad state"])

    def test_only_the_exact_native_configuration_reload_notice_is_classified(self):
        notice = "[12:00:00] [Thread-1/FATAL] [ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!"
        self.assertEqual("FORGE_25_CONFIG_RELOAD_NOTICE", audit_lines([notice])["classifiedDiagnostics"][0]["classification"])
        for bad in (notice.replace("ForgeConfig/CORE", "Other/CORE"), notice + " error"):
            with self.assertRaises(AssertionError): audit_lines([bad])
        with self.assertRaises(AssertionError): audit_lines([notice, "[Thread-1/ERROR] invalid config"])
