$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$reference = (Resolve-Path (Join-Path $PSScriptRoot '../../../../PA114/PowerAdvantage/test-rig/client-resources')).Path
$output = Join-Path $project ('build/client-probe-negative-controls/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$relative = 'java/zone/moddev/mc/advantageworks/client'
$controls = @(
    @{ name='recipe-pixel-threshold'; file='RecipeBookGroupingProbe'; before='colored > 1000'; after='colored > 100' },
    @{ name='tank-packet-quantity'; file='TankVisualProbe'; before='clientTank.getTank().getFluidAmount() == 10000'; after='clientTank.getTank().getFluidAmount() == 1000' },
    @{ name='bucket-volume'; file='OilRenderProbe'; before='contents.getAmount() != 1000'; after='contents.getAmount() != 100' },
    @{ name='missing-model'; file='ClientResourceProbe'; before='require(model != missing,'; after='require(model == missing,' }
)
$results = @()
foreach ($control in $controls) {
    $fixture = Join-Path $output $control.name
    Copy-Item -LiteralPath $PSScriptRoot -Destination $fixture -Recurse
    $path = Join-Path $fixture "$relative/$($control.file).java"
    $source = [IO.File]::ReadAllText($path)
    if (-not $source.Contains($control.before)) { throw "Negative control has no exact mutation point: $($control.name)" }
    [IO.File]::WriteAllText($path, $source.Replace($control.before, $control.after), [Text.UTF8Encoding]::new($false))
    $rejected = $false
    $failure = $null
    try { & (Join-Path $fixture 'test-probe-contract.ps1') -ReferenceRoot $reference | Out-Null }
    catch { $rejected = $true; $failure = $_.Exception.Message }
    $results += [ordered]@{ control=$control.name; expected='REJECT'; rejected=$rejected; error=$failure }
    if (-not $rejected) { throw "A weakened oracle was accepted: $($control.name). Evidence: $output" }
}
$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'results.json') -Encoding utf8
Write-Output "PASS: $($controls.Count) weakened-oracle controls rejected. Original source and worlds were not changed. Evidence: $output"
