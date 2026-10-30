"""Strict 1.16 serialization boundaries; never rewrites a world or weakens an oracle."""

import copy


DIMENSIONS = {-1: "minecraft:the_nether", 0: "minecraft:overworld", 1: "minecraft:the_end"}


def dimension_id(value):
    if isinstance(value, bool):
        raise ValueError("Boolean is not a dimension")
    if isinstance(value, int) and value in DIMENSIONS:
        return value
    for identity, key in DIMENSIONS.items():
        if value == key:
            return identity
    raise ValueError("Unsupported fixture dimension: " + repr(value))


def signed(value, bits):
    value &= (1 << bits) - 1
    return value - (1 << bits) if value & (1 << (bits - 1)) else value


def uuid_longs(words):
    if not isinstance(words, (list, tuple)) or len(words) != 4:
        raise ValueError("Native UUID must contain exactly four signed ints")
    if any(isinstance(word, bool) or not isinstance(word, int) or not -(1 << 31) <= word < (1 << 31) for word in words):
        raise ValueError("Malformed native UUID int array")
    return (signed((words[0] & 0xffffffff) << 32 | words[1] & 0xffffffff, 64),
            signed((words[2] & 0xffffffff) << 32 | words[3] & 0xffffffff, 64))


def actor_nbt(value):
    """Expose exact UUID/dimension identity in the historical oracle's representation."""
    if isinstance(value, list):
        return [actor_nbt(entry) for entry in value]
    if not isinstance(value, dict):
        return value
    result = {key: actor_nbt(entry) for key, entry in value.items()}
    if "UUID" in result:
        most, least = uuid_longs(result.pop("UUID"))
        if "UUIDMost" in result or "UUIDLeast" in result:
            raise ValueError("Ambiguous duplicate UUID encodings")
        result.update(UUIDMost=most, UUIDLeast=least)
    if "Owner" in result and isinstance(result["Owner"], list):
        import uuid
        most, least = uuid_longs(result.pop("Owner"))
        if "OwnerUUID" in result:
            raise ValueError("Ambiguous duplicate owner encodings")
        result["OwnerUUID"] = str(uuid.UUID(int=((most & ((1 << 64) - 1)) << 64) | (least & ((1 << 64) - 1))))
    for key in ("Dimension", "SpawnDimension"):
        if key in result:
            result[key] = dimension_id(result[key])
    return result


def worldgen_settings(data):
    settings = data.get("WorldGenSettings")
    if not isinstance(settings, dict):
        raise ValueError("Native116 level.dat is missing WorldGenSettings")
    for key in ("seed", "generate_features", "bonus_chest", "dimensions"):
        if key not in settings:
            raise ValueError("Missing native WorldGenSettings field: " + key)
    dimensions = settings["dimensions"]
    if not isinstance(dimensions, dict) or not set(DIMENSIONS.values()).issubset(dimensions):
        raise ValueError("Missing native dimension registry entries")
    for entry in dimensions.values():
        if not isinstance(entry, dict) or "type" not in entry or not isinstance(entry.get("generator"), dict):
            raise ValueError("Malformed native dimension generator")
    return copy.deepcopy(settings)


def world_info(data, allow_legacy=False):
    if "WorldGenSettings" not in data:
        if not allow_legacy:
            raise ValueError("Legacy generation fields cannot prove a native116 save")
        return dict(MapFeatures=int(data["MapFeatures"]), RandomSeed=int(data["RandomSeed"]),
                    generatorName=str(data["generatorName"]))
    settings = worldgen_settings(data)
    generator = settings["dimensions"][DIMENSIONS[0]]["generator"]
    kind = generator.get("type")
    if kind not in ("minecraft:flat", "minecraft:noise", "minecraft:debug"):
        raise ValueError("Unknown native generator type: " + repr(kind))
    return dict(MapFeatures=int(settings["generate_features"]), RandomSeed=int(settings["seed"]),
                generatorName={"minecraft:flat": "flat", "minecraft:noise": "default", "minecraft:debug": "debug_all_block_states"}[kind])


def decode_palette(palette, words, count=4096):
    """1.16 pads each long; entries never straddle a 64-bit boundary."""
    size = len(palette)
    if not 1 <= size <= count or count <= 0:
        raise ValueError("Invalid chunk palette size")
    if size == 1 and not words:
        return [palette[0]] * count
    bits = max(4, (size - 1).bit_length())
    per_long = 64 // bits
    if len(words) != (count + per_long - 1) // per_long:
        raise ValueError("Native116 BlockStates has the wrong padded length")
    result = []
    for index in range(count):
        identity = ((int(words[index // per_long]) & ((1 << 64) - 1)) >> ((index % per_long) * bits)) & ((1 << bits) - 1)
        if identity >= size:
            raise ValueError("BlockStates references an absent palette entry")
        result.append(palette[identity])
    return result
