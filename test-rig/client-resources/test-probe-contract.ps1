param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA114/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$ReferenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$referenceHashes = @{
    ClientResourceProbe = 'A6003A2EE76006C3BA3E279EC49E49DA69FD0A9B9146C7D0328288A85E173E02'
    TankVisualProbe = '1256340A70F53F5B0D2C9B824DF5C9C6E2F0DFFAD1AFDB4EA560FE03B30137F4'
    OilRenderProbe = 'DF697A6BE69453F4956C25756110F2BB51B2DE78D95F4BF79A7612B49F18B1BA'
    RecipeBookGroupingProbe = '9D842426742BEC9B6E0BEB1C763CAF1CF0D9C96F006A53796459B4B92BA1F4F5'
}
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
function Normalize-Probe([string]$Source) {
    $Source.Replace("`r`n", "`n").Replace('1.14.4', '1.15.2').Replace('28.2.26', '31.2.57').
        Replace('GlStateManager.', 'RenderSystem.').Replace('buffer.getVertexCount()', 'vertices').
        Replace('quad.getSprite()', 'quad.func_187508_a()')
}
$relative = 'java/zone/moddev/mc/advantageworks/client'
foreach ($name in 'ClientResourceProbe','TankVisualProbe','OilRenderProbe','RecipeBookGroupingProbe') {
    $sourcePath = Join-Path $ReferenceRoot "$relative/$name.java"
    $hash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    Assert-Contract ($hash -eq $referenceHashes[$name]) "Reference SHA-256 mismatch for $name.java; expected $($referenceHashes[$name]), got $hash. Do not substitute a different baseline."
    $source = Get-Content -LiteralPath $sourcePath -Raw
    $target = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$relative/$name.java") -Raw
    $expected = Normalize-Probe $source
    $actual = Normalize-Probe $target
    # Keep every original assertion, including its operands, thresholds and message.
    foreach ($assertion in [regex]::Matches($expected, '(?s)\brequire\(.*?\);')) {
        Assert-Contract ($actual.Contains($assertion.Value)) "$name lost an original assertion: $($assertion.Value)"
    }
    foreach ($literal in [regex]::Matches($expected, '"[^"\r\n]*(?::|\.png|\.bin|\.dat)[^"\r\n]*"')) {
        Assert-Contract ($actual.Contains($literal.Value)) "$name changed a resource, fixture or diagnostic literal: $($literal.Value)"
    }
    if ($name -eq 'RecipeBookGroupingProbe') {
        $expected = $expected.Replace('import com.mojang.blaze3d.platform.GlStateManager;', 'import com.mojang.blaze3d.systems.RenderSystem;')
        Assert-Contract ($actual -ceq $expected) 'Recipe grouping/capture changed beyond native version and RenderSystem adaptation.'
    }
    if ($name -eq 'TankVisualProbe') {
        $actual = $actual.Replace("import com.mojang.blaze3d.systems.RenderSystem;`n", '')
        Assert-Contract ($actual -ceq $expected) 'Tank simulation, packets, floor checks or capture changed beyond RenderSystem adaptation.'
    }
    if ($name -eq 'OilRenderProbe') {
        $bucket = [regex]::Match($expected, '(?s)private static void requireBucket\(.*?(?=    private static boolean check)').Value
        Assert-Contract ($bucket.Length -gt 0 -and $actual.Contains($bucket)) 'Oil bucket identity/capability assertions changed.'
        $cases = [regex]::Match($expected, '(?s)boolean valid = true;.*?if \(!valid\).*?;').Value
        Assert-Contract ($cases.Length -gt 0 -and $actual.Contains($cases)) 'Oil/water/lava cases, sprites or failure aggregation changed.'
        Assert-Contract ($target.Contains('buffer.getNextBuffer()') -and $target.Contains('built.getFirst().getVertexCount()')) 'Read the completed native draw state, not the reset builder count.'
        Assert-Contract ($target.Contains('element.getUsage() == usage && element.getIndex() == index')) 'Native vertex offsets must use the actual format elements.'
        Assert-Contract ($target.Contains('finally { net.minecraft.tags.FluidTags.setCollection(previousTags); }')) 'Synthetic fluid tags must be restored.'
        Assert-Contract ($target -notmatch 'IEnviromentBlockReader|getTextureMap\(|getByteBuffer\(|getUvOffsetById\(|getColorOffset\(') 'Removed 1.14 fluid/atlas/vertex APIs remain.'
    }
}
$pack = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/pack.mcmeta') -Raw | ConvertFrom-Json
Assert-Contract ($pack.pack.pack_format -eq 5) 'Native 1.15 probe resource pack must use format 5.'
$metadata = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/META-INF/mods.toml') -Raw
foreach ($pin in 'loaderVersion="[31,32)"','versionRange="[1.15.2]"','versionRange="[31.2.57]"','versionRange="[2.4.6.115021]"') {
    Assert-Contract ($metadata.Contains($pin)) "Missing native probe metadata pin: $pin"
}
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
$failure = [regex]::Match($runner, '(?s)\} catch \{\s*# Preserve native partial results.*?\} finally \{').Value
Assert-Contract ($failure.Contains('runner-failure.json') -and $failure.Contains('if (-not (Test-Path')) 'Runner must preserve partial native results and write its failure separately.'
Assert-Contract ($runner -notmatch '\b(?:Remove-Item|Clear-Content)\b') 'Runner must not delete old or failed evidence.'
Write-Output "PASS: $checks static probe preservation checks against $ReferenceRoot. Compilation and graphical acceptance NOT RUN."
