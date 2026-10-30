"""Offline evidence-fixture controls, not additional Minecraft runs."""

from collections import Counter
from copy import deepcopy
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
import zipfile

import broad_result_audit as audit


LINE = Path(__file__).resolve().parents[3].name.removeprefix("PA")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data), encoding="utf-8")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


class BroadResultAuditTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.runtime = self.root / "broad"
        self.runtime.mkdir()
        source = Path(__file__).resolve().parents[1] / "src/main/resources" / audit.RESOURCE
        definition = source.read_bytes()
        self.stations = json.loads(definition)["stations"]
        self.definitions = {s["id"]: s for s in self.stations}
        jars = []
        for name in ("poweradvantage", "steamadvantage", "electricadvantage", "advantageworkstest", "basemetals", "orespawn"):
            path = self.root / (name + ".jar")
            if name == "advantageworkstest":
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr(audit.RESOURCE, definition.replace(b"\r\n", b"\n"))
            else:
                path.write_bytes(name.encode("ascii"))
            jars.append(dict(id=name, path=str(path), sha256=audit.sha(path.read_bytes()), version="fixture"))
        self.pins = dict(jars=jars, launcher="forge.jar", launcher_sha256=audit.sha(b"engine"),
                         serverCode=dict(files={"forge.jar": dict(sha256=audit.sha(b"engine"), size=6)}))
        self.manifest = self.root / "inputs.json"
        write(self.manifest, {"target" + LINE: self.pins})
        runner = b"# immutable historical broad source fixture\n"
        (self.runtime / "runner-source.py").write_bytes(runner)
        self.report = dict(status="PASS", targetManifest=str(self.manifest),
                           targetManifestSha256=audit.sha(self.manifest.read_bytes()),
                           runnerSha256=audit.sha(runner), stationDefinitionSha256=audit.sha(definition), profiles={})
        for name in audit.PROFILES:
            self.make_profile(name)
        self.save_top()

    def save_top(self):
        write(self.runtime / ("native" + LINE + "-broad-results.json"), self.report)

    def profile(self, name="packaged-current"):
        return self.runtime / name

    def save_profile(self, name="packaged-current"):
        write(self.profile(name) / "broad-results.json", self.report["profiles"][name])
        self.save_top()

    def make_profile(self, name):
        directory = self.profile(name)
        directory.mkdir()
        (directory / "forge.jar").write_bytes(b"engine")
        staged = []
        mods = {}
        for pin in self.pins["jars"]:
            if name == "packaged-decoupled" and pin["id"] == "basemetals":
                continue
            path = directory / "mods" / Path(pin["path"]).name
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(Path(pin["path"]).read_bytes())
            staged.append(dict(file=path.name, sha256=pin["sha256"], source=pin["path"]))
            mods[pin["id"]] = pin["version"]
        write(directory / "input-jars.json", staged)
        minecraft, forge = audit.VERSIONS[LINE]
        mods.update(minecraft=minecraft, forge=forge)
        raw = {}
        for spec in self.stations:
            assertions = [dict(id=a["id"], type=a["type"], status="SKIP" if set(a.get("requiredMods", [])) - set(mods) else "PASS",
                               detail="optional integration is absent" if set(a.get("requiredMods", [])) - set(mods) else "matched")
                          for a in spec["assertions"]]
            raw[spec["id"]] = dict(station=spec["id"], minecraft=minecraft, forge=forge, mods=deepcopy(mods),
                                   assertions=assertions, status="MANUAL_REQUIRED", manualChecks=spec["manualChecks"],
                                   evidenceScope="declared_assertions_only; manual procedures are not automated passes",
                                   worldTime=3, snapshot=[dict(pos=[0, 0, 0], block="minecraft:air", state="air")])
        initial, moved = {}, {}
        for sid in ("S-05", "E-06"):
            start, end = self.drills(self.definitions[sid], False), self.drills(self.definitions[sid], True)
            write(directory / "advantage-works-results" / (sid + "-checkpoint-initial.json"), dict(snapshot=start))
            initial[sid] = dict(snapshot=start)
            raw[sid]["snapshot"] = end
        for phase in ("first", "restarted"):
            write(directory / "advantage-works-results" / (phase + "-all-stations.json"), raw)
        for sid in audit.STATIONS:
            write(directory / "advantage-works-results" / (sid + "-checkpoint-operated.json"),
                  dict(station=sid, minecraft=minecraft, forge=forge, mods=mods, checkpoint="operated", worldTime=1,
                       snapshot=raw[sid]["snapshot"]))
        initial = audit.drill_snapshot(initial, self.definitions, initial=True)
        moved = audit.drill_snapshot(raw, self.definitions)
        counts = dict(Counter(a["status"] for record in raw.values() for a in record["assertions"]))
        checks = dict(status="PASS", assertions=counts, manualRequired=list(audit.STATIONS))
        result = dict(profile=name, status="PASS", expectedMods=mods, beforeRestart=moved, afterRestart=deepcopy(moved), phases={
            "initial": dict(status="PASS", drills=initial), "first": checks, "restarted": deepcopy(checks),
            "operation": dict(status="PASS", drills=12, moved=12),
            "persistence": dict(status="PASS", drills=12, exactEnergy=True)})
        self.report["profiles"][name] = result
        write(directory / "broad-results.json", result)
        log = ("Starting minecraft server version " + minecraft + "\nForge mod loading, version " + forge + ", for MC " + minecraft
               + '\nDone (1.0s)! For help, type "help"\nSaved the game\nStopping server\nSaving players\nSaving worlds\n'
               + "".join("Saving chunks for level 'fixture'/minecraft:" + d + "\n" for d in ("overworld", "the_nether", "the_end"))
               + "".join("ThreadedAnvilChunkStorage (" + label + "): All chunks are saved\n" for label in ("fixture", "DIM-1", "DIM1")))
        for phase in ("first", "restarted"):
            path = directory / ("console-" + phase + ".log")
            commissioning = ("Advantage Works built 21 station(s); skipped 0\n" + "".join(
                "Started " + sid + " - fixture\n" + sid + "-checkpoint-operated.json\nStopped " + sid + " - fixture\n"
                for sid in audit.STATIONS)) if phase == "first" else ""
            rendered = log.replace("Saved the game", commissioning + "Advantage Works check: {MANUAL_REQUIRED=21}\nSaved the game")
            path.write_bytes(rendered.encode("utf-8"))
            if LINE == "113":
                write(path.with_suffix(".diagnostics.json"), dict(status="PASS", logSha256=audit.sha(path.read_bytes()), classifiedDiagnostics=[]))

    def drills(self, spec, moved):
        result = []
        for a in spec["assertions"]:
            block = a.get("block")
            if block not in ("steamadvantage:steam_drill", "electricadvantage:electric_drill"):
                continue
            direction = a.get("meta", audit.DIRECTIONS.index(a.get("expectedText", "down")))
            origin = [x + y for x, y in zip(spec["origin"], a["pos"])]
            rear = [x + y for x, y in zip(origin, audit.REAR[direction])]
            pos = rear if moved else origin
            key = spec["id"] + ":" + a["id"]
            tag = ('{id:"' + block + '",x:' + str(pos[0]) + ',y:' + str(pos[1]) + ',z:' + str(pos[2])
                   + ',ForgeData:{AdvBayWitness:' + json.dumps(key) + '},Energy:[I;0],Items:[{id:"minecraft:diamond",Count:7b,'
                   + 'tag:{AdvBayStock:' + json.dumps(key) + '}}]}')
            result.append(dict(pos=pos, block=block, state="facing=" + audit.DIRECTIONS[direction], nbt=tag))
            if not moved:
                result.append(dict(pos=rear, block="steamadvantage:steam_track" if spec["id"] == "S-05" else "electricadvantage:electric_track", state="track"))
        return result

    def assert_rejected(self, contains=None, requested=audit.PROFILES):
        result = audit.audit(self.runtime, LINE, requested)
        self.assertEqual("FAIL", result["status"], result)
        if contains:
            self.assertIn(contains, " ".join(result["errors"]))

    def mutate_raw(self, mutate, phase="first", name="packaged-current"):
        path = self.profile(name) / "advantage-works-results" / (phase + "-all-stations.json")
        data = load(path)
        mutate(data)
        write(path, data)

    def test_complete_both_profiles_accepts_bounded_proof_not_manual_pass(self):
        result = audit.audit(self.runtime, LINE)
        self.assertEqual("PASS", result["status"], result["errors"])
        self.assertEqual(2, len(result["profiles"]))
        self.assertEqual("NOT_AUTOMATED_BY_BROAD_SWEEP", result["profiles"][audit.PROFILES[0]]["manualProcedures"])
        self.assertIn("NOT_INDEPENDENTLY_RECORDED", result["profiles"][audit.PROFILES[0]]["shutdown"]["first"]["processExit"])

    def test_first_profile_pass_never_implies_both(self):
        self.report["profiles"].pop("packaged-decoupled")
        self.save_top()
        self.assert_rejected("Requested profile set incomplete")

    def test_explicit_single_profile_is_distinct_from_default_both(self):
        self.report["profiles"].pop("packaged-decoupled")
        self.save_top()
        self.assertEqual("PASS", audit.audit(self.runtime, LINE, ("packaged-current",))["status"])

    def test_unrequested_extra_profile_rejected(self):
        self.assert_rejected("Requested profile set incomplete", ("packaged-current",))

    def test_second_profile_failure_cannot_be_masked(self):
        self.report["profiles"]["packaged-decoupled"]["status"] = "FAIL"
        self.save_profile("packaged-decoupled")
        self.assert_rejected("profile did not pass")

    def test_missing_local_second_report_rejected(self):
        (self.profile("packaged-decoupled") / "broad-results.json").unlink()
        self.assert_rejected("broad-results.json")

    def test_top_running_is_not_final_pass(self):
        self.report["status"] = "RUNNING"
        self.save_top()
        self.assert_rejected("did not finish PASS")

    def test_empty_or_duplicate_requested_set_rejected(self):
        self.assert_rejected("Invalid requested", ())
        self.assert_rejected("Invalid requested", ("packaged-current", "packaged-current"))

    def test_missing_station_and_missing_raw_assertion_rejected(self):
        self.mutate_raw(lambda rows: rows.pop("W-04"))
        self.assert_rejected("all 21")

    def test_omitted_assertion_rejected_despite_summary_counts(self):
        self.mutate_raw(lambda rows: rows["W-01"]["assertions"].pop())
        self.assert_rejected("assertion set incomplete")

    def test_duplicate_assertion_rejected(self):
        self.mutate_raw(lambda rows: rows["W-01"]["assertions"].append(deepcopy(rows["W-01"]["assertions"][0])))
        self.assert_rejected("duplicate")

    def test_partial_pass_counts_rejected(self):
        self.report["profiles"]["packaged-current"]["phases"]["first"]["assertions"]["PASS"] -= 1
        self.save_profile()
        self.assert_rejected("summary counts/manual list")

    def test_manual_station_cannot_be_reported_as_pass(self):
        self.mutate_raw(lambda rows: rows["W-01"].update(status="PASS"))
        self.assert_rejected("classification masked")

    def test_manual_procedures_cannot_disappear(self):
        self.mutate_raw(lambda rows: rows["W-01"].update(manualChecks=[]))
        self.assert_rejected("manual checks omitted")

    def test_unexplained_skip_rejected(self):
        self.mutate_raw(lambda rows: rows["W-01"]["assertions"][0].update(status="SKIP"))
        self.assert_rejected("wrong raw result")

    def test_absent_mod_integration_cannot_be_pass(self):
        def change(rows):
            a = next(a for a in rows["S-04"]["assertions"] if a["id"] == "base-metals-steel-plate")
            a["status"] = "PASS"
        self.mutate_raw(change, name="packaged-decoupled")
        self.assert_rejected("wrong raw result")

    def test_failed_assertion_cannot_hide_behind_manual(self):
        self.mutate_raw(lambda rows: rows["W-01"]["assertions"][0].update(status="FAIL"))
        self.assert_rejected("wrong raw result")

    def test_loaded_mod_version_mismatch_rejected(self):
        self.mutate_raw(lambda rows: rows["W-01"]["mods"].update(poweradvantage="wrong"))
        self.assert_rejected("loaded mod set")

    def test_error_and_shutdown_error_cannot_be_masked(self):
        self.report["profiles"]["packaged-current"]["shutdownError"] = "process exit failed"
        self.save_profile()
        self.assert_rejected("failure masked")

    def test_top_level_error_cannot_hide_behind_pass(self):
        self.report["error"] = "second profile crashed"
        self.save_top()
        self.assert_rejected("failure masked")

    def test_recorded_nonzero_process_exit_is_not_ignored(self):
        self.report["profiles"]["packaged-current"]["processExitCode"] = 1
        self.save_profile()
        self.assert_rejected("process exit failed")

    def test_pass_detail_must_not_say_did_not_match(self):
        self.mutate_raw(lambda rows: rows["W-01"]["assertions"][0].update(detail="did not match"))
        self.assert_rejected("detail contradicts result")

    def test_missing_restart_phase_rejected(self):
        self.report["profiles"]["packaged-current"]["phases"].pop("persistence")
        self.save_profile()
        self.assert_rejected("required phases incomplete")

    def test_native_drift_cannot_be_masked_by_summary_pass(self):
        self.mutate_raw(lambda rows: rows["S-05"]["snapshot"][0].update(nbt=rows["S-05"]["snapshot"][0]["nbt"].replace("Count:7b", "Count:6b")), phase="restarted")
        self.assert_rejected("stock lost/duplicated")

    def test_native_drill_summary_is_not_the_measurement(self):
        self.report["profiles"]["packaged-current"]["beforeRestart"]["S-05:down-facing"]["pos"][0] += 1
        self.save_profile()
        self.assert_rejected("differs from native snapshots")

    def test_mod_bytes_and_staging_receipt_both_required(self):
        (self.profile() / "mods/poweradvantage.jar").write_bytes(b"changed")
        self.assert_rejected("mod bytes differ")

    def test_extra_mod_rejected(self):
        (self.profile() / "mods/unpinned.jar").write_bytes(b"extra")
        self.assert_rejected("mod bytes differ")

    def test_staging_receipt_cannot_lie(self):
        path = self.profile() / "input-jars.json"
        data = load(path)
        data.pop()
        write(path, data)
        self.assert_rejected("input receipt differs")

    def test_manifest_and_runner_hashes_required(self):
        self.manifest.write_text(self.manifest.read_text() + "\n")
        self.assert_rejected("manifest changed")

    def test_archived_runner_changed_rejected(self):
        (self.runtime / "runner-source.py").write_bytes(b"changed")
        self.assert_rejected("runner changed")

    def test_staged_engine_bytes_required(self):
        (self.profile() / "forge.jar").write_bytes(b"wrong")
        self.assert_rejected("launcher changed")

    def test_missing_restart_log_rejected(self):
        (self.profile() / "console-restarted.log").unlink()
        self.assert_rejected("console-restarted.log")

    def test_native_check_log_must_correspond_to_all_raw_stations(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes().replace(b"MANUAL_REQUIRED=21", b"MANUAL_REQUIRED=20"))
        self.assert_rejected("native check summary differs")

    def test_native_first_boot_must_run_each_station(self):
        path = self.profile() / "console-first.log"
        path.write_bytes(path.read_bytes().replace(b"Started W-04 -", b"omitted W-04 -"))
        self.assert_rejected("station activity")

    def test_restart_cannot_rebuild_to_hide_state_loss(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes() + b"Advantage Works built 21 station(s)\n")
        self.assert_rejected("activity after cold restart")

    def test_missing_operated_station_checkpoint_rejected(self):
        (self.profile() / "advantage-works-results/W-04-checkpoint-operated.json").unlink()
        self.assert_rejected("W-04-checkpoint-operated.json")

    def test_stale_restart_records_are_not_accepted(self):
        self.mutate_raw(lambda rows: rows["W-01"].update(worldTime=0), phase="restarted")
        self.assert_rejected("not ordered")

    def test_shutdown_without_save_rejected(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes().replace(b"Saved the game", b"not saved").replace(b"All chunks are saved", b"not completed"))
        self.assert_rejected("save and shutdown" if LINE == "113" else "completed chunk-storage shutdown")

    def test_shutdown_player_saves_are_required(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes().replace(b"Saving players", b"not completed"))
        self.assert_rejected("incomplete shutdown saves")

    def test_later_native_stop_can_prove_save_without_explicit_flush_command(self):
        if LINE == "113":
            self.skipTest("Forge 25 protocol explicitly flushes before stopping")
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes().replace(b"Saved the game", b"native stop autosave"))
        result = audit.audit(self.runtime, LINE)
        self.assertEqual("PASS", result["status"], result["errors"])
        self.assertFalse(result["profiles"]["packaged-current"]["shutdown"]["restarted"]["explicitSaveCommand"])

    def test_missing_dimension_shutdown_rejected(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes().replace(b"/minecraft:the_end", b"/missing"))
        self.assert_rejected("saved dimension")

    def test_error_in_complete_log_rejected(self):
        path = self.profile() / "console-restarted.log"
        path.write_bytes(path.read_bytes() + b"[Server thread/ERROR] serious native failure\n")
        self.assert_rejected("native error")

    def test_duplicate_json_keys_rejected(self):
        with self.assertRaisesRegex(AssertionError, "Duplicate JSON"):
            audit.decode(b'{"status":"FAIL","status":"PASS"}')

    def test_cli_default_rejects_partial_and_returns_nonzero(self):
        self.report["profiles"].pop("packaged-decoupled")
        self.save_top()
        output = io.StringIO()
        with redirect_stdout(output):
            code = audit.main(["--runtime", str(self.runtime), "--line", LINE])
        self.assertEqual(1, code)
        self.assertEqual(list(audit.PROFILES), json.loads(output.getvalue())["requestedProfiles"])


if __name__ == "__main__":
    unittest.main()
