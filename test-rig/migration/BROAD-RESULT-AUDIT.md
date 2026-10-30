# Broad Campaign Result Audit

Historical `native113_broad.py` through `native116_broad.py` progress writers
could write top-level `PASS` after the first profile finished, even when both
profiles were requested. A later crash could leave that misleading subset report.
The maintained writers now record `RUNNING` until the requested profiles are
complete, and retain `FAIL` plus earlier evidence if a later profile crashes.
The independent offline auditor is still required: a writer status alone does
not prove complete raw checks, artifact custody, or shutdown.

Run from the PowerAdvantage checkout, using the migration Python environment:

```powershell
python -B test-rig/migration/broad_result_audit.py --runtime <broad-output-root> --line 113
```

Use line `114`, `115`, or `116` for the corresponding engine. Both
`packaged-current` and `packaged-decoupled` are required by default. Only use
`--profiles packaged-current` or `--profiles packaged-decoupled` when that was
the explicitly requested campaign. The auditor never infers intended coverage
from the subset of results it happens to find. Missing, extra, unfinished, or
failed profiles return exit code 1.

The auditor reads but does not modify results, worlds, jars, or runner scripts.
Its JSON output binds the inspected evidence by SHA-256. It checks:

- the requested profile set, local results, all five phases, and unmasked errors;
- all 21 stations against the definition inside the pinned harness jar;
- every raw assertion ID, type, result, available-mod condition, and summary count;
- retained manual instructions and truthful `MANUAL_REQUIRED` versus `SKIP`;
- raw drill SNBT, six facings per drill, inventory witnesses, movement and cold state;
- loaded mod identities, staged jar bytes, launcher and sealed engine inventory when supplied;
- native startup and all-dimension save/shutdown evidence for both boots;
- unchanged evidence throughout the audit, with existing narrow native fatal classifications.

Station resource hashes may differ only by LF/CRLF normalization during Gradle
resource processing. No semantic changes or omitted definitions are accepted.

This is bounded broad-sweep proof, not a replacement for operating, conservation,
harvest, migration, endurance, GUI or manual gameplay tests. Manual procedures
are not automated passes. Historical broad output does not independently retain
the operating-system process exit code; the auditor states that gap explicitly
and verifies the available save/shutdown logs instead of inventing an exit receipt.
Forge 25 issues an explicit `save-all flush`; the later broad protocols use the
native stop autosave and retain completed chunk-storage saves for all dimensions.
The audit follows those actual protocols instead of inventing an extra command.

Focused controls:

```powershell
python -B -m unittest discover -s test-rig/migration -p test_broad_result_audit.py -v
```

The writer-only status repair was applied after all in-flight broad campaigns
finished and the coordinating agent cleared those files. Offline replay of the
archived native evidence verifies the repaired JSON writer without rerunning or
rebuilding worlds. Historical native receipts keep their original protocol hashes;
separate replay receipts bind the old and new hashes. Geometry, actors and native
assertions are unchanged.
