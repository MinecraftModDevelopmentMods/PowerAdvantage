import copy
import unittest
from unittest.mock import patch

from native114_respawn import native_respawn_verdict


class NativeRespawnAdapterTest(unittest.TestCase):
    def test_only_exact_native_handler_is_translated(self):
        for handler, expected in (("ServerPlayNetHandler.PERFORM_RESPAWN", "NetHandlerPlayServer.PERFORM_RESPAWN"),
                                  ("Other.PERFORM_RESPAWN", "Other.PERFORM_RESPAWN"),
                                  ("NetHandlerPlayServer.PERFORM_RESPAWN", "NetHandlerPlayServer.PERFORM_RESPAWN")):
            original = {"respawn": {"handler": handler}, "retain": {"Inventory": [17], "bed": "missing"}}
            before = copy.deepcopy(original)
            with patch("native114_respawn.respawn_verdict", return_value={"status": "TEST"}) as oracle:
                self.assertEqual({"status": "TEST"}, native_respawn_verdict(original, {1: "enchantment"}, {"radius": 10}))
                adapted = copy.deepcopy(before)
                adapted["respawn"]["handler"] = expected
                oracle.assert_called_once_with(adapted, {1: "enchantment"}, {"radius": 10})
            self.assertEqual(before, original)


if __name__ == "__main__":
    unittest.main()
