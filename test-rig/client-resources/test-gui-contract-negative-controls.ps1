param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA116/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$reference = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$output = Join-Path $project ('build/client-gui-negative-controls/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$gui = 'java/zone/moddev/mc/advantageworks/client/MachineGuiLayoutProbe.java'
$controls = @(
    @{ name='glyph-count'; file=$gui; before='ink < 20'; after='ink < 0' },
    @{ name='glyph-mismatch'; file=$gui; before='missing != 0'; after='missing > 100' },
    @{ name='missing-label-control'; file=$gui; before='require(labelError(background, expected, background, screen, viewport[2]) != null,'; after='require(labelError(background, expected, background, screen, viewport[2]) == null,' },
    @{ name='inventory-hitbox'; file=$gui; before='158 + (i / 9) * 18'; after='140 + (i / 9) * 18' },
    @{ name='required-caption-version'; file='run.ps1'; before='$result.machine_gui_text_spec -ne 2'; after='$result.machine_gui_text_spec -ne 1' },
    @{ name='incomplete-caption-cases'; file='run.ps1'; before='if ($RequireExtensions) { 351 } else { 126 }'; after='if ($RequireExtensions) { 1 } else { 1 }' },
    @{ name='skipped-native-probe'; file='java/zone/moddev/mc/advantageworks/client/ClientResourceProbe.java'; before='MachineGuiLayoutProbe.verify(minecraft, result);'; after='' },
    @{ name='wrong-machine-caption'; file='resources/gui-label-expectations.json'; before='Metal Fluid Tank'; after='Machine' }
)
$results = @()
foreach ($control in $controls) {
    $fixture = Join-Path $output $control.name
    Copy-Item -LiteralPath $PSScriptRoot -Destination $fixture -Recurse
    $path = Join-Path $fixture $control.file
    $source = [IO.File]::ReadAllText($path)
    if (-not $source.Contains($control.before)) { throw "Negative control has no exact mutation point: $($control.name)" }
    [IO.File]::WriteAllText($path, $source.Replace($control.before, $control.after), [Text.UTF8Encoding]::new($false))
    $rejected = $false
    $failure = $null
    try { & (Join-Path $fixture 'test-gui-contract.ps1') -ReferenceRoot $reference | Out-Null }
    catch { $rejected = $true; $failure = $_.Exception.Message }
    $results += [ordered]@{ control=$control.name; expected='REJECT'; rejected=$rejected; error=$failure }
    if (-not $rejected) { throw "A weakened GUI oracle was accepted: $($control.name). Evidence: $output" }
}
$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'results.json') -Encoding utf8
Write-Output "PASS: $($controls.Count) weakened GUI oracles rejected. Original source and worlds unchanged. Evidence: $output"
