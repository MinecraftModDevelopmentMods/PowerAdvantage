"""Headless safety/oracle tests; synthetic NBT never substitutes for leased native proof."""

import base64
import copy
import gzip
import io
import json
import os
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile
import zlib

import nbtlib

import matrix
import native116_machine_items as machine
import native116_observation_plan as observation


def encode(value):
    stream = io.BytesIO()
    nbtlib.File(nbtlib.parse_nbt(machine.snbt(value))).write(stream)
    return base64.b64encode(gzip.compress(stream.getvalue())).decode()


def snapshot(case="wool14", source="original110", modern=False, ticks=0):
    blocks = [dict(id="MI-" + str(index), station="machine-items", kind=name.split(":")[1],
                   name=name, x=index * 4, y=80, z=0) for index, name in enumerate(machine.MACHINES)]
    definition = dict(format=1, sourceProfile=source, case=case, bounds=[0, 80, 0, 12, 80, 0], blocks=blocks)
    document = dict(format=1, held=True, requestedTicks=ticks, advancedTicks=ticks,
                    buildCallsThisProcess=0, snapshot=copy.deepcopy(blocks))
    for row in document["snapshot"]:
        stacks = machine.inventory(case, row["name"], "Chest" if source.endswith("110") else "minecraft:chest")
        if modern:
            stacks = [machine.modern_item(stack) for stack in stacks]
        tile = dict(id=row["name"] if modern else row["name"].replace(":", "."),
                    x=row["x"], y=row["y"], z=row["z"], Items=stacks,
                    ForgeData=dict(AdvConnectedWitness=row["id"]))
        row.update(actualName=row["name"], properties={"facing": "north"}, witness=row["id"],
                   objectIdentity=100 + row["x"], nbtBase64=encode(tile))
    return definition, document


def mutate_items(document, callback):
    row = document["snapshot"][0]
    tile = matrix.plain(matrix.parse_nbt(row))
    callback(tile["Items"])
    row["nbtBase64"] = encode(tile)


class ItemExpectationsTest(unittest.TestCase):
    def test_known_metadata_is_identity_not_tool_damage(self):
        for case, identity, count in (("wool14", "red_wool", 7), ("coal1", "charcoal", 5),
                                      ("dye4", "lapis_lazuli", 9)):
            with self.subTest(case=case):
                self.assertEqual(machine.modern_item(machine.items(case)), dict(id="minecraft:" + identity, Count=count))

    def test_tool_damage_and_both_enchantments_remain_exact(self):
        actual = machine.modern_item(machine.items("enchanted-pick"))
        self.assertNotIn("Damage", actual)
        self.assertEqual(actual["tag"]["Damage"], 17)
        self.assertEqual(actual["tag"]["Enchantments"], [dict(id="minecraft:efficiency", lvl=3),
                                                          dict(id="minecraft:unbreaking", lvl=2)])
        self.assertEqual(actual["tag"]["RepairCost"], 4)
        self.assertEqual(actual["tag"]["AdvMachineItems"], dict(note="unchanged", number=19))

    def test_stored_book_enchantments_do_not_become_regular_enchantments(self):
        value = machine.modern_item(machine.items("enchanted-book"))
        self.assertEqual(set(value["tag"]), {"StoredEnchantments"})
        self.assertEqual(value["tag"]["StoredEnchantments"][0], dict(id="minecraft:efficiency", lvl=3))

    def test_nested_chest_has_all_five_actual_legacy_witnesses(self):
        for identity in ("Chest", "minecraft:chest"):
            old = machine.items("nested-chest", identity)
            fixed = machine.modern_item(old)["tag"]["BlockEntityTag"]
            self.assertEqual(fixed["id"], "minecraft:chest")
            self.assertEqual(fixed["Items"], [dict(Slot=index, **machine.modern_item(machine.items(case)))
                                              for index, case in enumerate(machine.CASES[:-1])])
            self.assertEqual(old["tag"]["BlockEntityTag"]["id"], identity)

    def test_unknown_metadata_and_enchantment_are_not_silently_normalized(self):
        with self.assertRaises(ValueError):
            machine.modern_item(dict(id="minecraft:wool", Count=7, Damage=13))
        old = machine.items("enchanted-pick")
        old["tag"]["ench"][0]["id"] = 99
        with self.assertRaises(KeyError):
            machine.modern_item(old)

    def test_exact_source_and_destination_all_cases_both_versions(self):
        for case in machine.CASES:
            for source in ("original110", "legacy112"):
                for modern in (False, True):
                    with self.subTest(case=case, source=source, modern=modern):
                        definition, document = snapshot(case, source, modern)
                        self.assertEqual(len(machine.document_items(document, definition, modern=modern)), 4)

    def test_archived_source_uses_actual_array_without_fabricating_native_flags(self):
        definition, document = snapshot("nested-chest")
        rows = document["snapshot"]
        for row in rows:
            row.pop("objectIdentity")
            row.pop("witness")
        self.assertEqual(len(machine.source_items(rows, definition)), 4)
        with self.assertRaisesRegex(AssertionError, "actual snapshot array"):
            machine.source_items(document, definition)
        tile = matrix.plain(matrix.parse_nbt(rows[0]))
        tile.pop("ForgeData")
        rows[0]["nbtBase64"] = encode(tile)
        with self.assertRaisesRegex(AssertionError, "NBT source witness"):
            machine.source_items(rows, definition)

    def test_failed_source_command_cannot_create_a_fresh_baseline(self):
        definition, document = snapshot()
        mutate_items(document, lambda stacks: stacks[0].update(id="minecraft:dirt", Count=13, Damage=0))
        with self.assertRaisesRegex(AssertionError, "Exact item mismatch"):
            machine.document_items(document, definition, modern=False)

    def test_unfixed_legacy_metadata_fails_native_oracle(self):
        for case in ("wool14", "coal1", "dye4"):
            definition, document = snapshot(case)
            with self.subTest(case=case), self.assertRaises(AssertionError):
                machine.document_items(document, definition, modern=True)

    def test_count_slot_tags_and_damage_mutations_fail(self):
        for mutate in (lambda stacks: stacks.clear(), lambda stacks: stacks.append(copy.deepcopy(stacks[0])),
                       lambda stacks: stacks[0].update(Count=2), lambda stacks: stacks[0].update(Slot=2),
                       lambda stacks: stacks[0]["tag"].update(Damage=0),
                       lambda stacks: stacks[0]["tag"]["Enchantments"][0].update(lvl=2),
                       lambda stacks: stacks[0]["tag"].pop("AdvMachineItems")):
            definition, document = snapshot("enchanted-pick", modern=True)
            mutate_items(document, mutate)
            with self.subTest(mutate=mutate), self.assertRaises(AssertionError):
                machine.document_items(document, definition, modern=True)

    def test_nested_item_loss_wrong_identity_and_zeroed_tool_fail(self):
        for mutate in (lambda values: values.pop(), lambda values: values[0].update(id="minecraft:wool"),
                       lambda values: values[3]["tag"].update(Damage=0),
                       lambda values: values[4]["tag"]["StoredEnchantments"][0].update(id="minecraft:protection")):
            definition, document = snapshot("nested-chest", modern=True)
            mutate_items(document, lambda stacks: mutate(stacks[0]["tag"]["BlockEntityTag"]["Items"]))
            with self.subTest(mutate=mutate), self.assertRaises(AssertionError):
                machine.document_items(document, definition, modern=True)

    def test_filter_template_slot_is_preserved_not_ignored_as_ghost(self):
        definition, document = snapshot("nested-chest", modern=True)
        row = document["snapshot"][1]
        tile = matrix.plain(matrix.parse_nbt(row))
        self.assertEqual([stack["Slot"] for stack in tile["Items"]], [0, 1])
        tile["Items"].pop()
        row["nbtBase64"] = encode(tile)
        with self.assertRaises(AssertionError):
            machine.document_items(document, definition, modern=True)


class SafetyTest(unittest.TestCase):
    def test_source_commands_use_only_blockdata_for_the_existing_four_tiles(self):
        definition, _ = snapshot("nested-chest")
        commands = machine.seed_commands(definition)
        self.assertEqual(len(commands), 4)
        for command, spec in zip(commands, definition["blocks"]):
            prefix = "blockdata %d %d %d " % (spec["x"], spec["y"], spec["z"])
            self.assertTrue(command.startswith(prefix))
            self.assertEqual(set(nbtlib.parse_nbt(command[len(prefix):])), {"Items", "ForgeData"})
            machine.require_command(command, source_seeds=commands)
            with self.assertRaises(AssertionError):
                machine.require_command(command)
        machine.require_command("advprobe snapshot source", source_seeds=commands)
        with self.assertRaises(AssertionError):
            machine.require_command("advconnected snapshot source", source_seeds=commands)

    def test_destination_mutators_multiline_and_build_are_rejected(self):
        for command in ("advconnected build source", "advprobe build x", "advworks build all",
                        "setblock 0 80 0 minecraft:chest", "blockdata 0 80 0 {Items:[]}",
                        "data merge block 0 80 0 {Items:[]}", "replaceitem block 0 80 0 slot.container.0 stone",
                        "advconnected snapshot x\nstop", "advconnected step 0 x", "advconnected step 4001 x"):
            with self.subTest(command=command), self.assertRaises(AssertionError):
                machine.require_command(command, operated=True)

    def test_destination_operation_locked_until_preservation_passes(self):
        with self.assertRaises(AssertionError):
            machine.require_command("advconnected step 512 operated")
        machine.require_command("advconnected step 512 operated", operated=True)
        for command in ("stop", "save-all flush", "advconnected snapshot source"):
            machine.require_command(command)

    def test_rebuild_unfrozen_wrong_tick_and_missing_witness_fail(self):
        for field, value in (("held", False), ("buildCallsThisProcess", 1), ("advancedTicks", 1),
                             ("requestedTicks", 1)):
            definition, document = snapshot()
            document[field] = value
            with self.subTest(field=field), self.assertRaises(AssertionError):
                machine.document_items(document, definition, modern=False)
        definition, document = snapshot()
        document["snapshot"].pop()
        with self.assertRaises(AssertionError):
            machine.document_items(document, definition, modern=False)

    def test_moved_or_replaced_machine_and_missing_identity_fail(self):
        for field, value in (("x", 999), ("actualName", "minecraft:chest"), ("witness", "wrong"),
                             ("objectIdentity", None)):
            definition, document = snapshot()
            document["snapshot"][0][field] = value
            with self.subTest(field=field), self.assertRaises(AssertionError):
                machine.document_items(document, definition, modern=False)

    def test_exact_512_ticks_and_same_object_required(self):
        definition, before = snapshot(modern=True)
        _, after = snapshot(modern=True, ticks=512)
        machine.document_items(after, definition, modern=True, ticks=512, previous=before)
        after["snapshot"][0]["objectIdentity"] += 1
        with self.assertRaisesRegex(AssertionError, "replaced"):
            machine.document_items(after, definition, modern=True, ticks=512, previous=before)

    def test_missing_or_duplicate_existing_source_positions_fail(self):
        _, old = snapshot()
        with self.assertRaises(AssertionError):
            machine.select_definition(old["snapshot"][:-1], "wool14", "original110")
        old["snapshot"][1].update(x=0)
        with self.assertRaises(AssertionError):
            machine.select_definition(old["snapshot"], "wool14", "original110")

    def test_plan_selects_existing_machines_without_construction_seeds(self):
        _, old = snapshot()
        definition = machine.select_definition(old["snapshot"], "wool14", "original110")
        self.assertEqual(len(definition["blocks"]), 4)
        self.assertFalse(any("seed" in row for row in definition["blocks"]))

    def test_source_and_destination_cli_require_explicit_lease(self):
        for mode in ("source", "destination"):
            with self.subTest(mode=mode), patch("sys.stderr", new=io.StringIO()), self.assertRaises(SystemExit):
                machine.parser().parse_args([mode, "--output", "new", "--java8", "java"])

    def test_only_25574_can_be_used(self):
        for port in (25575, 25576, 25595, 0):
            with self.subTest(port=port), self.assertRaises(ValueError):
                machine.require_lease(port)

    def test_new_output_cannot_overwrite_or_nest_in_protected_world(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            canonical = root / "canonical"
            canonical.mkdir()
            for output in (canonical, canonical / "child", root):
                with self.subTest(output=output), self.assertRaises((ValueError, FileExistsError)):
                    machine.new_root(output, [canonical])
            self.assertEqual(machine.new_root(root / "new", [canonical]), root / "new")

    def test_wrong_manifest_hash_and_old_power_c_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "manifest.json"
            matrix.write_json(path, {"target116": {"jars": [dict(id="poweradvantage", sourceRef="a" * 40,
                                                               sha256=machine.OLD_POWER_C)]}})
            with self.assertRaises(AssertionError):
                machine.checked(path, "0" * 64)
            with self.assertRaises(AssertionError):
                machine.require_power(path, "a" * 40, machine.OLD_POWER_C)
            value = json.loads(path.read_text())
            value["target116"]["jars"][0]["sha256"] = "B" * 64
            matrix.write_json(path, value)
            machine.require_power(path, "a" * 40, "B" * 64)
            with self.assertRaises(AssertionError):
                machine.require_power(path, "c" * 40, "B" * 64)

    def test_failed_source_receipt_does_not_boot_destination(self):
        with patch.object(machine, "source_receipt", side_effect=AssertionError("not closed")), \
             patch.object(machine.native116, "boot") as boot:
            args = SimpleNamespace(source_receipt=Path("fake"), source_receipt_sha256="0" * 64)
            with self.assertRaises(AssertionError):
                machine.run_destination(args)
            boot.assert_not_called()

    def test_boot_boundary_hold_never_launches_another_process(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            machine.hold_gate(root)
            (root / "HOLD").touch()
            with self.assertRaises(InterruptedError):
                machine.hold_gate(root)

    def test_destination_mro_keeps_native_code_custody(self):
        self.assertTrue(issubclass(machine.DestinationServer, machine.NativeServer))
        self.assertTrue(issubclass(machine.DestinationServer, machine.ObservedServer))
        with patch.object(machine.matrix.Server, "__init__"), \
             patch("native116_code.verify_if_sealed") as verifier:
            machine.DestinationServer(Path("new-unit-runtime"), Path("java"), "launcher", "test")
            verifier.assert_called_once_with(Path("new-unit-runtime"))


class RawEvidenceTest(unittest.TestCase):
    def chunk(self, tiles, version=2586):
        root = dict(DataVersion=version, Level=dict(xPos=0, zPos=0, TileEntities=tiles))
        stream = io.BytesIO()
        nbtlib.File(nbtlib.parse_nbt(machine.snbt(root))).write(stream)
        payload = b"\x02" + zlib.compress(stream.getvalue())
        return len(payload).to_bytes(4, "big") + payload

    def test_malformed_saved_chunk_is_rejected(self):
        for value in (b"", b"\0\0\0\x03\x02bad"):
            with self.subTest(value=value), self.assertRaises((ValueError, zlib.error)):
                machine.decode_chunk(value)

    def test_raw_stopped_chunks_independently_match_serializer_and_version(self):
        definition, document = snapshot("nested-chest", modern=True)
        tiles = [matrix.plain(matrix.parse_nbt(row)) for row in document["snapshot"]]
        with tempfile.TemporaryDirectory() as temp, patch.object(machine, "read_region") as region:
            region.return_value = (None, {0: self.chunk(tiles)})
            hashes = machine.stopped_evidence(Path(temp), "converted", definition, document, modern=True)
            self.assertIn("0_0.chunk", hashes)
            self.assertEqual(region.call_count, 1)

    def test_air_drop_nested_damage_reset_wrong_version_and_duplicate_tiles_fail(self):
        definition, document = snapshot("nested-chest", modern=True)
        original = [matrix.plain(matrix.parse_nbt(row)) for row in document["snapshot"]]
        mutations = [lambda tiles: tiles[0]["Items"].clear(),
                     lambda tiles: tiles[0]["Items"][0]["tag"]["BlockEntityTag"]["Items"][3]["tag"].update(Damage=0),
                     lambda tiles: tiles.append(copy.deepcopy(tiles[0]))]
        for mutation in mutations:
            tiles = copy.deepcopy(original)
            mutation(tiles)
            with tempfile.TemporaryDirectory() as temp, patch.object(machine, "read_region", return_value=(None, {0: self.chunk(tiles)})):
                with self.assertRaises(AssertionError):
                    machine.stopped_evidence(Path(temp), "converted", definition, document, modern=True)
        with tempfile.TemporaryDirectory() as temp, patch.object(machine, "read_region", return_value=(None, {0: self.chunk(original, 512)})):
            with self.assertRaisesRegex(AssertionError, "DataVersion"):
                machine.stopped_evidence(Path(temp), "converted", definition, document, modern=True)


class PreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.canonical = self.root / "canonical"
        (self.canonical / matrix.WORLD).mkdir(parents=True)
        level = nbtlib.parse_nbt('{Data:{DataVersion:512},FML:{Registries:{"minecraft:enchantments":{ids:['
            '{K:"minecraft:efficiency",V:32},{K:"minecraft:unbreaking",V:34},'
            '{K:"steamadvantage:high_explosive",V:10},{K:"steamadvantage:powderless",V:11}]}}}}')
        nbtlib.File(level, gzipped=True).save(self.canonical / matrix.WORLD / "level.dat")
        definition, document = snapshot()
        matrix.write_json(self.canonical / "migration-source.json", document["snapshot"])
        matrix.write_json(self.canonical / "migration-fixture.json", definition["blocks"])
        hashes = self.root / "hashes.json"
        matrix.write_json(hashes, matrix.fingerprint(self.canonical / matrix.WORLD))
        self.seed = self.root / "seed"
        (self.seed / "libraries").mkdir(parents=True)
        (self.seed / "libraries/dep.jar").write_bytes(b"unit-test-only-not-java-code")
        (self.seed / "forge-old.jar").write_bytes(b"unit-test-only-not-java-code")
        jar = self.root / "AdvantageMigrationProbe-110-1.0.0.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("zone/moddev/mc/advantageworks/migration/LegacyProbe.class",
                             b"synthetic-unit-only migration-operate.allow field_175730_i nbtBase64")
            archive.writestr("zone/moddev/mc/advantageworks/migration/LegacyActors.class",
                             b"synthetic-unit-only playerAdapter AdvWorksPlayer")
        self.probe = patch.object(machine, "FROZEN_SOURCE_PROBE", matrix.digest(jar))
        self.probe.start()
        self.addCleanup(self.probe.stop)
        manifest = self.root / "profiles.json"
        matrix.write_json(manifest, {"original110": dict(seed=str(self.seed), launcher="forge-old.jar",
                          jars=[dict(path=str(jar), sha256=matrix.digest(jar))])})
        self.route = dict(id="legacy/original110", kind="frozen", sourceKey="original110", source=str(self.canonical),
                          sourceSnapshot="migration-source.json", sourceSnapshotSha256=matrix.digest(self.canonical / "migration-source.json"),
                          sourceHashes=str(hashes), sourceHashesSha256=matrix.digest(hashes),
                          sourceManifest=str(manifest), sourceManifestSha256=matrix.digest(manifest))
        self.routes = self.root / "routes.json"
        matrix.write_json(self.routes, dict(routes=[self.route]))
        self.custody = dict(receiptSha256="0" * 64, files={"unit-only": "0" * 64})
        self.history = patch.object(machine, "history_custody", return_value=self.custody)
        self.history.start()
        self.addCleanup(self.history.stop)
        console_root = self.root / "canonical-consoles"
        console = console_root / "original110/console-source.log"
        console.parent.mkdir(parents=True)
        console.write_text("[01:00:00] [Server thread/INFO]: Done (unit-only)\n")
        self.console_root = patch.object(machine, "SOURCE_CONSOLE_ROOT", console_root)
        self.console_pins = patch.object(machine, "SOURCE_CONSOLE_PINS", {"original110": matrix.digest(console)})
        self.console_root.start()
        self.console_pins.start()
        self.addCleanup(self.console_root.stop)
        self.addCleanup(self.console_pins.stop)

    def prepare_plan(self):
        value = machine.plan(self.routes, matrix.digest(self.routes), ["original110"])
        path = self.root / "plan.json"
        matrix.write_json(path, value)
        return path, value

    def test_world_only_canonical_uses_exact_separate_profile_without_writes(self):
        before = matrix.fingerprint(self.canonical)
        _, value = self.prepare_plan()
        self.assertEqual(value["sourceBoots"], 6)
        self.assertEqual(value["destinationBoots"], 18)
        self.assertEqual(value["sources"][0]["canonicalLayout"], "world-and-snapshots")
        self.assertEqual(matrix.fingerprint(self.canonical), before)
        self.assertFalse((self.canonical / "mods").exists())

    def test_changed_canonical_fingerprint_fails_before_staging(self):
        (self.canonical / matrix.WORLD / "level.dat").write_bytes(b"changed")
        with self.assertRaisesRegex(AssertionError, "approved frozen receipt"):
            machine.plan(self.routes, matrix.digest(self.routes), ["original110"])

    def test_mismatched_existing_mods_directory_is_not_treated_as_world_only(self):
        (self.canonical / "mods").mkdir()
        with self.assertRaisesRegex(AssertionError, "old jar set"):
            machine.plan(self.routes, matrix.digest(self.routes), ["original110"])

    def test_duplicate_source_selection_and_route_ambiguity_fail(self):
        with self.assertRaises(ValueError):
            machine.plan(self.routes, matrix.digest(self.routes), ["original110", "original110"])
        matrix.write_json(self.routes, dict(routes=[self.route, self.route]))
        with self.assertRaisesRegex(AssertionError, "unique approved"):
            machine.plan(self.routes, matrix.digest(self.routes), ["original110"])

    def test_plan_is_invalidated_by_runner_or_historical_custody_change(self):
        path, value = self.prepare_plan()
        value["runnerSha256"] = "0" * 64
        matrix.write_json(path, value)
        with self.assertRaisesRegex(AssertionError, "runner changed"):
            machine.load_plan(path, matrix.digest(path))
        value["runnerSha256"] = matrix.digest(Path(machine.__file__))
        value["historical"] = {}
        matrix.write_json(path, value)
        with self.assertRaisesRegex(AssertionError, "oracles changed"):
            machine.load_plan(path, matrix.digest(path))

    def source_args(self):
        path, _ = self.prepare_plan()
        return SimpleNamespace(plan=path, plan_sha256=matrix.digest(path), output=self.root / "sources",
                               lease_port=25574, java8=Path("never-launched"))

    def fake_server(self, *, failed_seed=False):
        closed = []
        class FakeServer:
            def __init__(inner, runtime, java, launcher, label, source_seeds=()):
                inner.runtime = runtime
                inner.commands = []
                inner.closed = False
                inner.definition = machine.read(runtime / "connected-fixture.json")
                (runtime / "console-source.log").write_text("Starting minecraft server version 1.10.2\nDone (unit-only)\n")
                closed.append(inner)

            def send(inner, command):
                inner.commands.append(command)
                if command.startswith("advprobe snapshot "):
                    label = command.split()[-1]
                    _, document = snapshot(inner.definition["case"])
                    for row in document["snapshot"]:
                        tile = matrix.plain(matrix.parse_nbt(row))
                        tile["Items"] = machine.inventory(inner.definition["case"], row["name"], "Chest",
                                                          inner.definition.get("storedEnchantments"))
                        row["nbtBase64"] = encode(tile)
                    if failed_seed and label == "source":
                        mutate_items(document, lambda stacks: stacks[0].update(id="minecraft:air"))
                    matrix.write_json(inner.runtime / ("migration-" + label + ".json"), document["snapshot"])
                elif command.startswith("advprobe actors-read "):
                    label = command.split()[-1]
                    player = nbtlib.load(inner.runtime / matrix.WORLD / "playerdata" / (str(machine.PLAYER_UUID) + ".dat"))
                    stream = io.BytesIO()
                    player.write(stream)
                    matrix.write_json(inner.runtime / ("actors-" + label + ".json"), dict(playerAdapter=True, frozen=True,
                        player=dict(nbtBase64=base64.b64encode(gzip.compress(stream.getvalue())).decode())))

            def wait(inner, *args):
                pass

            def close(inner):
                inner.commands.append("stop")
                inner.closed = True
                matrix.write_json(inner.runtime / "console-source.commands.json", inner.commands)
        return FakeServer, closed

    def test_source_seeds_new_copies_closes_before_raw_capture_and_never_writes_canonical(self):
        args = self.source_args()
        before = matrix.fingerprint(self.canonical)
        server, instances = self.fake_server()
        def raw(runtime, *unused, **kwargs):
            self.assertTrue(instances[-1].closed)
            self.assertNotEqual(runtime, self.canonical)
            return {"unit-only": "0" * 64}
        with patch.object(machine, "ObservedServer", server), patch.object(machine, "require_lease"), \
             patch.object(machine, "stopped_evidence", side_effect=raw):
            receipt = machine.run_source(args)
        self.assertEqual(receipt["status"], machine.SOURCE_WITNESS)
        self.assertEqual(len(receipt["rows"]), 6)
        self.assertTrue(all(instance.closed for instance in instances))
        self.assertEqual(matrix.fingerprint(self.canonical), before)
        self.assertTrue(all(command.startswith(("advprobe snapshot ", "advprobe actors-read ", "blockdata "))
                            or command in ("save-all flush", "stop") for instance in instances for command in instance.commands))

    def test_failed_live_source_stack_retains_red_receipt_and_stops_at_first_case(self):
        args = self.source_args()
        server, instances = self.fake_server(failed_seed=True)
        with patch.object(machine, "ObservedServer", server), patch.object(machine, "require_lease"), \
             patch.object(machine, "stopped_evidence") as raw:
            with self.assertRaisesRegex(AssertionError, "Exact item mismatch"):
                machine.run_source(args)
            raw.assert_not_called()
        receipt = machine.read(args.output / "source-receipt.json")
        self.assertEqual(receipt["status"], "FAIL")
        self.assertEqual(len(instances), 1)
        self.assertTrue(instances[0].closed)
        self.assertTrue(list(args.output.rglob("migration-source.json")))

    def test_source_preserves_freeze_marker_and_never_enables_connected_operation(self):
        runtime = self.root / "marker-test"
        runtime.mkdir()
        (runtime / "server.properties").write_text("server-port=25584\n")
        definition, _ = snapshot()
        machine.configure(runtime, definition, 25574, destination=False)
        self.assertFalse((runtime / "migration-operate.allow").exists())
        self.assertFalse((runtime / "advantage-connected.allow").exists())
        (runtime / "migration-operate.allow").touch()
        with self.assertRaisesRegex(AssertionError, "frozen marker state"):
            machine.configure(runtime, definition, 25574, destination=False)

    def test_source_admin_lists_prevent_missing_file_exceptions_without_overwriting_data(self):
        runtime = self.root / "source-admin-config"
        runtime.mkdir()
        (runtime / "server.properties").write_text("server-port=25584\n")
        definition, _ = snapshot()
        machine.configure(runtime, definition, 25574, destination=False)
        for filename in ("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json"):
            self.assertEqual(machine.read(runtime / filename), [])
        path = runtime / "ops.json"
        matrix.write_json(path, [dict(name="do-not-overwrite")])
        before = matrix.digest(path)
        with self.assertRaisesRegex(AssertionError, "not empty"):
            machine.configure(runtime, definition, 25574, destination=False)
        self.assertEqual(matrix.digest(path), before)

    def test_changed_runtime_code_fails_before_any_source_boot(self):
        args = self.source_args()
        (self.seed / "libraries/dep.jar").write_bytes(b"changed")
        with patch.object(machine, "require_lease"), patch.object(machine, "ObservedServer") as server:
            with self.assertRaisesRegex(AssertionError, "source/code changed"):
                machine.run_source(args)
            server.assert_not_called()

    def test_installer_retained_in_seed_custody_but_not_staged_or_launched(self):
        (self.seed / "forge-installer.jar").write_bytes(b"unit-only-installer")
        args = self.source_args()
        server, _ = self.fake_server()
        with patch.object(machine, "ObservedServer", server), patch.object(machine, "require_lease"), \
             patch.object(machine, "stopped_evidence", return_value={}):
            result = machine.run_source(args)
        self.assertEqual(result["status"], machine.SOURCE_WITNESS)
        self.assertFalse(list(args.output.rglob("forge-installer.jar")))
        self.assertIn("forge-installer.jar", machine.read(args.plan)["sources"][0]["seedCode"])


class DestinationFlowTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "fresh-stopped-source"
        (self.source / matrix.WORLD).mkdir(parents=True)
        (self.source / matrix.WORLD / "level.dat").write_bytes(b"synthetic-unit-only-not-a-world")
        self.definition, _ = snapshot()
        matrix.write_json(self.source / "migration-fixture.json", self.definition["blocks"])
        self.before = matrix.fingerprint(self.source)
        self.fresh = dict(plan=str(self.root / "plan.json"), rows=[dict(runtime=str(self.source),
            definition=self.definition, sourceKey="original110", case="wool14", runtimeFingerprint=self.before)])
        seed = self.root / "seed"
        (seed / "libraries").mkdir(parents=True)
        (seed / "forge-unit.jar").write_bytes(b"not-a-launcher")
        jar = self.root / "candidate-unit.jar"
        jar.write_bytes(b"not-a-mod")
        self.profile = (seed, "forge-unit.jar", [jar])
        manifest = self.root / "manifest.json"
        matrix.write_json(manifest, {"target116": {"jars": [dict(id="poweradvantage", sourceRef="a" * 40, sha256="B" * 64)]}})
        self.args = SimpleNamespace(source_receipt=self.root / "source-receipt.json", source_receipt_sha256="0" * 64,
            target_manifest=manifest, target_manifest_sha256=matrix.digest(manifest), power_source="a" * 40,
            power_sha256="B" * 64, output=self.root / "destinations", lease_port=25574, java8=Path("never-launched"))
        self.instances = []

    def run_fake(self, *, lose_item=False):
        instances = self.instances
        class FakeDestination:
            def __init__(inner, runtime, java, launcher, label):
                inner.runtime, inner.label = runtime, label
                inner.commands, inner.operated, inner.closed = [], False, False
                inner.latest = None
                instances.append(inner)
                (runtime / ("console-" + label + ".log")).write_text(
                    "Starting minecraft server version 1.16.5\n"
                    "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550\nDone (unit-only)\n")

            def send(inner, command):
                machine.require_command(command, operated=inner.operated)
                inner.commands.append(command)
                if command.startswith("advconnected "):
                    label = command.split()[-1]
                    ticks = 512 if " step " in command else 0
                    _, document = snapshot(modern=True, ticks=ticks)
                    if lose_item:
                        mutate_items(document, lambda stacks: stacks.clear())
                    inner.latest = document
                    matrix.write_json(inner.runtime / ("connected-" + label + ".json"), document)
                    matrix.write_json(inner.runtime / ("migration-" + label + ".json"), document["snapshot"])

            def wait(inner, *unused):
                pass

            def close(inner):
                inner.commands.append("stop")
                inner.closed = True
                matrix.write_json(inner.runtime / ("console-" + inner.label + ".commands.json"), inner.commands)

        def stage(runtime, profile, java, port):
            return matrix.stage(runtime, profile)

        def region(unused):
            self.assertTrue(instances[-1].closed)
            tiles = [matrix.plain(matrix.parse_nbt(row)) for row in instances[-1].latest["snapshot"]]
            return None, {0: RawEvidenceTest().chunk(tiles)}

        with patch.object(machine, "source_receipt", return_value=self.fresh), \
             patch.object(machine.native116, "pinned_profile", return_value=self.profile), \
             patch.object(machine.native116, "boot", side_effect=stage), \
             patch.object(machine, "DestinationServer", FakeDestination), \
             patch.object(machine, "require_lease"), patch.object(machine, "read_region", side_effect=region):
            result = machine.run_destination(self.args)
            audited = machine.audit(self.args.output)
        return result, audited

    def test_three_boots_use_only_snapshot_step_save_stop_and_replay_raw_evidence(self):
        result, audited = self.run_fake()
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(audited["boots"], 3)
        self.assertEqual(matrix.fingerprint(self.source), self.before)
        self.assertEqual(len(self.instances), 3)
        self.assertEqual(self.instances[0].commands, ["advconnected snapshot converted", "save-all flush", "stop"])
        self.assertEqual(self.instances[1].commands, ["advconnected snapshot restarted", "advconnected step 512 operated",
                                                     "save-all flush", "stop"])
        self.assertTrue(all(server.closed for server in self.instances))

    def test_first_conversion_mismatch_closes_and_preserves_fail_without_restart(self):
        with self.assertRaisesRegex(AssertionError, "Exact item mismatch"):
            self.run_fake(lose_item=True)
        self.assertEqual(len(self.instances), 1)
        self.assertTrue(self.instances[0].closed)
        self.assertEqual(machine.read(self.args.output / "results.json")["status"], "FAIL")
        self.assertTrue(list(self.args.output.rglob("connected-converted.json")))
        self.assertEqual(matrix.fingerprint(self.source), self.before)

    def test_audit_rejects_unrecorded_destination_mutation_command(self):
        self.run_fake()
        path = self.args.output / "original110/wool14/console-restarted.commands.json"
        commands = machine.read(path)
        commands.insert(0, "blockdata 0 80 0 {Items:[]}")
        matrix.write_json(path, commands)
        with patch.object(machine, "source_receipt", return_value=self.fresh), \
             patch.object(machine.native116, "pinned_profile", return_value=self.profile):
            with self.assertRaisesRegex(AssertionError, "command custody"):
                machine.audit(self.args.output)


class StoredBookPlayerTest(unittest.TestCase):
    def definition(self):
        definition, _ = snapshot("enchanted-book")
        definition.update(storedEnchantments=[dict(id=32, lvl=3), dict(id=10, lvl=1), dict(id=11, lvl=1)],
            enchantmentNames={"32": "minecraft:efficiency", "34": "minecraft:unbreaking",
                              "10": "steamadvantage:high_explosive", "11": "steamadvantage:powderless"}, playerBook=True)
        return definition

    def test_new_player_file_exists_before_source_read_and_has_numeric_vanilla_and_steam_book(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            definition = self.definition()
            checksum = machine.seed_player(root, definition)
            path = root / matrix.WORLD / "playerdata" / (str(machine.PLAYER_UUID) + ".dat")
            self.assertEqual(checksum, matrix.digest(path))
            data = matrix.plain(nbtlib.load(path).unpack())
            expected = machine.player_items(data, definition, modern=False)
            self.assertEqual(expected["tag"]["StoredEnchantments"], definition["storedEnchantments"])
            self.assertEqual(len(data["Inventory"]), 1)
            self.assertEqual(data["DataVersion"], 512)

    def test_existing_player_file_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            machine.seed_player(root, self.definition())
            before = matrix.fingerprint(root)
            with self.assertRaises(FileExistsError):
                machine.seed_player(root, self.definition())
            self.assertEqual(matrix.fingerprint(root), before)

    def test_exact_named_steam_book_passes_and_protection_corruption_fails(self):
        definition = self.definition()
        book = machine.modern_item(dict(Slot=0, **machine.items("enchanted-book",
            stored_enchantments=definition["storedEnchantments"])), machine.names(definition))
        words = [(machine.PLAYER_UUID.int >> shift) & 0xffffffff for shift in (96, 64, 32, 0)]
        data = dict(Inventory=[book], EnderItems=[], UUID=words)
        machine.player_items(data, definition, modern=True)
        self.assertEqual(book["tag"]["StoredEnchantments"][1]["id"], "steamadvantage:high_explosive")
        for index in range(3):
            changed = copy.deepcopy(data)
            changed["Inventory"][0]["tag"]["StoredEnchantments"][index]["id"] = "minecraft:protection"
            with self.subTest(index=index), self.assertRaisesRegex(AssertionError, "StoredEnchantments"):
                machine.player_items(changed, definition, modern=True)

    def test_numeric_ids_are_resolved_from_each_saved_registry_not_shared_assumptions(self):
        with tempfile.TemporaryDirectory() as temp:
            for number in (10, 14):
                level = nbtlib.parse_nbt('{FML:{Registries:{"minecraft:enchantments":{ids:['
                    '{K:"minecraft:efficiency",V:32},{K:"minecraft:unbreaking",V:34},'
                    '{K:"steamadvantage:recoil",V:%d}]}}}}' % number)
                path = Path(temp) / (str(number) + ".dat")
                nbtlib.File(level, gzipped=True).save(path)
                registry = machine.enchantment_registry(path)
                self.assertEqual(registry["stored"], [dict(id=32, lvl=3), dict(id=number, lvl=1)])
                self.assertEqual(registry["names"][str(number)], "steamadvantage:recoil")

    def test_wrong_player_uuid_count_slot_or_lost_steam_enchantment_fails(self):
        definition = self.definition()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            machine.seed_player(root, definition)
            data = matrix.plain(nbtlib.load(root / matrix.WORLD / "playerdata" / (str(machine.PLAYER_UUID) + ".dat")).unpack())
        for mutate in (lambda value: value.update(UUIDMost=0), lambda value: value["Inventory"][0].update(Count=2),
                       lambda value: value["Inventory"][0].update(Slot=1),
                       lambda value: value["Inventory"][0]["tag"]["StoredEnchantments"].pop()):
            changed = copy.deepcopy(data)
            mutate(changed)
            with self.subTest(mutate=mutate), self.assertRaises(AssertionError):
                machine.player_items(changed, definition, modern=False)

    def test_only_existing_player_read_commands_are_exposed(self):
        machine.require_command("advactors read converted-book")
        machine.require_command("advprobe actors-read source-book", source_seeds=["blockdata unit"])
        for command in ("advactors build book", "advprobe actors-build book", "advactors operate book",
                        "advprobe actors-read book\nstop"):
            with self.subTest(command=command), self.assertRaises(AssertionError):
                machine.require_command(command)


class SourceDiagnosticTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.authority_root = self.root / "retained"
        self.raw_historical = ["[01:02:03] " + line for line in machine.KNOWN_SOURCE_LINES]
        self.pins = {}
        for profile, lines in (("original110", self.raw_historical), ("legacy112", [])):
            path = self.authority_root / profile / "console-source.log"
            path.parent.mkdir(parents=True)
            path.write_text("\n".join(lines + ["[01:02:05] [Server thread/INFO]: Done (retained-unit-only)"]) + "\n")
            self.pins[profile] = matrix.digest(path)
        self.root_patch = patch.object(machine, "SOURCE_CONSOLE_ROOT", self.authority_root)
        self.pins_patch = patch.object(machine, "SOURCE_CONSOLE_PINS", self.pins)
        self.root_patch.start()
        self.pins_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.addCleanup(self.pins_patch.stop)
        self.actual = self.root / "new-runtime.log"

    def log(self, lines):
        self.actual.write_text("\n".join(lines + ["[12:34:59] [Server thread/INFO]: Done (new-unit-only)"]) + "\n")
        return self.actual

    def report(self, lines, profile="original110"):
        return machine.source_log_report(self.log(lines), profile, machine.canonical_source_console(profile))

    def test_exact_known_lines_record_raw_evidence_count_and_pinned_historical_sha(self):
        actual = ["[12:34:56] " + line for line in machine.KNOWN_SOURCE_LINES]
        report = self.report(actual)
        self.assertEqual(report["status"], machine.SOURCE_WITH_DIAGNOSTICS)
        self.assertNotEqual(report["status"], "PASS")
        self.assertEqual(report["actualDiagnosticCount"], 2)
        self.assertEqual(report["canonicalDiagnosticCount"], 2)
        self.assertEqual(report["canonicalSourceLogSha256"], self.pins["original110"])
        self.assertEqual(report["actualLogSha256"], matrix.digest(self.actual))
        for entry in report["diagnostics"]:
            self.assertEqual(entry["status"], "KNOWN_SOURCE_DIAGNOSTIC")
            self.assertEqual(entry["actualCount"], 1)
            self.assertEqual(entry["canonicalCount"], 1)
            self.assertEqual(entry["actualRawLines"], [line for line in actual if line.endswith(entry["identity"])])
            self.assertEqual(entry["canonicalRawLines"], [line for line in self.raw_historical if line.endswith(entry["identity"])])

    def test_clean_source_has_explicit_source_witness_status_not_blanket_pass(self):
        report = self.report([])
        self.assertEqual(report["status"], machine.SOURCE_WITNESS)
        self.assertEqual(report["diagnostics"], [])

    def test_profile_without_identical_historical_lines_cannot_use_another_profiles_allowance(self):
        with self.assertRaisesRegex(AssertionError, "Unapproved source"):
            self.report(self.raw_historical, "legacy112")
        authority = machine.canonical_source_console("original110")
        with self.assertRaisesRegex(AssertionError, "authority/profile"):
            machine.source_log_report(self.log(self.raw_historical), "legacy112", authority)

    def test_near_misses_different_mod_threads_loggers_case_and_whitespace_fail(self):
        signature, bucket = self.raw_historical
        variants = [signature + " ", signature + ".", signature.replace("not a good thing", "not good"),
            signature.replace("[main/ERROR]", "[Server thread/ERROR]"), signature.replace("[FML]", "[Other]"),
            signature.replace("FML appears", "fml appears"), bucket.replace("Base Metals", "Steam Advantage"),
            bucket.replace("Base Metals", "Base Metals Extra"), bucket.replace("too late.", "too late!"),
            bucket.replace("[Server thread/ERROR]", "[main/ERROR]")]
        for line in variants:
            with self.subTest(line=line), self.assertRaisesRegex(AssertionError, "Unapproved source"):
                self.report([line])

    def test_additional_error_exception_nosuch_and_fatal_lines_always_fail(self):
        for line in ("[12:34:56] [Server thread/ERROR] [FML]: another failure",
                     "java.lang.IllegalStateException: failed", "Exception in thread main",
                     "java.lang.NoSuchMethodError: missing", "NoSuchFieldError: missing",
                     "NoSuchThingException: missing", "java.lang.NoClassDefFoundError: absent",
                     "java.io.FileNotFoundException: ops.json (The system cannot find the file specified)",
                     "[12:34:56] [main/FATAL] [FML]: failed"):
            with self.subTest(line=line), self.assertRaisesRegex(AssertionError, "Unapproved source"):
                self.report(self.raw_historical + [line])

    def test_repeated_known_line_beyond_historical_count_is_additional_error(self):
        with self.assertRaisesRegex(AssertionError, "excessCounts"):
            self.report(self.raw_historical + [self.raw_historical[0]])

    def test_changed_or_unpinned_canonical_log_fails_closed(self):
        authority = machine.canonical_source_console("original110")
        Path(authority["path"]).write_text("changed")
        with self.assertRaisesRegex(AssertionError, "Pinned file changed"):
            machine.source_log_report(self.log(self.raw_historical), "original110", authority)
        with self.assertRaises(ValueError):
            machine.canonical_source_console("unapproved-profile")

    def test_all_target_errors_including_both_known_source_lines_still_fail(self):
        boot = ("Starting minecraft server version 1.16.5\n"
                "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550\n")
        errors = self.raw_historical + ["[12:34:56] " + line for line in machine.LEGACY112_INTEGRATION_LINES] + ["[12:34:56] [main/ERROR] [FML]: unexpected",
            "[12:34:56] [main/ERROR] [FML]: java.lang.IllegalStateException: failed",
            "java.lang.NoSuchFieldError: absent", "java.lang.NoSuchMethodError: absent"]
        with patch.object(machine, "source_log_report") as source_check:
            for line in errors:
                self.actual.write_text(boot + line + "\n")
                with self.subTest(line=line), self.assertRaises(AssertionError):
                    machine.clean_log(self.actual, modern=True)
            source_check.assert_not_called()

    def legacy112_notices(self):
        lines = ["[01:02:03] " + line for line in machine.LEGACY112_INTEGRATION_LINES]
        path = self.authority_root / "legacy112/console-source.log"
        path.write_text("\n".join(lines + ["[01:02:04] [Server thread/INFO]: Done (retained-unit-only)"]) + "\n")
        self.pins["legacy112"] = matrix.digest(path)
        return lines

    def test_only_exact_pinned_legacy112_integration_notices_are_witnessed(self):
        lines = self.legacy112_notices()
        report = self.report(lines, "legacy112")
        self.assertEqual(report["status"], machine.SOURCE_WITH_DIAGNOSTICS)
        self.assertEqual(report["actualDiagnosticCount"], 2)
        self.assertEqual(report["canonicalSourceLogSha256"], self.pins["legacy112"])
        self.assertTrue(all(entry["actualCount"] == entry["canonicalCount"] == 1 for entry in report["diagnostics"]))
        with self.assertRaises(AssertionError):
            self.report(lines, "original110")

    def test_legacy112_near_miss_different_mod_logger_and_extra_count_fail(self):
        lines = self.legacy112_notices()
        for mutated in (lines[0] + " ", lines[0].replace("orespawn]:", "othermod]:"),
                        lines[0].replace("For orespawn", "For electricadvantage"),
                        lines[0].replace("Loading Integration", "Loading integration"),
                        lines[0].replace("/FATAL]", "/ERROR]")):
            with self.subTest(mutated=mutated), self.assertRaises(AssertionError):
                self.report([mutated, lines[1]], "legacy112")
        with self.assertRaises(AssertionError):
            self.report(lines + [lines[0]], "legacy112")
        with self.assertRaises(AssertionError):
            self.report(lines + self.raw_historical, "legacy112")

    def test_saved_registry_sha_is_exact_and_order_independent(self):
        first = dict(names={"32": "minecraft:efficiency", "10": "steamadvantage:recoil"},
                     stored=[dict(id=32, lvl=3), dict(id=10, lvl=1)])
        reordered = dict(stored=first["stored"], names={"10": "steamadvantage:recoil", "32": "minecraft:efficiency"})
        self.assertEqual(machine.registry_sha256(first), machine.registry_sha256(reordered))
        changed = copy.deepcopy(first)
        changed["stored"][1]["id"] = 14
        self.assertNotEqual(machine.registry_sha256(first), machine.registry_sha256(changed))


class ObservationInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.seed = self.root / "original-seed"
        self.original = self.seed / observation.COREMODS
        self.original.parent.mkdir(parents=True)
        self.engine = "net/minecraftforge/coremod/CoreModEngine.class"
        self.added = {"qualification/order/TransformerOrderBridge.class": b"synthetic bridge",
                      "qualification/order/TransformerOrderBridge$Observed.class": b"synthetic observer"}
        self.jar(self.original, {self.engine: b"synthetic original", "META-INF/MANIFEST.MF": b"unchanged"})
        self.observer = self.root / "observer.jar"
        self.jar(self.observer, {self.engine: b"synthetic instrumented", "META-INF/MANIFEST.MF": b"unchanged", **self.added})
        dependency = self.seed / "libraries/dependency.jar"
        dependency.write_bytes(b"synthetic dependency")
        (self.seed / "libraries/mappings.txt").write_text("synthetic pinned non-jar code input")
        (self.seed / "forge-unit.jar").write_bytes(b"synthetic launcher; never executed")
        matrix.write_json(self.seed / observation.INVENTORY, dict(files=observation.code_files(self.seed)))
        self.source = self.root / "UnitBridge.java"
        self.source.write_text("synthetic unit source; never compiled")
        self.proof = self.root / "probe-build.json"
        proof = observation.validate_patch(self.original, self.observer)
        proof.update(sourceSha256=matrix.digest(self.source), dependencies={str(dependency): matrix.digest(dependency)})
        matrix.write_json(self.proof, proof)

    def jar(self, path, entries):
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

    def derive(self):
        return observation.make_plan(self.seed, matrix.digest(self.seed / observation.INVENTORY), self.observer,
            matrix.digest(self.observer), self.proof, matrix.digest(self.proof), self.source)

    def plan(self):
        path = self.root / "observation-plan.json"
        matrix.write_json(path, self.derive())
        return path, matrix.digest(path)

    def staged_unsealed(self, runtime):
        shutil.copytree(self.seed, runtime)
        shutil.copy2(self.observer, runtime / observation.COREMODS)

    def test_derivative_changes_only_coremods_and_preserves_all_other_entries(self):
        before = matrix.fingerprint(self.seed)
        plan = self.derive()
        self.assertEqual(plan["changedPaths"], [observation.COREMODS])
        self.assertEqual(plan["codeFileCount"], 4)
        for name, metadata in plan["baselineInventory"]["files"].items():
            if name != observation.COREMODS:
                self.assertEqual(plan["derivedInventory"]["files"][name], metadata)
        self.assertEqual(matrix.fingerprint(self.seed), before)

    def test_worldless_stage_proves_original_gate_red_then_unchanged_native_gate_green(self):
        before = matrix.fingerprint(self.seed)
        path, checksum = self.plan()
        runtime = self.root / "observation-code-only"
        proof = observation.stage_probe(runtime, path, checksum)
        self.assertEqual(proof["baselineGateBeforeReseal"], "REJECTED_AS_EXPECTED")
        self.assertEqual(proof["status"], "PASS_CODE_CUSTODY_ONLY_NO_MC")
        self.assertEqual(matrix.digest(runtime / observation.CUSTODY / "baseline-inventory.json"), matrix.digest(self.seed / observation.INVENTORY))
        observation.verify_code(runtime)
        observation.audit_runtime(runtime, path, checksum)
        self.assertEqual(matrix.fingerprint(self.seed), before)
        self.assertFalse((runtime / matrix.WORLD).exists())

    def test_seal_rejects_unknown_extra_or_changed_other_code_before_writing_any_receipt(self):
        path, checksum = self.plan()
        for index, mutate in enumerate((lambda root: (root / "unexpected.jar").write_bytes(b"unexpected"),
                                       lambda root: (root / "libraries/dependency.jar").write_bytes(b"changed"),
                                       lambda root: (root / "libraries/mappings.txt").write_text("changed"))):
            runtime = self.root / ("invalid-" + str(index))
            self.staged_unsealed(runtime)
            mutate(runtime)
            before = matrix.digest(runtime / observation.INVENTORY)
            with self.subTest(index=index), self.assertRaisesRegex(AssertionError, "not exactly baseline"):
                observation.seal_runtime(runtime, path, checksum)
            self.assertEqual(matrix.digest(runtime / observation.INVENTORY), before)
            self.assertFalse((runtime / observation.CUSTODY).exists())

    def test_shared_seed_and_aliased_baseline_receipt_cannot_be_written(self):
        path, checksum = self.plan()
        with self.assertRaises(ValueError):
            observation.seal_runtime(self.seed, path, checksum)
        runtime = self.root / "hardlink-negative"
        self.staged_unsealed(runtime)
        (runtime / observation.INVENTORY).unlink()
        os.link(self.seed / observation.INVENTORY, runtime / observation.INVENTORY)
        before = matrix.digest(self.seed / observation.INVENTORY)
        with self.assertRaisesRegex(ValueError, "aliases"):
            observation.seal_runtime(runtime, path, checksum)
        self.assertEqual(matrix.digest(self.seed / observation.INVENTORY), before)

    def test_tampering_after_seal_is_rejected_not_resealed(self):
        path, checksum = self.plan()
        runtime = self.root / "later-tamper"
        observation.stage_probe(runtime, path, checksum)
        (runtime / "libraries/dependency.jar").write_bytes(b"tampered after seal")
        with self.assertRaises(AssertionError):
            observation.audit_runtime(runtime, path, checksum)
        with self.assertRaises(AssertionError):
            observation.seal_runtime(runtime, path, checksum)

    def test_replaced_observer_or_bridge_build_receipt_does_not_match_pins(self):
        path, checksum = self.plan()
        self.observer.write_bytes(b"untrusted replacement")
        with self.assertRaisesRegex(AssertionError, "Changed pinned"):
            observation.load_plan(path, checksum)

    def test_extra_zip_changes_and_duplicate_entries_fail_patch_validation(self):
        self.jar(self.observer, {self.engine: b"synthetic instrumented", "META-INF/MANIFEST.MF": b"changed", **self.added})
        with self.assertRaises(AssertionError):
            self.derive()
        self.jar(self.observer, {self.engine: b"synthetic instrumented", "META-INF/MANIFEST.MF": b"unchanged", **self.added})
        with zipfile.ZipFile(self.observer, "a") as archive, patch("warnings.warn"):
            archive.writestr(self.engine, b"duplicate engine")
        with self.assertRaisesRegex(ValueError, "Duplicate ZIP"):
            self.derive()

    def test_forged_inventory_metadata_or_non_coremods_changes_fail_plan_replay(self):
        path, _ = self.plan()
        plan = observation.read(path)
        plan["derivedInventory"]["files"]["libraries/dependency.jar"]["sha256"] = "0" * 64
        matrix.write_json(path, plan)
        with self.assertRaisesRegex(AssertionError, "pinned derivation"):
            observation.load_plan(path, matrix.digest(path))

    def test_changed_original_seed_or_dependency_provenance_fails_closed(self):
        path, checksum = self.plan()
        (self.seed / "libraries/dependency.jar").write_bytes(b"changed original")
        with self.assertRaises(AssertionError):
            observation.load_plan(path, checksum)

    def test_path_traversal_case_aliases_and_invalid_inventory_metadata_are_rejected(self):
        baseline = self.derive()["baselineInventory"]
        for name in ("libraries/../escape.jar", "C:/escape.jar", "./escape.jar", "metadata.txt"):
            changed = copy.deepcopy(baseline)
            changed["files"][name] = dict(sha256="A" * 64, size=1)
            with self.subTest(name=name), self.assertRaises(ValueError):
                observation.validate_inventory(changed)
        changed = copy.deepcopy(baseline)
        changed["files"][observation.COREMODS.upper()] = changed["files"][observation.COREMODS]
        with self.assertRaises(ValueError):
            observation.validate_inventory(changed)

    def test_worldless_output_cannot_overwrite_original_or_existing_evidence(self):
        path, checksum = self.plan()
        with self.assertRaises(ValueError):
            observation.stage_probe(self.seed / "child", path, checksum)
        runtime = self.root / "already-present"
        runtime.mkdir()
        with self.assertRaises(FileExistsError):
            observation.stage_probe(runtime, path, checksum)


if __name__ == "__main__":
    unittest.main()
