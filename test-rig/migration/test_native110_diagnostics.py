import unittest
from native110_diagnostics import PUBLIC_FORGE_SHA, audit_lines


class Native110DiagnosticsTest(unittest.TestCase):
    notice = "[13:54:31] [main/ERROR] [FML]: FML appears to be missing any signature data. This is not a good thing"

    def test_only_exact_notice_in_exact_public_forge_is_classified(self):
        result = audit_lines([self.notice], PUBLIC_FORGE_SHA)
        self.assertEqual(1, len(result["classifiedDiagnostics"]))
        for digest in (None, "changed"):
            with self.assertRaises(AssertionError): audit_lines([self.notice], digest)

    def test_other_error_after_notice_still_fails(self):
        for line in ("[13:54:31] [main/ERROR] [FML]: FML has been ordered to ignore the invalid or missing minecraft certificate.",
                     "[13:54:32] [Server thread/ERROR] [FML]: machine exploded",
                     "[13:54:32] [Server thread/FATAL] [orespawn]: unknown"):
            with self.assertRaises(AssertionError): audit_lines([self.notice, line], PUBLIC_FORGE_SHA)
