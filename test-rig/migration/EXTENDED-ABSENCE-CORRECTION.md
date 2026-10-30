# Continuous Absence Validation Correction

The extended-network validator had two test defects. It checked only the gap
between the final two observations, so a continuously absent endpoint observed
for 189 native ticks failed when the final gap was 38 ticks. It also failed to
compare load/unload counters between absent observations, allowing an unseen
reload to escape that check.

The corrected validator requires at least **40 native ticks** from the first
genuine absent observation through the terminal confirmation. Every observation
in that segment must remain absent with identical native load/unload counters.
Observed reloads, hidden reloads, malformed clocks and missing observations fail.
A later stable suffix cannot erase an intervening reload.

Retained endpoints must stay loaded with unchanged counters and tile identities.
The subsequent reload requires a new load callback after the absence interval.
Existing block/state, inventory, fluid, movement, energy and restart expectations
are unchanged. No Java, gameplay, fixture, timing, or native transport change was
made.

## Evidence And Scope

The preserved 1.14 holder-extended failure records these native times:

```text
1315, 1336, 1357, 1378, 1399, 1422, 1444, 1466, 1504
```

All nine observations show the three selected endpoints absent, each with
`loads=1, unloads=1`. Their retained endpoints stay loaded at
`loads=1, unloads=0`. Recovery produces real `loads=2, unloads=1` callbacks.
The span is 189 ticks, not merely the last adjacent gap of 38.

Twenty new continuity controls first reproduced nine failures and one missing
sample error per line against the old helper. All 33 continuity/existing
extended controls then passed per line. Eight additional independent event-vector
controls bring the final focused set to **41 passing tests per line**.

Separate exact-pin raw re-audits cover the combined 1.13 extended campaign and
the 1.14, 1.15 and 1.16 holder-extended campaigns. Each includes fresh and imported
worlds, eighteen drill routes, twelve partial-network cases, reload ordering,
blocked output, restart, redstone, processing and fluid conservation.

Only three historical verdicts change: the fresh 1.14 first machine-only unload
cases. Their amended qualification passes; the original native report remains
`FAIL`. All other original verdicts remain unchanged. These are amended-validator
replays of existing measurements, not new native runs.

The later `extended-reads` campaigns are a distinct eighteen-route suite.
Their original reports are retained and are not relabelled by this correction.
A separate native 1.14 rerun is coordinated independently.

## Frozen Helpers

| Lines | Original SHA-256 | Corrected SHA-256 |
|---|---|---|
| 113 / 114 | `4EC8EBA4B808C81DCCE6B09C784B92CA7E770022F8E38FE760D60665B1F64934` | `9CE7D28FD5DFFD8BD65212F286055FB890E15BFB9C5432F5E8265FD99BE6E70B` |
| 115 / 116 | `8A1E84C3E696EFAC711D3D0D847DD532253F57BFEF1A7BC93EF5529605A6280B` | `EF746EEC9B21E5D9757684A5B1AD6387706EBCD7E2CB15D020234E9B4D57DC82` |

The validator function AST is identical across these four copies. Existing
line-ending differences account for the separate byte hashes. The 1.10 and
1.12 adapters do not contain this helper: their event-driven validators require
240-tick absence and separate 220-tick observations/isolation. They were not
changed.

## Offline Re-Audit

```powershell
python -B test-rig/migration/audit_extended_absence.py --runtime <original-run> --line 114 --manifest <sealed-inputs.json> --old-helper <retained-original-helper.py>
```

The auditor prints a new receipt; it never edits a native result, world or
expected state. It binds old/new helper hashes, its own hash, every raw input,
the exact manifest, staged jars and sealed engine inventory. It reproduces the
old verdicts before evaluating the correction. Its independent interval proof
does not call the amended helper.

Original native source hashes remain authoritative for the historical runs.
Do not replace them with the corrected helper hash or claim byte-identical
historical behavior. In 1.15/1.16, `audited-semantic-corrections.json` records the
narrow approved semantic correction and guard-only test update while preserving
the original historical-input ledgers. Other historical byte checks remain exact;
unchanged-code and corrected-function AST fingerprints prevent a byte-hash update
alone from approving a lower threshold or an unrelated oracle edit.

All retained failed and intermediate audit receipts remain available. An initial
offline auditor assumption required two logs for every profile; native imported
holder worlds legitimately have a third frozen-import boot. The corrected auditor
requires the exact two-or-three boot set rather than discarding the import log.
