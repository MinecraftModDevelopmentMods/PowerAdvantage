param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA112/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
$relative = 'java/zone/moddev/mc/advantageworks/client/MachineGuiLayoutProbe.java'
$reference = Join-Path (Resolve-Path -LiteralPath $ReferenceRoot).Path $relative
Assert-Contract ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -eq 'AF4DCB18C1597FC000EB2DF9F11A27A2D11866CEBC14E7A89971486781F2D63A') 'Pinned native 1.12 GUI oracle changed.'
$expected = (Get-Content -LiteralPath $reference -Raw).Replace("`r`n", "`n")
$pairs = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/gui-native-mappings.json') -Raw | ConvertFrom-Json
foreach ($pair in $pairs) {
    Assert-Contract ($expected.Contains($pair[0])) "Mapping source is absent: $($pair[0])"
    $expected = $expected.Replace($pair[0], $pair[1])
}
$actual = (Get-Content -LiteralPath (Join-Path $PSScriptRoot $relative) -Raw).Replace("`r`n", "`n")
Assert-Contract ($expected.TrimEnd() -ceq $actual.TrimEnd()) 'GUI source changed beyond the declared native API mappings; assertions must not be weakened.'
$labels = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/gui-label-expectations.json') -Raw | ConvertFrom-Json
$original = Get-Content -LiteralPath (Join-Path $ReferenceRoot 'resources/gui-label-expectations.json') -Raw | ConvertFrom-Json
Assert-Contract (($labels | ConvertTo-Json -Depth 6 -Compress) -ceq ($original | ConvertTo-Json -Depth 6 -Compress)) 'Reviewed literal captions or layout rows changed.'
Assert-Contract (@($labels.machines.PSObject.Properties).Count -eq 39 -and $labels.inventory -ceq 'Inventory') 'All 39 equipment names and Inventory are required.'
$driver = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'java/zone/moddev/mc/advantageworks/client/ClientResourceProbe.java') -Raw
foreach ($literal in 'Minecraft.getMinecraft()', 'MachineGuiLayoutProbe.verify(minecraft, result);',
        'minecraft.world == null && minecraft.player == null && !minecraft.isIntegratedServerRunning()',
        'get("machine_gui_layout_count").getAsInt() == 39', 'get("machine_gui_text_checks").getAsInt() == 351',
        'get("machine_gui_negative_controls_rejected").getAsInt() == 355') {
    Assert-Contract ($driver.Contains($literal)) "Native client lost a required gate: $literal"
}
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
foreach ($literal in '$Report.machine_gui_text_spec -ne 2', '$Report.machine_gui_text_checks -ne 351',
        '$Report.machine_gui_negative_controls_rejected -ne 355', '$Report.machine_gui_layout_count -ne 39',
        '$row[0].tooltip_and_inventory_hitbox -ne ''PASS''', '$row[0].blank_label_controls_rejected -ne 9',
        '-Dadvantageworks.machineGuiLayouts=true', 'Candidate changed while snapshotting:',
        'PREFLIGHT_ONLY_NOT_CLIENT_EVIDENCE', 'runner-failure.json', '-WindowStyle Hidden') {
    Assert-Contract ($runner.Contains($literal)) "Runner lost a required native acceptance gate: $literal"
}
$tokens = $null; $errors = $null
[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'run.ps1'), [ref]$tokens, [ref]$errors) | Out-Null
Assert-Contract ($errors.Count -eq 0) 'Native GUI runner has PowerShell syntax errors.'
Write-Output "PASS: $checks native mapping/oracle/runner source checks. No build or native rendering performed."
