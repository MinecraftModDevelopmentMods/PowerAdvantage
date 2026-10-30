"""Independent negative controls for the cold-reload redstone operating oracle."""

import copy
from pathlib import Path
import struct
import unittest

from native116_redstone_reload import definitions, validate_readiness, validate_reload, validate_seed


def row(case, progress, held=False, energy=None, targeted=True):
    energy = 500 - 24 * progress if energy is None else energy
    x, y, z = case["pos"]
    bits = struct.unpack(">i", struct.pack(">f", energy))[0]
    states = ','.join('{Name:"minecraft:obsidian"}' for _ in range(9))
    items = '{Slot:0b,id:"minecraft:dirt",Count:7b,tag:{AdvWorksWitness:"' + case["id"] + '"}},{Slot:1b,id:"minecraft:apple",Count:13b}'
    nbt = ('{id:"electricadvantage:electric_drill",x:%d,y:%d,z:%d,Energy:[I;%d],progress:%ds,'
           'Items:[%s],targetX:%d,targetY:%d,targetZ:%d,TargetStates:[%s]}') % (
               x, y, z, bits, progress, items, x, y, z - 1, states)
    if not targeted:
        nbt = nbt[:nbt.index(',targetX:')] + '}'
    return dict(case, block="electricadvantage:electric_drill", active=True,
                state="Block{electricadvantage:electric_drill}[active=true,facing=north]",
                signal=held, holdBlock="minecraft:redstone_block" if held else "minecraft:air",
                energy=energy, progress=progress, nbt=nbt,
                targets=[dict(pos=[x + dx, y + dy, z - 1], state="Block{minecraft:obsidian}")
                         for dx in range(-1, 2) for dy in range(-1, 2)])


def documents():
    initial = [row(case, 0, targeted=False) for case in definitions()]
    for value in initial:
        value.update(active=False, state=value["state"].replace("active=true", "active=false"))
    trace = []
    for i in range(1, 10):
        values = []
        for case in definitions():
            acquired = next(t for t in range(101, 109) if (t + case["offset"]) % 8 == 0)
            values.append(row(case, max(0, 100 + i - acquired), targeted=100 + i >= acquired))
        trace.append(dict(index=i, tickStartWorldTime=99 + i, worldTime=100 + i, cases=values))
    before = trace[-1]["cases"]
    fixture = dict(schema=1, processToken="seed-process", constructionCallsThisProcess=9,
                   initialEnergy=500, initial=dict(worldTime=100, cases=initial), seedTrace=trace,
                   preHold=copy.deepcopy(before), worldTime=109, cases=copy.deepcopy(before))
    for value in fixture["cases"]:
        value.update(signal=value["held"], holdBlock="minecraft:redstone_block" if value["held"] else "minecraft:air")
    restored = dict(worldTime=200, cases=copy.deepcopy(fixture["cases"]))
    ticks = []
    previous = copy.deepcopy(fixture["cases"])
    for i in range(1, 17):
        rows = []
        for case in definitions():
            if case["held"]:
                old = next(r for r in fixture["cases"] if r["id"] == case["id"])
                value = row(case, 0, True, energy=old["energy"])
                value.update(active=False, state=value["state"].replace("active=true", "active=false"))
            else:
                old = previous[-1]
                powered = old["energy"] > 24
                value = row(case, old["progress"] + (1 if powered else -1), energy=old["energy"] - (24 if powered else 0))
                if value["energy"] <= 24:
                    value.update(active=False, state=value["state"].replace("active=true", "active=false"))
            rows.append(value)
        ticks.append(dict(index=i, tickStartWorldTime=199 + i, worldTime=200 + i, cases=rows))
        previous = rows
    report = dict(schema=1, processToken="restart-process", constructionCallsThisProcess=0,
                  observationTicks=16, restored=restored, ticks=ticks, held=True)
    fixture["readiness"] = readiness("seed", 100)
    report["readiness"] = readiness("restart", 200)
    return fixture, report


def readiness(phase, release_time):
    rows = [dict(id=case["id"], pos=case["pos"], blockTicking=True, tickableListed=True) for case in definitions()]
    pending = copy.deepcopy(rows)
    pending[-1].update(blockTicking=False, tickableListed=False)
    return [dict(phase=phase, worldTime=release_time - 1, cases=pending),
            dict(phase=phase, worldTime=release_time, cases=rows)]


class RedstoneReloadOracleTests(unittest.TestCase):
    def assert_rejected(self, change):
        fixture, report = documents()
        change(fixture, report)
        with self.assertRaises(AssertionError):
            validate_reload(fixture, report)

    def test_all_eight_offsets_and_finite_unheld_control(self):
        fixture, report = documents()
        self.assertEqual(set(range(8)), {r["offset"] for r in fixture["cases"] if r["held"]})
        self.assertEqual("PASS", validate_seed(fixture)["status"])
        self.assertEqual("PASS", validate_reload(fixture, report)["status"])

    def test_first_tick_active_transient_is_not_ignored(self):
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0].update(active=True))

    def test_one_tick_debit_fails_even_if_later_refunded(self):
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0].update(energy=428))

    def test_source_cache_bug_with_consistent_raw_nbt_is_rejected(self):
        def actual_debit(fixture, report):
            seed = fixture["cases"][0]
            report["ticks"][0]["cases"][0] = row(definitions()[0], seed["progress"] + 1,
                                                 True, energy=seed["energy"] - 24)
        self.assert_rejected(actual_debit)

    def test_active_transient_with_consistent_raw_blockstate_is_rejected(self):
        def actual_flag(fixture, report):
            value = report["ticks"][0]["cases"][0]
            value.update(active=True, state=value["state"].replace("active=false", "active=true"))
        self.assert_rejected(actual_flag)

    def test_progress_above_previous_not_only_above_seed_is_rejected(self):
        self.assert_rejected(lambda f, r: r["ticks"][1]["cases"][0].update(progress=1))

    def test_unheld_frozen_false_positive_is_rejected(self):
        def freeze(fixture, report):
            for tick in report["ticks"]:
                tick["cases"][-1] = copy.deepcopy(fixture["cases"][-1])
        self.assert_rejected(freeze)

    def test_missing_duplicate_or_wrong_phase_witness_is_rejected(self):
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"].pop())
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"].append(r["ticks"][0]["cases"][0]))
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0].update(offset=7))

    def test_missing_repeated_or_gapped_tick_is_rejected(self):
        self.assert_rejected(lambda f, r: r["ticks"].pop())
        self.assert_rejected(lambda f, r: r["ticks"][1].update(worldTime=201))
        self.assert_rejected(lambda f, r: r["ticks"][1].update(tickStartWorldTime=202, worldTime=203))

    def test_reconstruction_same_process_and_nonquiescence_are_rejected(self):
        self.assert_rejected(lambda f, r: r.update(constructionCallsThisProcess=1))
        self.assert_rejected(lambda f, r: r.update(processToken=f["processToken"]))
        self.assert_rejected(lambda f, r: r.update(held=False))

    def test_restored_nbt_must_match_entire_seed_not_only_resources(self):
        self.assert_rejected(lambda f, r: r["restored"]["cases"][0].update(nbt=f["cases"][0]["nbt"][:-1] + ',Hidden:1}'))
        self.assert_rejected(lambda f, r: r["restored"]["cases"][0].update(nbt=f["cases"][0]["nbt"].replace('Count:13b', 'Count:12b')))

    def test_real_signal_target_and_stationary_position_are_checked(self):
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0].update(signal=False))
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0]["targets"][0].update(state="Block{minecraft:air}"))
        self.assert_rejected(lambda f, r: r["ticks"][0]["cases"][0].update(pos=[0, 80, 0]))

    def test_seed_needs_natural_mid_target_work_and_original_finite_charge(self):
        fixture, _ = documents()
        for change in (lambda f: f.update(seedTrace=[]), lambda f: f.update(initialEnergy=501),
                       lambda f: f["cases"][0].update(progress=0),
                       lambda f: f["seedTrace"][-1]["cases"][0].update(energy=500)):
            bad = copy.deepcopy(fixture)
            change(bad)
            with self.assertRaises(AssertionError): validate_seed(bad)

    def test_both_native_ticking_and_actual_tickable_registration_are_required(self):
        for field in ("blockTicking", "tickableListed"):
            def change(fixture, report):
                report["readiness"][-1]["cases"][-1][field] = False
            self.assert_rejected(change)
        self.assert_rejected(lambda f, r: r.update(readiness=[]))
        self.assert_rejected(lambda f, r: f.update(readiness=[]))

    def test_readiness_is_bounded_and_not_resampled_until_operating_verdict_passes(self):
        data = dict(readiness=readiness("seed", 100))
        validate_readiness(data, "seed", 100)
        for change in (lambda d: d["readiness"].append(copy.deepcopy(d["readiness"][-1])),
                       lambda d: d["readiness"][0]["cases"][-1].update(blockTicking=True, tickableListed=True),
                       lambda d: d["readiness"][-1].update(worldTime=101),
                       lambda d: d.update(readiness=d["readiness"] * 41)):
            bad = copy.deepcopy(data)
            change(bad)
            with self.assertRaises(AssertionError): validate_readiness(bad, "seed", 100)

    def test_probe_never_drives_ticks_or_loads_expected_nbt(self):
        source = (Path(__file__).parents[1] / "src/main/java/zone/moddev/mc/advantageworks/RedstoneReloadProbe.java").read_text()
        for forbidden in (".tickUpdate(", ".powerUpdate(", ".tick();", ".load("):
            self.assertNotIn(forbidden, source)
        self.assertIn("tickableBlockEntities.clear()", source)
        self.assertIn("isTickingChunk(witness.pos)", source)
        self.assertIn("tickableBlockEntities.contains(tile)", source)
        self.assertIn('else if (ready("seed"))', source)
        self.assertIn('else if (ready("restart"))', source)
        self.assertIn("StandardOpenOption.CREATE_NEW", source)
        mod = (Path(__file__).parents[1] / "src/main/java/zone/moddev/mc/advantageworks/AdvantageWorksMod.java").read_text()
        self.assertEqual(1, mod.count("RedstoneReloadProbe.register();"))


if __name__ == "__main__": unittest.main()
