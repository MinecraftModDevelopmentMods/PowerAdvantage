"""Strict native 1.10 diagnostics with one exact public-Forge signature notice."""

import re

PUBLIC_FORGE_SHA = "809C2EA5B4D4F3E5A294D3A326744F20FEC412A1263E708297D00F512E377542"
SIGNATURE_NOTICE = re.compile(r"^\[\d\d:\d\d:\d\d\] \[main/ERROR\] \[FML\]: "
    r"FML appears to be missing any signature data\. This is not a good thing$")

def audit_lines(lines, forge_sha=None):
    classified = []
    for number, line in enumerate(lines, 1):
        if forge_sha == PUBLIC_FORGE_SHA and SIGNATURE_NOTICE.fullmatch(line):
            classified.append(dict(line=number, message=line,
                classification="EXACT_PUBLIC_FORGE_2511_SIGNATURE_NOTICE",
                dependencySha256=PUBLIC_FORGE_SHA))
            continue
        if any(marker in line for marker in ("/ERROR]", "/FATAL]", "NoSuchFieldError", "NoSuchMethodError",
                                            "Encountered an unexpected exception", "Failed to reload data packs")):
            raise AssertionError("Native runtime diagnostic at line " + str(number) + ": " + line)
    return dict(status="PASS", classifiedDiagnostics=classified)
