import base64
import copy
import gzip
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import nbtlib
import native116_actor_io
import dimensions
from matrix import WORLD
from native116_dimension_io import (ATTRIBUTE_NAMES, VANILLA_ATTRIBUTES, attribute_list, entity_uuid,
                                    observed_context, parse_dimension_nbt, project_entity, validate_record)


ROOT = [1, 2, 3, 4]
CHILD = [5, 6, 7, 8]
NETHER = "minecraft:the_nether"
END = "minecraft:the_end"


def typed(value, key=None):
    if isinstance(value, dict):
        return nbtlib.Compound({name: typed(child, name) for name, child in value.items()})
    if isinstance(value, list):
        if key == "UUID":
            return nbtlib.IntArray(value)
        if not value:
            return nbtlib.List[nbtlib.Compound]()
        values = [typed(child) for child in value]
        return nbtlib.List[type(values[0])](values)
    if isinstance(value, str):
        return nbtlib.String(value)
    if isinstance(value, float):
        return nbtlib.Double(value)
    return nbtlib.Int(value)


def entry(payload, key=NETHER):
    buffer = io.BytesIO()
    nbtlib.File(typed(payload)).write(buffer)
    worlds = {entity_uuid(payload): key}
    for passenger in payload.get("Passengers", []):
        worlds.update(entry(passenger, key)["entityWorlds"])
    return {"uuid": entity_uuid(payload), "witness": "boat", "worldDimensionKey": key,
            "sameWorld": True, "entityWorlds": worlds,
            "nbtBase64": base64.b64encode(gzip.compress(buffer.getvalue())).decode("ascii")}


def record(payload=None, key=NETHER):
    return {"nativeEntityContextVersion": 1, "worldDimensionKey": key,
            "dimension": -1 if key == NETHER else 1,
            "entities": [entry(payload or {"id": "minecraft:boat", "UUID": ROOT}, key)],
            "definition": {"dimension": 999}}


class AttributeProjectionTest(unittest.TestCase):
    def test_exact_eight_vanilla_names_are_bijective_and_preserve_payloads(self):
        self.assertEqual(8, len(VANILLA_ATTRIBUTES))
        self.assertEqual(8, len(set(VANILLA_ATTRIBUTES.values())))
        for native, legacy in VANILLA_ATTRIBUTES.items():
            source = [{"Name": native, "Base": 2.25, "Private": {"Name": native},
                       "Modifiers": [{"UUID": ROOT, "Amount": 0.75, "Operation": 0, "Private": "keep"}]}]
            before = copy.deepcopy(source)
            expected = copy.deepcopy(source)
            expected[0]["Name"] = legacy
            self.assertEqual(expected, attribute_list(source))
            self.assertEqual(before, source)

    def test_only_the_one_approved_forge_identity_is_projected(self):
        self.assertEqual("forge.swimSpeed", attribute_list([{"Name": "forge:swim_speed", "Base": 2.0}])[0]["Name"])
        for name in ("forge:other", "forge:swimSpeed", "example:swim_speed", "generic.max_health"):
            source = [{"Name": name, "Base": 2.0, "Unknown": [1, 2]}]
            self.assertEqual(source, attribute_list(source))

    def test_absent_and_empty_known_modifiers_are_explicit_without_losing_nonempty(self):
        for value in ({"Name": "minecraft:generic.armor", "Base": 0.0},
                      {"Name": "minecraft:generic.armor", "Base": 0.0, "Modifiers": []}):
            self.assertEqual([], attribute_list([value])[0]["Modifiers"])
        modifier = {"UUID": ROOT, "Amount": 3.0, "Operation": 2, "Unrecognized": {"keep": 7}}
        self.assertEqual([modifier], attribute_list([{"Name": "minecraft:generic.armor", "Modifiers": [modifier]}])[0]["Modifiers"])

    def test_rejects_native_legacy_name_collisions(self):
        for native, legacy in ATTRIBUTE_NAMES.items():
            with self.assertRaisesRegex(ValueError, "Ambiguous"):
                attribute_list([{"Name": native, "Base": 2}, {"Name": legacy, "Base": 3}])

    def test_rejects_duplicate_unknown_attributes_instead_of_dict_overwrite(self):
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            attribute_list([{"Name": "example:custom"}, {"Name": "example:custom"}])

    def test_rejects_malformed_attribute_and_modifier_containers(self):
        for value in ({}, [7], [{"Base": 2}], [{"Name": 3}],
                      [{"Name": "minecraft:generic.armor", "Modifiers": {}}],
                      [{"Name": "minecraft:generic.armor", "Modifiers": ["not-compound"]}]):
            with self.assertRaises(ValueError):
                attribute_list(value)

    def test_idempotent_projection_preserves_unknown_attribute_keys(self):
        source = [{"Name": "minecraft:generic.max_health", "Base": 20.0, "Private": {"attributes": [1]}}]
        once = attribute_list(source)
        self.assertEqual(once, attribute_list(once))
        self.assertEqual(source[0]["Private"], once[0]["Private"])


class ActualWorldContextTest(unittest.TestCase):
    def test_actual_registry_world_is_used_not_expected_definition(self):
        payload = {"id": "minecraft:boat", "UUID": ROOT}
        raw = record(payload, END)
        raw["definition"]["dimension"] = -1
        self.assertEqual(1, project_entity(payload, observed_context(raw))["Dimension"])

    def test_actual_passenger_context_is_required_and_projected(self):
        child = {"id": "minecraft:wolf", "UUID": CHILD, "Health": 20.0}
        payload = {"id": "minecraft:boat", "UUID": ROOT, "Passengers": [child]}
        raw = record(payload)
        before = copy.deepcopy(raw)
        actual = project_entity(payload, observed_context(raw))
        self.assertEqual(-1, actual["Dimension"])
        self.assertEqual(-1, actual["Passengers"][0]["Dimension"])
        self.assertEqual(20.0, actual["Passengers"][0]["Health"])
        self.assertEqual(before, raw)

    def test_missing_context_is_never_inferred(self):
        for key in ("nativeEntityContextVersion", "worldDimensionKey"):
            raw = record(); del raw[key]
            with self.assertRaises(ValueError): observed_context(raw)
        raw = record(); del raw["entities"][0]["worldDimensionKey"]
        with self.assertRaises(ValueError): observed_context(raw)

    def test_same_world_requires_a_real_boolean_true(self):
        for value in (False, 1, "true", None):
            raw = record(); raw["entities"][0]["sameWorld"] = value
            with self.assertRaises(AssertionError): observed_context(raw)

    def test_rejects_root_key_and_observed_dimension_mismatch(self):
        raw = record(); raw["dimension"] = 1
        with self.assertRaises(AssertionError): observed_context(raw)

    def test_rejects_existing_nbt_dimension_mismatch(self):
        payload = {"id": "minecraft:boat", "UUID": ROOT, "Dimension": 1}
        with self.assertRaises(AssertionError): project_entity(payload, {entity_uuid(payload): NETHER})
        payload["Dimension"] = NETHER
        self.assertEqual(-1, project_entity(payload, {entity_uuid(payload): NETHER})["Dimension"])

    def test_rejects_missing_extra_and_wrong_passenger_world_observations(self):
        payload = {"id": "minecraft:boat", "UUID": ROOT,
                   "Passengers": [{"id": "minecraft:wolf", "UUID": CHILD}]}
        raw = record(payload)
        missing = copy.deepcopy(raw); del missing["entities"][0]["entityWorlds"][entity_uuid(payload["Passengers"][0])]
        extra = copy.deepcopy(raw); extra["entities"][0]["entityWorlds"]["extra"] = NETHER
        wrong = copy.deepcopy(raw); wrong["entities"][0]["entityWorlds"][entity_uuid(payload["Passengers"][0])] = END
        for bad in (missing, extra, wrong):
            with self.assertRaises(AssertionError): observed_context(bad)

    def test_rejects_duplicate_root_uuid_and_mismatching_uuid_attestation(self):
        raw = record(); raw["entities"].append(copy.deepcopy(raw["entities"][0]))
        with self.assertRaises(AssertionError): observed_context(raw)
        raw = record(); raw["entities"][0]["uuid"] = "wrong"
        with self.assertRaises(AssertionError): observed_context(raw)

    def test_rejects_duplicate_serialized_passenger_uuid(self):
        payload = {"id": "minecraft:boat", "UUID": ROOT,
                   "Passengers": [{"id": "minecraft:wolf", "UUID": ROOT}]}
        with self.assertRaises(AssertionError): observed_context(record(payload))

    def test_rejects_unknown_world_keys_and_missing_entity_observations(self):
        payload = {"id": "minecraft:boat", "UUID": ROOT}
        for contexts in ({}, {entity_uuid(payload): "example:unknown"}):
            with self.assertRaises(ValueError): project_entity(payload, contexts)

    def test_does_not_walk_inventory_private_compounds_or_change_health(self):
        private = {"Attributes": [{"Name": "minecraft:generic.max_health", "Base": 17.0}]}
        payload = {"id": "minecraft:wolf", "UUID": ROOT, "Health": 20.0,
                   "Attributes": [{"Name": "minecraft:generic.max_health", "Base": 20.0}],
                   "PrivateData": private, "Inventory": [private]}
        before = copy.deepcopy(payload)
        actual = project_entity(payload, {entity_uuid(payload): NETHER})
        self.assertEqual(20.0, actual["Health"])
        self.assertEqual(private, actual["PrivateData"])
        self.assertEqual([private], actual["Inventory"])
        self.assertEqual(before, payload)


class CaptureBindingTest(unittest.TestCase):
    def test_real_definition_entity_references_are_not_serialized_nbt(self):
        definition = {"dimension": -1, "entities": [
            {"uuid": "382ee065-9b64-3e5d-ba95-28d25fe76f39", "witness": "boat"},
            {"uuid": "8fcfc8b9-ad2c-3649-8bbc-9985c0044121", "witness": "horse"},
            {"uuid": "56a3698a-8a5b-3f03-9088-7283f466f0e2", "witness": "hydroturbine"},
            {"uuid": "5e0e0ed0-34bb-3d03-8b5a-b262336b07e4", "witness": "leash-knot"},
            {"uuid": "b1a56f21-95c0-3e04-a77d-b160826c9c7f", "witness": "passenger-wolf"}],
            "generator": {"name": "electricadvantage:hydroelectric_generator", "x": 176, "y": 101, "z": 8},
            "geometry": {"minX": 144, "maxX": 176, "y": 100, "z": 8}, "nativeOnLoad": True,
            "playerName": "AdvWorksDimensionPlayer", "playerUuid": "e036830e-303d-3ac3-bd23-062b72eed895",
            "universalBucket": False}
        with patch("native116_dimension_io.parse_nbt", side_effect=AssertionError("Definition is not an NBT snapshot")) as parse:
            self.assertEqual(definition, native116_actor_io.DimensionJson.loads(json.dumps(definition)))
            parse.assert_not_called()

    def test_explicit_native_observations_without_payload_still_fail_closed(self):
        raw = record(); del raw["entities"][0]["nbtBase64"]
        with self.assertRaisesRegex(KeyError, "nbtBase64"):
            native116_actor_io.DimensionJson.loads(json.dumps(raw))
        for marker, value in (("worldDimensionKey", NETHER), ("sameWorld", True), ("entityWorlds", {})):
            explicit = {marker: value, "entities": [{"uuid": "00000000-0000-0001-0000-000000000002", "witness": "boat"}]}
            with self.assertRaisesRegex(ValueError, "versioned"):
                native116_actor_io.DimensionJson.loads(json.dumps(explicit))
        contextual = copy.deepcopy(raw); contextual.pop("nativeEntityContextVersion"); contextual.pop("worldDimensionKey")
        with self.assertRaisesRegex(ValueError, "versioned"):
            native116_actor_io.DimensionJson.loads(json.dumps(contextual))

    def test_legacy_source_capture_keeps_existing_boundary(self):
        payload = {"id": "Wolf", "UUIDMost": 1, "UUIDLeast": 2, "Dimension": -1,
                   "Attributes": [{"Name": "generic.maxHealth", "Base": 20.0}]}
        old = entry(payload); old.pop("worldDimensionKey"); old.pop("sameWorld"); old.pop("entityWorlds")
        self.assertEqual(payload, parse_dimension_nbt(old))
        self.assertIs(native116_actor_io.capture_dimensions.__code__, dimensions.capture.__code__)

    def test_sealed_old_native_data_without_context_cannot_be_relabelled(self):
        raw = record()
        raw.pop("nativeEntityContextVersion"); raw.pop("worldDimensionKey")
        for key in ("worldDimensionKey", "sameWorld", "entityWorlds"):
            raw["entities"][0].pop(key)
        with self.assertRaises(ValueError): validate_record(raw)

    def test_native_capture_uses_contextual_projection_in_historical_function_copy(self):
        payload = {"id": "minecraft:wolf", "UUID": ROOT, "Health": 20.0,
                   "Attributes": [{"Name": "minecraft:generic.max_health", "Base": 20.0}]}

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); world = root / WORLD; (world / "playerdata").mkdir(parents=True)
            nbtlib.File(typed({"Data": {"DataVersion": 2586}})).save(world / "level.dat", gzipped=True)
            player = {"UUID": ROOT, "Dimension": NETHER, "Inventory": [], "EnderItems": []}
            nbtlib.File(typed(player)).save(world / "playerdata" / (entity_uuid(player) + ".dat"), gzipped=True)
            (root / "dimension-fixture.json").write_text(json.dumps({"playerUuid": entity_uuid(player)}))
            raw = record(payload); raw["playerAdapter"] = False; raw["generator"] = {"actualName": "minecraft:stone"}
            (root / "dimensions-native.json").write_text(json.dumps(raw))
            actual = native116_actor_io.capture_dimensions(root, "native")
            self.assertEqual(-1, actual["entities"]["boat"]["Dimension"])
            self.assertEqual(20.0, actual["entities"]["boat"]["Attributes"]["generic.maxHealth"]["Base"])
            self.assertEqual(20.0, actual["entities"]["boat"]["Health"])


if __name__ == "__main__":
    unittest.main()
