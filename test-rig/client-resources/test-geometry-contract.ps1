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
foreach ($fragment in 'TileEntityRendererDispatcher.instance.getRenderer(tile)', 'minecraft.getEntityRenderDispatcher().renderers.get(type)',
        'renderer.render(tile, partial, matrices, buffers', 'buffer.begin(type.mode(), type.format())',
        'buffer.popNextBuffer()', 'private static final float EPSILON = 0.0001F;',
        'mesh(vertices, 24)', 'mesh(vertices, 16)', 'mesh(vertices, 40)',
        'new float[]{0F, 0.5F}', 'for (Direction facing : Direction.values())',
        'partial-tick rotation did not move vertices', 'Production renderer did not restore its matrix stack',
        'position.equals(original.pose()) && normal.equals(original.normal())',
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
# Native method names change, but the fixed accepted source hashes and complete
# recipe/tank capture comparisons remain enforced by the primary contract.
& (Join-Path $PSScriptRoot 'test-probe-contract.ps1')
$checks++
$reference = Join-Path $PSScriptRoot "../../../../PA115/PowerAdvantage/test-rig/client-resources/$relative/ChildRendererGeometryProbe.java"
Assert-GeometryContract ((Get-FileHash -LiteralPath $reference).Hash -eq 'A125FEC9F7322B019887FB240316411DBDF25173501CFA66F8F6816D536E4E03') 'The accepted renderer reference changed.'
$source = Get-Content -LiteralPath $reference -Raw
$source = $source.Replace('minecraft.getRenderManager()', 'minecraft.getEntityRenderDispatcher()').
    Replace('type.getDrawMode()', 'type.mode()').Replace('type.getVertexFormat()', 'type.format()').
    Replace('DefaultVertexFormats.ENTITY', 'DefaultVertexFormats.NEW_ENTITY').
    Replace('minecraft.world == null', 'minecraft.level == null').
    Replace('minecraft.isIntegratedServerRunning()', 'minecraft.hasSingleplayerServer()').
    Replace('tile.getWorld()', 'tile.getLevel()')
foreach ($assertion in [regex]::Matches($source, '(?s)\brequire\(.*?\);')) {
    Assert-GeometryContract ($geometry.Contains($assertion.Value)) "Renderer oracle changed: $($assertion.Value)"
}
Write-Output "PASS: $checks static additional-geometry wiring and primary-oracle preservation checks. No renderer, Java or client was run."
