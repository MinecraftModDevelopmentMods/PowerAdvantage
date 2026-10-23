import unittest

from matrix import intermediate_profile


class BridgeProfileTests(unittest.TestCase):
    def test_default_keeps_the_qualified_historical_bridge(self):
        legacy = ("legacy-seed", "forge112.jar", ["original-electric.jar"])
        self.assertIs(legacy, intermediate_profile({"maintained112": legacy}))

    def test_override_leaves_the_historical_source_profile_unchanged(self):
        legacy = ("legacy-seed", "forge112.jar", ["original-electric.jar"])
        repaired = ("legacy-seed", "forge112.jar", ["repaired-electric.jar"])
        available = {"maintained112": legacy, "bridge112": repaired}
        self.assertIs(repaired, intermediate_profile(available))
        self.assertIs(legacy, available["maintained112"])

    def test_explicit_override_can_run_without_a_historical_bridge_entry(self):
        repaired = ("legacy-seed", "forge112.jar", ["repaired-electric.jar"])
        self.assertIs(repaired, intermediate_profile({"bridge112": repaired}))


if __name__ == "__main__":
    unittest.main()
