"""Observed native entity context and finite attribute-name representation only."""

import copy
import uuid

from matrix import plain, parse_nbt
from native116_format import DIMENSIONS, actor_nbt, dimension_id, uuid_longs


VANILLA_ATTRIBUTES = {
    "minecraft:generic.max_health": "generic.maxHealth",
    "minecraft:generic.knockback_resistance": "generic.knockbackResistance",
    "minecraft:generic.movement_speed": "generic.movementSpeed",
    "minecraft:generic.armor": "generic.armor",
    "minecraft:generic.armor_toughness": "generic.armorToughness",
    "minecraft:generic.follow_range": "generic.followRange",
    "minecraft:generic.attack_damage": "generic.attackDamage",
    "minecraft:horse.jump_strength": "horse.jumpStrength",
}
# This is the one separately approved Forge identity, not a namespace rewrite.
FORGE_ATTRIBUTES = {"forge:swim_speed": "forge.swimSpeed"}
ATTRIBUTE_NAMES = dict(VANILLA_ATTRIBUTES, **FORGE_ATTRIBUTES)
KNOWN_ATTRIBUTES = frozenset(ATTRIBUTE_NAMES.values())


def native_key(value):
    if not isinstance(value, str) or value not in DIMENSIONS.values():
        raise ValueError("Unsupported observed native dimension key: " + repr(value))
    return value


def entity_uuid(value):
    if "UUID" in value:
        if "UUIDMost" in value or "UUIDLeast" in value:
            raise ValueError("Duplicate native/legacy entity UUID encodings")
        most, least = uuid_longs(value["UUID"])
    else:
        most, least = value.get("UUIDMost"), value.get("UUIDLeast")
        if any(isinstance(word, bool) or not isinstance(word, int) or not -(1 << 63) <= word < (1 << 63)
               for word in (most, least)):
            raise ValueError("Missing or invalid observed entity UUID")
    return str(uuid.UUID(int=((most & ((1 << 64) - 1)) << 64) | (least & ((1 << 64) - 1))))


def entity_tree(value):
    if not isinstance(value, dict):
        raise ValueError("Non-compound serialized entity")
    yield value
    if "Passengers" in value:
        if not isinstance(value["Passengers"], list):
            raise ValueError("Non-list serialized passengers")
        for passenger in value["Passengers"]:
            yield from entity_tree(passenger)


def observed_context(record):
    if type(record.get("nativeEntityContextVersion")) is not int or record["nativeEntityContextVersion"] != 1:
        raise ValueError("Missing versioned per-entity native world observation")
    world = native_key(record.get("worldDimensionKey"))
    if dimension_id(record.get("dimension")) != dimension_id(world):
        raise AssertionError("Snapshot world key and observed dimension disagree")
    if not isinstance(record.get("entities"), list):
        raise ValueError("Missing native entity observations")
    contexts, roots = {}, set()
    for entry in record["entities"]:
        identity = entity_uuid(plain(parse_nbt(entry)))
        if identity in roots:
            raise AssertionError("Native observation has duplicate/mismatched root UUID")
        roots.add(identity)
        for key, observed in entry_context(entry, world).items():
            if key in contexts and contexts[key] != observed:
                raise AssertionError("Conflicting native world observations")
            contexts[key] = observed
    return contexts


def entry_context(entry, world):
    if entry.get("sameWorld") is not True or native_key(entry.get("worldDimensionKey")) != world:
        raise AssertionError("Entity was not observed in the same world instance")
    payload = plain(parse_nbt(entry))
    identity = entity_uuid(payload)
    if entry.get("uuid") != identity:
        raise AssertionError("Mismatched native root UUID attestation")
    tree = [entity_uuid(entity) for entity in entity_tree(payload)]
    if len(tree) != len(set(tree)):
        raise AssertionError("Duplicate UUID in serialized passenger tree")
    worlds = entry.get("entityWorlds")
    if not isinstance(worlds, dict) or set(worlds) != set(tree):
        raise AssertionError("Passenger/entity world observation coverage differs")
    for observed in worlds.values():
        if native_key(observed) != world:
            raise AssertionError("Passenger was observed in another dimension")
    return worlds


def native_entry(entry):
    return (any(key in entry for key in ("worldDimensionKey", "sameWorld", "entityWorlds"))
            or ("nbtBase64" in entry and "UUID" in plain(parse_nbt(entry))))


def validate_record(record):
    if isinstance(record, dict) and (any(key in record for key in
            ("nativeEntityContextVersion", "worldDimensionKey", "sameWorld", "entityWorlds")) or
            (isinstance(record.get("entities"), list) and any(native_entry(entry) for entry in record["entities"]))):
        observed_context(record)
    return record


def parse_dimension_nbt(entry):
    value = plain(parse_nbt(entry))
    if "witness" in entry and native_entry(entry):
        return project_entity(value, entry_context(entry, native_key(entry.get("worldDimensionKey"))))
    return value


def attribute_list(attributes):
    if not isinstance(attributes, list):
        raise ValueError("Non-list entity attributes")
    result, names = [], set()
    for original in attributes:
        if not isinstance(original, dict) or not isinstance(original.get("Name"), str):
            raise ValueError("Malformed native attribute entry")
        entry = copy.deepcopy(original)
        name = ATTRIBUTE_NAMES.get(entry["Name"], entry["Name"])
        if name in names:
            raise ValueError("Ambiguous native attribute identity: " + name)
        names.add(name)
        entry["Name"] = name
        if "Modifiers" in entry and (not isinstance(entry["Modifiers"], list)
                                     or any(not isinstance(modifier, dict) for modifier in entry["Modifiers"])):
            raise ValueError("Malformed native attribute modifiers")
        if name in KNOWN_ATTRIBUTES and "Modifiers" not in entry:
            # Native AttributeInstance.save omits an empty permanent-modifier list.
            entry["Modifiers"] = []
        result.append(entry)
    return result


def project_entity(value, contexts):
    """Project actual observations, never expected fixture values, on a copy."""
    result = copy.deepcopy(value)
    identity = entity_uuid(result)
    if identity not in contexts:
        raise ValueError("No actual native world observation for entity " + identity)
    observed = dimension_id(native_key(contexts[identity]))
    if "Dimension" in result and dimension_id(result["Dimension"]) != observed:
        raise AssertionError("Serialized entity dimension disagrees with its observed world")
    result["Dimension"] = observed
    if "Attributes" in result:
        result["Attributes"] = attribute_list(result["Attributes"])
    if "Passengers" in result:
        if not isinstance(result["Passengers"], list):
            raise ValueError("Non-list native passengers")
        result["Passengers"] = [project_entity(passenger, contexts) for passenger in result["Passengers"]]
    return actor_nbt(result)


def semantic_modifier_representation(state):
    """Make only known attributes' absent/empty modifier lists explicit on a copy."""
    result = copy.deepcopy(state)

    def entity(value):
        attributes = value.get("Attributes", {})
        if not isinstance(attributes, dict):
            raise ValueError("Malformed semantic attributes")
        for name, attribute in attributes.items():
            if name in KNOWN_ATTRIBUTES:
                if not isinstance(attribute, dict):
                    raise ValueError("Malformed semantic attribute")
                if "Modifiers" not in attribute:
                    attribute["Modifiers"] = []
        for passenger in value.get("Passengers", []):
            entity(passenger)

    for value in result.get("entities", {}).values():
        entity(value)
    return result
