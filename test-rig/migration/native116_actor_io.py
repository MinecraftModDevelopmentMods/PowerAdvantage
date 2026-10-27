"""Bind exact 1.16 UUID/dimension representations to unchanged historical oracles."""

import types
import json

import actors
import dimensions
from matrix import normalized, parse_nbt, plain
from native116_format import actor_nbt
from native116_dimension_io import parse_dimension_nbt, validate_record


def semantic(value, keys, registry):
    return actors.semantic(actor_nbt(plain(value)), keys, registry)


def native_normalized(value, registry=None):
    return normalized(actor_nbt(plain(value)), registry)


def bind(function, **bindings):
    return types.FunctionType(function.__code__, dict(function.__globals__, **bindings),
                              function.__name__, function.__defaults__)


capture_actors = bind(actors.capture, semantic=semantic)


class DimensionJson:
    @staticmethod
    def loads(text):
        return validate_record(json.loads(text))


capture_dimensions = bind(dimensions.capture, semantic=semantic, normalized=native_normalized,
                          parse_nbt=parse_dimension_nbt, json=DimensionJson)


def parsed_actor(entry):
    return actor_nbt(plain(parse_nbt(entry)))
