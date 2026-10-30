"""Unit fixtures exercise rejection logic only; they are NOT native runtime evidence."""

from copy import deepcopy
import hashlib
import json
from pathlib import Path
import unittest
from uuid import UUID
import native112_harvest as native


def sample(label="exercise"):
    rows, identity = [], 1
    for name, (level, allowed, silk, minimum, maximum, trials) in native.EXPECTED.items():
        evidence = []
        for trial in range(trials):
            drops = []
            if minimum:
                drops.append(dict(entityId=identity, uuid=str(UUID(int=identity)), item=native.drop_identity(name),
                                  count=minimum, damage=0, tagged=False, removed=False, x=1200.5, y=8.5, z=1024.5))
                identity += 1
            evidence.append(dict(trial=trial, worldTime=1, removedBeforeCleanup=trial,
                                 liveBeforeCleanup=int(trial > 0), liveAfterCleanup=0, actualDrops=drops))
        mask = int(name.split("/")[1]) if name.startswith(("wire/", "scaffold/")) else -1
        row = dict(id=name, block=native.block_identity(name), status="PASS", historicalHarvestLevel=level,
                   harvestAllowed=allowed, silkTouch=silk, trials=trials, actualDropCounts=[minimum]*trials,
                   connectionMask=mask, actualFullCube=name.startswith(("sulfur/","lithium/")),
                   actualOpaque=name.startswith(("sulfur/","lithium/")), materialSolid=True,
                   collisionBoxes=native.expected_boxes(name), dropEvidence=evidence)
        if mask >= 0: row["actualConnectionMask"] = mask
        rows.append(row)
    for name, (block, opaque, light_opacity) in native.PROPERTIES.items():
        rows.append(dict(id=name, status="PASS", block=block, actualOpaque=opaque, actualFullCube=opaque,
                         lightOpacity=light_opacity))
    for name in native.WATER_ONLY:
        rows.append(dict(id=name, status="N/A", reason=native.NA_REASON))
    return dict(label=label, minecraft=native.MINECRAFT, status="PASS", executedChecks=272, notApplicableChecks=3,
                checks=rows, fixtureIsolation=native.ISOLATION_PROTOCOL)


def row(document, name="sulfur/wood"):
    return next(entry for entry in document["checks"] if entry["id"] == name)


class LegacyHarvestTests(unittest.TestCase):
    def test_all_270_original_tool_drop_trial_oracles_are_preserved(self):
        digest = hashlib.sha256(json.dumps(native.EXPECTED, sort_keys=True, separators=(",",":")).encode()).hexdigest().upper()
        self.assertEqual("A43F316D748DFCCB4DA9B72AE771851FFBD30661C56E5609A0D04E0AC0050D5B", digest)
        self.assertEqual(270, len(native.EXPECTED))
        self.assertEqual(2, len(native.PROPERTIES))
        self.assertEqual(3, len(native.WATER_ONLY))

    def test_complete_unit_fixture_can_exercise_both_phase_validators(self):
        for label in ("exercise","restored"):
            document = sample(label)
            native.require_checks(document, label)
            native.require_isolation(document)

    def test_missing_duplicate_unknown_and_wrong_engine_rows_fail(self):
        for mutation in ("missing","duplicate","unknown","engine","label","executed","na-count"):
            document = sample()
            if mutation == "missing": document["checks"].pop()
            if mutation == "duplicate": document["checks"].append(deepcopy(document["checks"][0]))
            if mutation == "unknown": document["checks"][0]["id"] = "unknown"
            if mutation == "engine": document["minecraft"] = "1.13.2"
            if mutation == "label": document["label"] = "different"
            if mutation == "executed": document["executedChecks"] = 275
            if mutation == "na-count": document["notApplicableChecks"] = 0
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                native.require_checks(document, "exercise")

    def test_waterlogging_is_never_misreported_as_pass_or_silently_omitted(self):
        for name in native.WATER_ONLY:
            for field, value in (("status","PASS"),("status","SKIP"),("reason","unsupported"),("reason",None)):
                document = sample()
                row(document, name)[field] = value
                with self.subTest(name=name, field=field), self.assertRaises(AssertionError):
                    native.require_checks(document, "exercise")
        document = sample()
        row(document)["status"] = "N/A"
        with self.assertRaises(AssertionError): native.require_checks(document, "exercise")

    def test_tools_levels_quantities_flags_and_actual_masks_remain_strict(self):
        for name, field, value in (
                ("sulfur/wood","actualDropCounts",[5]*8),("sulfur/silk","actualDropCounts",[4]),
                ("lithium/wood-fails","actualDropCounts",[1]),("lithium/stone","historicalHarvestLevel",0),
                ("lithium/gold-fails","harvestAllowed",True),("sulfur/silk","silkTouch",False),
                ("wire/63/hand","connectionMask",0),("wire/63/hand","actualConnectionMask",0),
                ("wire/0/hand","actualFullCube",True),("frame/hand","actualOpaque",True),
                ("scaffold/0/silk","materialSolid",False),("sulfur/wood","trials",True),
                ("sulfur/hand-fails","actualDropCounts",[False])):
            document = sample()
            row(document,name)[field] = deepcopy(value)
            with self.subTest(name=name,field=field), self.assertRaises(AssertionError):
                native.require_checks(document,"exercise")

    def test_legacy_wire_plus_and_all_six_arms_are_not_modern_full_boxes(self):
        self.assertEqual(5, len(native.expected_boxes("wire/0/hand")))
        self.assertEqual(7, len(native.expected_boxes("wire/63/hand")))
        for name in ("wire/0/hand","wire/63/silk","wire/16/hand","scaffold/0/hand","frame/hand"):
            document = sample()
            row(document,name)["collisionBoxes"] = [[0,0,0,1,1,1]] if name.startswith("wire/") else native.expected_boxes("wire/0/hand")
            with self.subTest(name=name), self.assertRaisesRegex(AssertionError,"collision"):
                native.require_checks(document,"exercise")

    def test_missing_duplicate_nonfinite_and_malformed_collision_boxes_fail(self):
        for boxes in ([], [[False,0,0,1,1,1]], [[0,0,0,float("nan"),1,1]], [[0,0,1]], "full"):
            document = sample()
            row(document,"frame/hand")["collisionBoxes"] = boxes
            with self.subTest(boxes=boxes), self.assertRaises(AssertionError):
                native.require_checks(document,"exercise")
        document = sample()
        boxes = row(document,"wire/0/hand")["collisionBoxes"]
        boxes.append(deepcopy(boxes[0]))
        with self.assertRaises(AssertionError): native.require_checks(document,"exercise")

    def test_frame_opacity_and_solar_fullness_are_dedicated_not_implicit_checks(self):
        for name, field, value in (
                ("frame/native-opacity","actualOpaque",True),("frame/native-opacity","actualFullCube",True),
                ("frame/native-opacity","lightOpacity",255),("solar/native-solidity","actualOpaque",False),
                ("solar/native-solidity","actualFullCube",False),("solar/native-solidity","lightOpacity",255)):
            document=sample()
            row(document,name)[field]=value
            with self.subTest(name=name,field=field), self.assertRaisesRegex(AssertionError,"opacity"):
                native.require_checks(document,"exercise")

    def test_count_only_pass_cannot_hide_wrong_identity_metadata_or_position(self):
        for field,value in (("item","unit:wrong"),("damage",1),("tagged",True),("removed",True),
                            ("uuid",""),("uuid","not-a-uuid"),("entityId","1"),("count",True),
                            ("x",0),("y",float("inf")),("z",None)):
            document=sample()
            row(document)["dropEvidence"][0]["actualDrops"][0][field]=value
            with self.subTest(field=field,value=value), self.assertRaises(AssertionError):
                native.require_isolation(document)

    def test_removed_or_repeated_entity_cannot_be_counted_again(self):
        for across_rows in (False,True):
            for field in ("uuid","entityId"):
                document=sample()
                first=row(document)["dropEvidence"][0]["actualDrops"][0]
                later=row(document,"sulfur/stone")["dropEvidence"][0] if across_rows else row(document)["dropEvidence"][1]
                later["actualDrops"][0][field]=first[field]
                with self.subTest(across_rows=across_rows,field=field), self.assertRaisesRegex(AssertionError,"repeated"):
                    native.require_isolation(document)

    def test_missing_protocol_or_per_trial_audits_fail(self):
        for mutation in ("protocol","trial"):
            document=sample()
            if mutation=="protocol": document["fixtureIsolation"]="native113-live-drops-v1"
            else: row(document)["dropEvidence"].pop()
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): native.require_isolation(document)

    def test_cleanup_negative_counts_bad_sequence_and_bool_timestamps_fail(self):
        for field,value in (("liveAfterCleanup",1),("liveAfterCleanup",False),("trial",True),("trial",4),
                            ("worldTime",True),("removedBeforeCleanup",-1),("liveBeforeCleanup",-1)):
            document=sample()
            row(document)["dropEvidence"][0][field]=value
            with self.subTest(field=field,value=value), self.assertRaisesRegex(AssertionError,"not isolated"):
                native.require_isolation(document)

    def test_wrong_tool_cannot_hide_unexpected_live_drop(self):
        document=sample()
        row(document,"lithium/wood-fails")["dropEvidence"][0]["actualDrops"] = [
            dict(entityId=99999,uuid=str(UUID(int=99999)),item="electricadvantage:li_ore",
                 count=1,damage=0,tagged=False,removed=False,x=1200.5,y=8.5,z=1024.5)]
        with self.assertRaises(AssertionError): native.require_isolation(document)

    def test_failed_aggregate_or_actual_case_failure_is_not_relabeled_green(self):
        document=sample(); document["status"]="FAIL"
        row(document,"sulfur/silk").update(status="FAIL",failure="Wrong actual drop: sulfur powder")
        with self.assertRaisesRegex(AssertionError,"sulfur/silk: Wrong actual drop"):
            native.require_checks(document,"exercise")
        document=sample(); document["status"]="FAIL"
        with self.assertRaises(AssertionError): native.require_checks(document,"exercise")

    def test_java_observes_native_survival_and_real_unfiltered_item_identities(self):
        source=(Path(__file__).parents[1]/"src/main/java/zone/moddev/mc/advantageworks/NativeHarvestProbe.java").read_text()
        for literal in ("new PlayerInteractionManager(world)", "setGameType(GameType.SURVIVAL)",
                        "player.interactionManager.tryHarvestBlock(POS)", "block.canHarvestBlock(world, POS, player)",
                        "entity != null && !entity.isDead", 'actual.addProperty("uuid", entity.getUniqueID().toString())',
                        'actual.addProperty("removed", entity.isDead)', "block.addCollisionBoxToList(",
                        "block.getActualState(world.getBlockState(POS), world, POS)", '"frame/native-opacity"',
                        'getRequiredPermissionLevel() { return 4; }', '"AdvantageWorksMigration"',
                        "WorldType.FLAT", '"advantage-migration.allow"'):
            self.assertIn(literal,source)
        self.assertNotIn(".getDrops(",source)
        self.assertNotIn("import com.mcmoddev",source)
        self.assertNotIn("import zone.moddev.mc.electricadvantage",source)
        self.assertNotIn("import zone.moddev.mc.steamadvantage",source)

    def test_runner_enforces_code_custody_before_boot_without_editing_shared_helpers(self):
        source=Path(native.__file__).read_text()
        for literal in ('verify_code(target[0], expected_code)', 'verify_code(output, expected_code)',
                        'write_json(output / INVENTORY, expected_code)', 'verify_staged(output, target)',
                        'report["protocolUnchanged"]', 'report["manifestUnchanged"]'):
            self.assertIn(literal,source)
        self.assertLess(source.index('verify_code(output, expected_code)'), source.index('server = Server('))

    def test_legacy_solar_light_opacity_is_an_explicit_constructor_setting_not_a_full_cube_inference(self):
        root=Path(__file__).resolve().parents[2]
        source=(root/'src/main/java/cyano/poweradvantage/api/GUIBlock.java').read_text()
        constructor=source.split('public GUIBlock(Material m)',1)[1].split('}',1)[0]
        self.assertIn('super(m);',constructor)
        self.assertIn('this.setLightOpacity(0);',constructor)
        self.assertEqual((True,0),native.PROPERTIES['solar/native-solidity'][1:])


if __name__=="__main__": unittest.main()
