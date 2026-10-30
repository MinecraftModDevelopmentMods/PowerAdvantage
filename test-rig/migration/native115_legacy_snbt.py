"""Parse legacy diagnostic SNBT, requiring binary evidence for lossy byte summaries."""

from itertools import tee
import nbtlib
from nbtlib.literal.parser import Parser, tokenize, IncompatibleItemType


class ByteArraySummary(nbtlib.ByteArray):
    def __new__(cls, length):
        value = super().__new__(cls, [])
        value.summary_length = length
        return value


class LegacyParser(Parser):
    def list_tokens(self):
        self.next()
        while self.current_token.type != "CLOSE_BRACKET":
            yield self.current_token
            self.next()
            if self.current_token.type == "CLOSE_BRACKET": return
            if self.current_token.type != "COMMA": raise self.error("Expected list comma")
            self.next()

    def parse_list(self):
        values, indexed = [], None
        for token in self.list_tokens():
            self.token_stream, lookahead = tee(self.token_stream)
            following = next(lookahead, None)
            if not values and token.type == "NUMBER" and following is not None and following.value == "bytes":
                if not token.value.isdigit(): raise self.error("Invalid byte summary length")
                self.next().next()
                if self.current_token.type != "CLOSE_BRACKET": raise self.error("Malformed byte summary")
                return ByteArraySummary(int(token.value))
            has_index = token.type == "NUMBER" and following is not None and following.type == "COLON"
            if indexed is None: indexed = has_index
            if has_index != indexed: raise self.error("Mixed indexed and plain list")
            if has_index:
                if token.value != str(len(values)): raise self.error("Nonsequential legacy list index")
                self.next().next()
            values.append(self.parse())
        try:
            return nbtlib.List(values)
        except IncompatibleItemType as error:
            raise self.error(str(error)) from None


def _resolve(value, binary):
    if isinstance(value, ByteArraySummary):
        if not isinstance(binary, nbtlib.ByteArray) or len(binary) != value.summary_length:
            raise ValueError("Legacy byte summary requires matching binary array type and length")
        return binary.copy()
    if isinstance(value, nbtlib.Compound):
        return nbtlib.Compound({key: _resolve(tag, binary.get(key) if isinstance(binary, nbtlib.Compound) else None)
                               for key, tag in value.items()})
    if isinstance(value, nbtlib.List):
        peers = binary if isinstance(binary, (nbtlib.List, nbtlib.IntArray, nbtlib.LongArray)) else []
        return nbtlib.List([_resolve(tag, peers[index] if index < len(peers) else None) for index, tag in enumerate(value)])
    return value


def parse_legacy_snbt(literal, binary=None):
    parser = LegacyParser(tokenize(literal))
    value = parser.parse()
    if literal[parser.token_span[1]:].strip(): raise parser.error("Trailing NBT content")
    return _resolve(value, binary)
