"""Strict native 1.12 diagnostics, recording two mis-levelled notices in pinned OreSpawn 3."""

import re

LEGACY_ORESPAWN_SHA = "86A5E567950D5428F413A0C744FD4EA6DF5807955CBD444392595A1871CC1DFA"
INTEGRATION_NOTICE = re.compile(
    r"^\[[^\]]+\] \[Server thread/FATAL\] \[orespawn\]: Loading Integration For (orespawn|basemetals)$")

def audit_lines(lines, orespawn_sha=None):
    classified = []
    for number, line in enumerate(lines, 1):
        if orespawn_sha == LEGACY_ORESPAWN_SHA and INTEGRATION_NOTICE.fullmatch(line):
            classified.append(dict(line=number, message=line,
                classification="PINNED_ORESPAWN_3_INTEGRATION_NOTICE",
                dependencySha256=LEGACY_ORESPAWN_SHA))
            continue
        if any(marker in line for marker in ("/ERROR]", "/FATAL]", "NoSuchFieldError", "NoSuchMethodError",
                                            "Encountered an unexpected exception", "Failed to reload data packs")):
            raise AssertionError("Native runtime diagnostic at line " + str(number) + ": " + line)
    return dict(status="PASS", classifiedDiagnostics=classified)
