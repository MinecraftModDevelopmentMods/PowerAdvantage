# Machine GUI Checks

The original 1.16 screen base drew vanilla labels over the machine faceplates.
The first attempted correction removed those labels. That was wrong: the tank
then had no machine name or inventory label. The tests incorrectly required no
label pixels instead of requiring readable text, and the visual review missed
the same loss. The earlier GUI acceptance is withdrawn; its logs and images
remain available as failed-design evidence.

The replacement reserves separate rows for the translated machine name and
`Inventory`. Artwork, gauges and machine slots move down together; the player
inventory and its hover positions move together too. The assembled screen is
240 logical pixels tall, fitting Minecraft's minimum supported GUI viewport.
Player-assigned names are preserved. Long names fit within the screen with an
ellipsis and their full text remains available in a caption tooltip.

Fluid levels, Steam needles and progress indicators are bounded to their drawn
range. Invalid numbers display as empty. The hydroelectric output display now
uses 0 or 1 instead of 0 or 15; its generation rate is still 4 energy per tick.
No inventories, saved keys, registry names or machine processing rules change.

## Coverage

The native client probe discovers GUI definitions from the registered blocks,
rather than maintaining a separate list of screens to test. The combined client
must contain all 39 definitions:

| Mod | Screens | Equipment |
| --- | --- | --- |
| Power | 14 | Drain, discharge, portable and metal tanks, still, conveyor and all eight filters |
| Steam | 10 | Four boilers, tank, furnace, crusher, drill, pump and still |
| Electric | 15 | Three generators, battery array, furnace, crusher, drill, turret, fabricator, oven, pump, still, refinery and both growth-chamber blocks |

Each screen is drawn through Minecraft's actual container renderer at logical
sizes 320x240, 426x240 and 640x360. The middle case also uses a 2x framebuffer.
Empty, half-full and full fluid/energy fixtures produce 351 saved images. A
further 39 images check a real inventory item tooltip and its hover hitbox.
Four images deliberately remove or corrupt captions, and two more check long
names and their full-text tooltip. These negative controls are expected to fail
the label validator; they are not examples of acceptable screens.
The text oracle is the separately reviewed English equipment-name list in
`test-rig/client-resources/resources/gui-label-expectations.json`, not the output
of the production label renderer. Every state and size must contain the exact
machine name and `Inventory` glyphs. Assertions also reject text over artwork,
artwork in label rows, slot misalignment, missing textures and out-of-bounds
decoration. All nine conveyors must use equipment names, not Java class names.

The new assertions first rejected all 39 screens from the blank-caption build.
Blank-label controls must also fail for every state and size. Additional
negative controls omit only the machine name, omit only `Inventory`, substitute
an untranslated name and put the title over a gauge. All must be rejected.
Their expected failures are recorded separately from product passes. Long-name
fitting and its full-text tooltip receive their own rendered checks. Helper
tests also cover negative, oversized and nonfinite readings.

The runner requires caption contract version 2, all 351 size/state text checks
and all 355 rejected controls in the combined profile. An old bounds-only or
no-label report cannot qualify the current GUI. Review images include the whole
viewport so that captions cannot disappear outside a cropped review sheet.

These are worldless rendering checks with read-only block-state fixtures, not a
new gameplay, migration or multiplayer run. A tester should still check their
preferred GUI scale, language and resource pack in game. Earlier operating and
migration results remain attached to their original frozen jars, not relabelled
as results for this display-only candidate.

## Repeat The Check

Build all three development jars first. The extensions can still be compiled
against the frozen, API-compatible Power dependency recorded in their build
properties. The combined preview runs the corrected Power output instead.

```text
gradlew.bat clientResourceProbeJar verifyEclipseProductionClasspath prepareCombinedEclipseLaunch -PadvantageDevelopmentProfile=docs/1.16/gui-development-inputs.json
test-rig\client-resources\run.ps1 -JavaHome <jdk8> -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -MachineGuiLayouts
```

The runner copies the selected production output and dependency mods into a
disposable client directory. Logs, input hashes, results and images remain under
`build/client-resource-runs/`; they are not release contents or source-controlled
test output. Failed attempts are retained too. Do not rebuild jars in use by an
interactive Minecraft client.

For Eclipse, refresh the Gradle project, use **Project > Clean**, then launch
`runClientAllAdvantage.launch`. The normal Power-only launch remains available.
