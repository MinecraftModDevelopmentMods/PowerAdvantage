from argparse import Namespace
import unittest
from native116_campaign import observation_ticks


class ConnectedObservationIntervalTests(unittest.TestCase):
    def test_historical_default_remains_512(self):
        self.assertEqual(512, observation_ticks(Namespace()))

    def test_fixed_longer_interval_is_retained(self):
        for ticks in (1, 1024, 2048, 4000):
            self.assertEqual(ticks, observation_ticks(Namespace(ticks=ticks)))

    def test_unsafe_or_noninteger_intervals_are_rejected(self):
        for value in (0, -1, 4001, True, 2048.0, "2048"):
            with self.assertRaises(ValueError): observation_ticks(Namespace(ticks=value))


if __name__ == "__main__": unittest.main()
