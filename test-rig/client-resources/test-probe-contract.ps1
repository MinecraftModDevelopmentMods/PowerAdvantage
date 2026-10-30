param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../..'))
$ErrorActionPreference = 'Stop'
$ReferenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$referenceCommit = '08a002787f149e1bf7c85e9974cd281c167c212c'
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
function Canonical-Source([string]$Source) {
    $Source = $Source.Replace("`r`n", "`n")
    $Source = [regex]::Replace($Source, '(?m)^import [^\r\n]+\r?\n', '')
    $Source = [regex]::Replace($Source, '/\*[\s\S]*?\*/|//[^\r\n]*', '')
    [regex]::Replace($Source, '\s+', '')
}
$resolved = & git -C $ReferenceRoot rev-parse "$referenceCommit`^{commit}"
if ($LASTEXITCODE -ne 0) { throw 'The frozen native 1.13 source commit is unavailable.' }
Assert-Contract ($resolved.Trim() -eq $referenceCommit) 'Do not silently replace the native 1.13 reference.'
$relative = 'java/zone/moddev/mc/advantageworks/client'
foreach ($name in 'ClientResourceProbe','TankVisualProbe','OilRenderProbe','RecipeBookGroupingProbe') {
    $lines = & git -C $ReferenceRoot show "$referenceCommit`:test-rig/client-resources/$relative/$name.java"
    if ($LASTEXITCODE -ne 0) { throw "Missing frozen native probe: $name" }
    $source = ($lines -join "`n")
    $target = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$relative/$name.java") -Raw
    $expected = Canonical-Source $source
    $actual = Canonical-Source $target
    if ($name -eq 'ClientResourceProbe') {
        foreach ($assertion in [regex]::Matches($source, '(?s)\brequire\(.*?\);')) {
            Assert-Contract ($actual.Contains((Canonical-Source $assertion.Value))) "$name lost an original assertion: $($assertion.Value)"
        }
        foreach ($literal in [regex]::Matches($source, '"[^"\r\n]*(?::|\.png|\.bin|\.dat)[^"\r\n]*"')) {
            Assert-Contract ($actual.Contains((Canonical-Source $literal.Value))) "$name changed a resource or diagnostic literal: $($literal.Value)"
        }
    } else {
        Assert-Contract ($actual -ceq $expected) "$name changed its original native 1.13 fixtures, pixels, packet quantities or resource identities."
    }
}
$pack = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/pack.mcmeta') -Raw | ConvertFrom-Json
Assert-Contract ($pack.pack.pack_format -eq 4) 'The native 1.13 probe resource pack must keep format 4.'
$metadata = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/META-INF/mods.toml') -Raw
foreach ($pin in 'loaderVersion="[25,)"','versionRange="[2.4.4.113021,)"') {
    Assert-Contract ($metadata.Contains($pin)) "Missing original native probe metadata: $pin"
}
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
$failure = [regex]::Match($runner, '(?s)\} catch \{\s*# Preserve native partial results.*?\} finally \{').Value
Assert-Contract ($failure.Contains('runner-failure.json') -and $failure.Contains('if (-not (Test-Path')) 'Retain partial native results; do not replace them with a runner failure.'
Assert-Contract ($runner -notmatch '\b(?:Remove-Item|Clear-Content)\b') 'The runner must not delete old or failed evidence.'
Write-Output "PASS: $checks original native 1.13 probe preservation checks. Compilation and graphical acceptance NOT RUN."
