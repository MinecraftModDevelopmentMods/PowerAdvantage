import unittest
from native112_diagnostics import audit_lines, LEGACY_ORESPAWN_SHA


class NativeDiagnosticsTests(unittest.TestCase):
    def test_known_notice_is_recorded_only_for_the_exact_dependency(self):
        message = "[12:00:00] [Server thread/FATAL] [orespawn]: Loading Integration For basemetals"
        self.assertEqual(message, audit_lines([message], LEGACY_ORESPAWN_SHA)["classifiedDiagnostics"][0]["message"])
        for wrong in (None, "0" * 64):
            with self.assertRaises(AssertionError): audit_lines([message], wrong)

    def test_other_fatals_and_errors_remain_failures(self):
        for message in ("[Server thread/FATAL] [orespawn]: bad integration",
                        "[12:00:00] [Server thread/FATAL] [orespawn]: Loading Integration For unknown",
                        "[Server thread/ERROR] broken machine"):
            with self.assertRaises(AssertionError): audit_lines([message], LEGACY_ORESPAWN_SHA)
