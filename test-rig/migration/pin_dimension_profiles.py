"""Replace only test probes in an existing hash-pinned qualification manifest."""

import argparse
import json
from pathlib import Path
import shutil
import zipfile

from matrix import POWER, digest, write_json


def pin(base, output, archive=None):
    profiles = json.loads(base.read_text())
    expected_classes = {
        "AdvantageMigrationProbe-110-1.0.0.jar": "zone/moddev/mc/advantageworks/migration/LegacyDimensionEntities.class",
        "AdvantageMigrationProbe-112-1.0.0.jar": "zone/moddev/mc/advantageworks/migration/LegacyDimensionEntities.class",
        "AdvantageWorksTestHarness-1.1.0.113021.jar": "zone/moddev/mc/advantageworks/DimensionEntityProbe.class",
    }
    for profile in profiles.values():
        for jar in profile["jars"]:
            previous = Path(jar["path"])
            if previous.name in expected_classes:
                if "MigrationProbe" in previous.name:
                    target = previous.name.split("-")[1]
                    path = POWER / "test-rig/migration-probe/build" / target / "libs" / previous.name
                else:
                    path = POWER / "test-rig/build/libs" / previous.name
                with zipfile.ZipFile(path) as contents:
                    if expected_classes[previous.name] not in contents.namelist():
                        raise AssertionError("Dimension probe missing: " + str(path))
                if archive is not None:
                    staged = archive.resolve() / digest(path) / path.name
                    staged.parent.mkdir(parents=True, exist_ok=True)
                    if staged.exists() and digest(staged) != digest(path):
                        raise AssertionError("Archived probe changed: " + str(staged))
                    if not staged.exists():
                        shutil.copy2(path, staged)
                    path = staged
                jar.update(path=str(path), sha256=digest(path))
            elif digest(previous) != jar["sha256"]:
                raise AssertionError("Production/dependency artifact changed: " + str(previous))
    write_json(output, profiles)
    write_json(output.with_suffix(".provenance.json"), {"base_manifest": str(base.resolve()),
               "base_manifest_sha256": digest(base), "replacements": sorted(expected_classes),
               "production_and_dependency_pins": "UNCHANGED"})
    return profiles


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--archive", type=Path, help="Retain test probe bytes outside mutable build outputs")
    args = parser.parse_args()
    pin(args.base, args.output, args.archive)
