import copy
import unittest
from native115_campaign import REQUIRED, aggregate_shards, select_shard


def shards():
    names = sorted(REQUIRED["connected"])
    return [dict(kind="connected", routesManifestSha256="source", targetManifestSha256="candidate",
                 observationTicks=2048, shard=dict(index=index, count=3), status="PASS_SHARD", protocolUnchanged=True,
                 selectedRouteIds=names[index::3], routes=[dict(id=name, status="PASS") for name in names[index::3]])
            for index in range(3)]


class CampaignShardTests(unittest.TestCase):
    def test_shards_partition_every_route_exactly_once(self):
        names = sorted(REQUIRED["connected"])
        actual = [name for index in range(3) for name in select_shard(names, index, 3)]
        self.assertCountEqual(names, actual)
        self.assertEqual(10, len(aggregate_shards(shards())["routes"]))

    def test_missing_duplicate_or_failed_shard_cannot_pass(self):
        good = shards()
        for bad in (good[:2], [good[0], good[0], good[2]]):
            with self.assertRaises(AssertionError): aggregate_shards(bad)
        bad = copy.deepcopy(good); bad[1]["status"] = "FAIL"
        with self.assertRaises(AssertionError): aggregate_shards(bad)

    def test_mixed_candidate_or_interval_cannot_pass(self):
        for key, value in (("targetManifestSha256", "other"), ("observationTicks", 512)):
            bad = shards(); bad[1][key] = value
            with self.assertRaises(AssertionError): aggregate_shards(bad)

    def test_missing_duplicate_or_failed_route_cannot_pass(self):
        for mode in ("missing", "duplicate", "failed"):
            bad = shards()
            if mode == "missing": bad[0]["routes"].pop()
            elif mode == "duplicate": bad[0]["routes"][1] = dict(bad[0]["routes"][0])
            else: bad[0]["routes"][0]["status"] = "FAIL"
            with self.assertRaises(AssertionError): aggregate_shards(bad)

    def test_invalid_and_empty_shards_reject_before_launch(self):
        for index, count in ((-1, 3), (3, 3), (0, 0), (0, 16)):
            with self.assertRaises(ValueError): select_shard(["one"], index, count)
        with self.assertRaises(ValueError): select_shard([], 0, 1)


if __name__ == "__main__": unittest.main()
