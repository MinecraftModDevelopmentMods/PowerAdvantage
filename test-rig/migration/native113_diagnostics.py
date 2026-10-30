"""Strict native log audit with narrowly recorded Forge 25 diagnostics."""

from pathlib import Path
import re
import tempfile


TEMP_CLEANUP = re.compile(
    r"^\[[^\]]+\] \[Thread-[0-9]+/FATAL\] \[ne\.mi\.fm\.pa\.ModFileResourcePack/\]: "
    r"Failed to clean up tempdir (.+)$")
CONFIG_RELOAD = re.compile(
    r"^\[[^\]]+\] \[Thread-[0-9]+/FATAL\] \[ne\.mi\.co\.ForgeConfig/CORE\]: "
    r"Forge config just got changed on the file system!$")


def audit_lines(lines, temp_root=None):
    """The caller must also require a clean process exit and native startup markers."""
    temporary = Path(temp_root if temp_root is not None else tempfile.gettempdir()).resolve()
    saving_complete = False
    stopping = False
    classified = []
    for number, line in enumerate(lines, 1):
        if "Stopping server" in line or "Stopping the server" in line:
            stopping = True
        if "All chunks are saved" in line or "Saved the game" in line:
            saving_complete = True
        if "/ERROR]" in line or any(text in line for text in (
                "NoSuchFieldError", "NoSuchMethodError", "Failed to reload data packs",
                "Encountered an unexpected exception")):
            raise AssertionError("Native runtime error at line " + str(number) + ": " + line)
        if "/FATAL]" not in line:
            continue
        if CONFIG_RELOAD.fullmatch(line):
            classified.append(dict(line=number, message=line,
                classification="FORGE_25_CONFIG_RELOAD_NOTICE",
                scope="ForgeConfig.onFileChange logs its normal reload event; subsequent errors are not exempt"))
            continue
        match = TEMP_CLEANUP.fullmatch(line)
        path = Path(match[1]).resolve() if match else None
        if (match and stopping and saving_complete and path.parent == temporary
                and re.fullmatch(r"modpacktmp[0-9]+", path.name)):
            classified.append(dict(line=number, message=line,
                classification="FORGE_25_POST_SAVE_TEMP_DIRECTORY_CLEANUP",
                scope="Forge ModFileResourcePack shutdown hook; not a world save or an Advantage error"))
        else:
            raise AssertionError("Unclassified fatal runtime error at line " + str(number) + ": " + line)
    return dict(status="PASS", classifiedDiagnostics=classified,
                scope="Error/fatal audit only; native startup, process exit and machine outcomes are separate gates")
