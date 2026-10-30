import copy
import unittest
from native113_chunk_readiness import validate


class NativeReadinessTests(unittest.TestCase):
    def test_complete_native_state_passes_but_incomplete_state_waits(self):
        document = dict(label="case", worldTime=12, ready=True, chunks=[dict(x=0, z=0, loaded=True)])
        self.assertTrue(validate(document, "case", (0, 0, 0), (15, 0, 15)))
        document.update(ready=False, chunks=[dict(x=0, z=0, loaded=False)])
        self.assertFalse(validate(document, "case", (0, 0, 0), (15, 0, 15)))

    def test_ready_label_cannot_override_missing_or_false_native_evidence(self):
        base = dict(label="case", worldTime=12, ready=True, chunks=[dict(x=0, z=0, loaded=True)])
        for change in (dict(chunks=[]), dict(chunks=[dict(x=0, z=0, loaded=False)]),
                       dict(label="stale"), dict(chunks=[dict(x=1, z=0, loaded=True)]),
                       dict(chunks=[dict(x=0, z=0, loaded=1)])):
            bad = copy.deepcopy(base); bad.update(change)
            with self.assertRaises(AssertionError): validate(bad, "case", (0, 0, 0), (15, 0, 15))
