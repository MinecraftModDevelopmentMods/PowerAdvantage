# Mining and Block Properties

The 1.15 checks use real survival block breaking, not just the presence of loot
JSON. Failed tools must produce no items; successful tools and silk touch must
retain the historical drops. Every wire and scaffold connection mask is tested.

The first run was refused because its disposable runtime lacked
`advantage-works.allow`. The launcher now creates both required marker files.
The world-name and operator guards remain unchanged, and the refused run is
retained separately.

## Solidness Reference

A new test initially assumed that every narrow block was non-solid. That is
not how the 1.14 engine behaves: ordinary electric wires are solid but have a
narrow collision shape. The lattice scaffold and steel frame are non-solid.
This distinction must not be changed merely to satisfy a test.

An isolated Java check instantiated the actual accepted 1.14 classes against
their original Forge classpath, without loading a world:

| Component | `isSolid` | Render Layer |
| --- | --- | --- |
| Electric wire | `true` | Solid |
| Electric scaffold | `false` | Cutout |
| Photovoltaic generator | `true` | Solid |
| Steel frame | `false` | Cutout |

The accepted Electric development jar is identified by SHA-256
`2B32B667092D3BFBC4CF905AE0C630C2767B3C6E191B62035FF2ED0CDEEBF05E`.
The check source has SHA-256
`03743141CB0A72D0344B05B15CCB65DC2B2CCC60F95285635C4D46C8684975E5`;
the captured output has SHA-256
`76A55A91E180190CAB4758D8D7EEEC1327131EFFD688520AD60B2D60A106315C`.
The local evidence is retained under
`D:\PowerAdvantage\_verification\pa115-harvest-baseline14-20261026`.

Only the newly introduced wire-solidness expectation was corrected. Harvest
levels, failed-tool checks, drop quantities, silk-touch checks, collision bounds,
connection masks and the solar-panel check remain strict. No production block
was changed to accommodate the mistaken expectation.
