# Native Machine GUI Check

This isolated client probe checks the three Advantage mods on Minecraft 1.12.2,
Forge 14.23.5.2859 and stable mappings 39. It opens no saved world and registers
no gameplay content. It is not included in release jars or ordinary launches.

Use Java 8, the matching native launch profile, and the exact three candidate
jars being tested. `run.ps1` requires `-JavaHome`, `-PowerJar`, `-SteamJar` and
`-ElectricJar`; `-LaunchProfile` selects the prepared isolated launch when it is
not `runClient.launch`. `-PreflightOnly` checks and snapshots inputs without
starting Minecraft or claiming a GUI pass.

A full run requires 39 distinct screens, 351 caption/layout cases and 355
rejected negative controls, with the expected images. It preserves the original
artwork's baked labels and tests the generic title and inventory rows separately.
This is GUI evidence, not machine-operation or manual-play acceptance.

## Retained Evidence

Each invocation writes to a new `run-client-resource-runs/<timestamp>-<id>/`
directory at the PowerAdvantage project root, outside Gradle's `build/` output.
It retains input hashes, logs, images and `client-resources.json`. Preflight or
client failures also write `runner-failure.json`; partial native evidence is not
replaced with a passing report. Gradle `clean` does not remove this directory.
The output remains Git-ignored. Moving the output path does not recover older
captures already lost from the former build-based location.

With all six version checkouts available, run this from the workspace root:

```powershell
./PA116/PowerAdvantage/test-rig/client-resources/test-launcher.ps1 -StaticOnly -CrossVersionEvidenceRoots -WorkspaceRoot .
```

This static contract checks all six runner sources and rejects parser-valid
changes to the old `build/` output, traversal into `build/`, an absolute build
path or a changed project root. A missing runner fails the check. It starts no
Java process and does not qualify the native GUI or the candidate jars.
