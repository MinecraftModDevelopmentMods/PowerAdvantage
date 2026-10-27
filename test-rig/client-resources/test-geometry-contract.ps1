$ErrorActionPreference = 'Stop'
$checks = 0
function Assert-GeometryContract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
$relative = 'java/zone/moddev/mc/advantageworks/client'
$geometry = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$relative/ChildRendererGeometryProbe.java") -Raw
$main = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$relative/ClientResourceProbe.java") -Raw
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
Assert-GeometryContract ($geometry -notmatch '(?m)^import zone\.moddev\.mc\.(?:steamadvantage|electricadvantage)\.') 'Child renderer probe must not add compile dependencies on the expansions.'
Assert-GeometryContract ($geometry -notmatch 'new (?:DrillBitRenderer|LaserDrillRenderer|LaserTurretRenderer|HydroturbineRenderer)|renderGeometry\(|Unsafe|allocateInstance|new ClientWorld|loadWorld\(') 'Use dispatcher-owned production renderer instances without bypassing construction or loading a world.'
foreach ($fragment in 'TileEntityRendererDispatcher.instance.getRenderer(tile)', 'minecraft.getRenderManager().renderers.get(type)',
        'renderer.render(tile, partial, matrices, buffers', 'buffer.begin(type.getDrawMode(), type.getVertexFormat())',
        'buffer.getNextBuffer()', 'private static final float EPSILON = 0.0001F;',
        'mesh(vertices, 24)', 'mesh(vertices, 16)', 'mesh(vertices, 40)',
        'new float[]{0F, 0.5F}', 'for (Direction facing : Direction.values())',
        'partial-tick rotation did not move vertices', 'Production renderer did not restore its matrix stack',
        'position.equals(original.getMatrix()) && normal.equals(original.getNormal())',
        'native_buffers', 'vertices_xyz_uv_normal_alpha', 'bounds_xyz', 'uv_bounds',
        'Float.isFinite(value)', 'blank/degenerate quad', 'Beam cardinal bounds/length changed',
        'Idle turret uses the active optic UVs', 'Active turret uses the idle optic UVs',
        'Production renderer selected the wrong texture', 'ImageIO.read(resource.getInputStream())',
        'renderFailure.addSuppressed(failure)', 'child-renderer-geometry.json',
        'MANUAL_GAP_WORLD_REQUIRED', 'PASS_WITH_MANUAL_GAPS',
        'cases.size() == 26', 'passed == 25 && gaps == 1') {
    Assert-GeometryContract ($geometry.Contains($fragment)) "Missing geometry contract: $fragment"
}
$geometryCall = $main.IndexOf('if (Boolean.getBoolean("advantageworks.childRendererGeometry")) ChildRendererGeometryProbe.verify(minecraft, result);')
Assert-GeometryContract ($geometryCall -gt $main.IndexOf('RecipeBookGroupingProbe.verify(minecraft, result);')) 'Optional geometry must follow all existing resource oracles.'
Assert-GeometryContract ($main.IndexOf('result.addProperty("primary_resource_status", "PASS");') -lt $geometryCall) 'Keep primary resource results separate from the extra renderer scope.'
Assert-GeometryContract ($runner.Contains('[switch]$ChildRendererGeometry') -and $runner.Contains('-Dadvantageworks.childRendererGeometry=true')) 'Additional renderer geometry must be explicitly selected.'
Assert-GeometryContract ($runner.Contains('if ($ChildRendererGeometry -and -not $RequireExtensions)')) 'Geometry must require the combined scope.'
$unchanged = @{
    OilRenderProbe = '3560449BA7DBB2AD60CDEF4FEB5A7DCB53EF66A0AA9A82EC538F4D1993CD8ECE'
    RecipeBookGroupingProbe = 'C9E02B30693C7CCFA024290DE4B286E707BE5DD9BC07633704437FEA4E660376'
    TankVisualProbe = '756DE3330EDF1A9E649A2CE06BEFD7AC4A1C5E39E4443EE69A0AFE1D33A1807F'
}
foreach ($name in $unchanged.Keys) {
    Assert-GeometryContract ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot "$relative/$name.java")).Hash -eq $unchanged[$name]) "Primary capture/oracle file changed: $name"
}
Write-Output "PASS: $checks static additional-geometry wiring and primary-oracle preservation checks. No renderer, Java or client was run."
