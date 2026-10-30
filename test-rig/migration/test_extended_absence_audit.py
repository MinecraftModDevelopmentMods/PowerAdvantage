"""Independent raw event-vector proof controls; no native server activity."""
from copy import deepcopy
import unittest
from audit_extended_absence import interval_proof
import test_extended_absence_continuity as fixture_source


class AbsenceAuditControls(unittest.TestCase):
    def proof(self, fixture):
        f,before,samples,_=fixture
        return interval_proof(f,before,samples,"supply")

    def fixture(self,ticks=(10,50)):
        return fixture_source.ContinuousAbsenceTests().fixture(ticks)

    def test_exact_40_native_ticks(self):
        self.assertEqual("PASS",self.proof(self.fixture())["status"])

    def test_dense_189_tick_segment_not_last_38_tick_gap(self):
        proof=self.proof(self.fixture((1315,1336,1357,1378,1399,1422,1444,1466,1504)))
        self.assertEqual(("PASS",189,38),(proof["status"],proof["nativeTicks"],proof["lastAdjacentTicks"]))

    def test_39_ticks_is_not_40(self):
        self.assertEqual("FAIL",self.proof(self.fixture((10,30,49)))["status"])

    def test_hidden_reload_rejected(self):
        f=self.fixture();f[2][-1]["chunks"][0].update(loads=2,unloads=2)
        self.assertEqual("FAIL",self.proof(f)["status"])

    def test_observed_reload_rejected(self):
        f=self.fixture((10,30,90));f[2][1]["chunks"][0]["loaded"]=True
        self.assertEqual("FAIL",self.proof(f)["status"])

    def test_late_stable_suffix_does_not_erase_reload(self):
        f=self.fixture((10,30,90))
        for sample in f[2][1:]:sample["chunks"][0].update(loads=2,unloads=2)
        self.assertEqual("FAIL",self.proof(f)["status"])

    def test_clock_must_not_move_backwards(self):
        self.assertEqual("FAIL",self.proof(self.fixture((10,5,50)))["status"])

    def test_raw_inputs_are_not_changed(self):
        f=self.fixture();before=deepcopy(f);self.proof(f)
        self.assertEqual(before,f)


if __name__=="__main__":
    unittest.main()
