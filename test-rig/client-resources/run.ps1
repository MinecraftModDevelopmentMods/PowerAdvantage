param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$PowerJar,
    [Parameter(Mandatory = $true)][string]$SteamJar,
    [Parameter(Mandatory = $true)][string]$ElectricJar,
    [string]$ProbeJar,
    [string]$LaunchProfile = 'runClient.launch',
    [switch]$PreflightOnly,
    [ValidateRange(30, 1800)][int]$TimeoutSeconds = 300
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime = Join-Path $project ('run-client-resource-runs/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $runtime | Out-Null
$phase = 'launch-preflight'
$inputReport = [ordered]@{ minecraft = '1.10.2'; forge = '12.18.3.2511'; mappings = 'stable_29-1.10.2'; scope = 'MACHINE_GUI_ONLY'; qualification = 'UNQUALIFIED' }
function Get-Record([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    [ordered]@{ path = $file.FullName; bytes = $file.Length; sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
}
function Read-ModMetadata([string]$Path) {
    $jar = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $jar.GetEntry('mcmod.info')
        if (-not $entry) { return @() }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $json = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($json -match '\$\{[^}]+\}') { throw "Unexpanded mod metadata: $Path" }
        $metadata = $json | ConvertFrom-Json
        if ($metadata.modList) { return @($metadata.modList) }
        return @($metadata)
    } finally { $jar.Dispose() }
}
function Quote-Argument([string]$Value) {
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Assert-GuiReport($Report, [string]$Images) {
    if ($Report.status -ne 'PASS' -or $Report.scope -ne 'MACHINE_GUI_ONLY' -or
        $Report.minecraft -ne '1.10.2' -or $Report.forge -ne '12.18.3.2511' -or
        $Report.machine_gui_layout_status -ne 'PASS' -or $Report.machine_gui_text_status -ne 'PASS' -or
        $Report.machine_gui_text_spec -ne 2 -or $Report.machine_gui_layout_count -ne 39 -or
        $Report.machine_gui_text_checks -ne 351 -or $Report.machine_gui_negative_controls_rejected -ne 355) {
        throw 'Native GUI report is failed, incomplete, or from a different contract/engine.'
    }
    $expected = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/gui-label-expectations.json') -Raw | ConvertFrom-Json).machines
    $rows = @($Report.machine_gui_layouts)
    $ids = @($rows | ForEach-Object { $_.block })
    if ($rows.Count -ne 39 -or @($ids | Sort-Object -Unique).Count -ne 39 -or
        @($Report.advantage_mods | Sort-Object -Unique).Count -ne 3) { throw 'Missing or duplicate GUI rows/mods.' }
    foreach ($id in $expected.PSObject.Properties.Name) {
        $row = @($rows | Where-Object { $_.block -eq $id })
        if ($row.Count -ne 1 -or $row[0].status -ne 'PASS' -or $row[0].renders -ne 9 -or
            $row[0].required_text -ne 'PASS' -or $row[0].tooltip_and_inventory_hitbox -ne 'PASS' -or
            $row[0].blank_label_controls_rejected -ne 9 -or
            $row[0].expected_title -cne $expected.$id -or $row[0].menu_title -cne $expected.$id) {
            throw "Incomplete or incorrect GUI row: $id"
        }
        $stem = $id.Replace(':', '-')
        foreach ($viewport in @(@(320,240,1), @(426,240,2), @(640,360,1))) {
            foreach ($fill in 0..2) {
                $name = "$stem-$($viewport[0])x$($viewport[1])-scale$($viewport[2])-fill$fill.png"
                if (-not (Test-Path -LiteralPath (Join-Path $Images $name))) { throw "Missing native capture: $name" }
            }
        }
        if (-not (Test-Path -LiteralPath (Join-Path $Images "$stem-tooltip.png"))) { throw "Missing item-tooltip capture: $id" }
    }
    foreach ($name in 'negative-control-title_only.png','negative-control-inventory_only.png','negative-control-wrong_text.png',
            'negative-control-over_gauge.png','metal-tank-long-name.png','metal-tank-long-name-tooltip.png') {
        if (-not (Test-Path -LiteralPath (Join-Path $Images $name))) { throw "Missing control/custom-name capture: $name" }
    }
    if (@(Get-ChildItem -LiteralPath $Images -Filter '*.png' -File).Count -ne 396) { throw 'Expected exactly 396 native GUI images.' }
}
try {
    $java = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java) -or (Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw) -notmatch '(?m)^JAVA_VERSION="1\.8\.') {
        throw 'Supply a Java 8 runtime for the native 1.10 client.'
    }
    if (-not $ProbeJar) { $ProbeJar = Join-Path $project 'build/client-resource-probe/advantageclientresources-deobf.jar' }
    $launchPath = if ([IO.Path]::IsPathRooted($LaunchProfile)) { $LaunchProfile } else { Join-Path $project $LaunchProfile }
    [xml]$launch = Get-Content -LiteralPath $launchPath -Raw
    Copy-Item -LiteralPath $launchPath -Destination (Join-Path $runtime 'launch.xml')
    $attributes = @{}
    foreach ($attribute in $launch.launchConfiguration.stringAttribute) { $attributes[$attribute.key] = $attribute.value }
    $environment = @{}
    foreach ($map in $launch.launchConfiguration.mapAttribute) {
        if ($map.key -eq 'org.eclipse.debug.core.environmentVariables') {
            foreach ($entry in $map.mapEntry) { $environment[$entry.key] = $entry.value }
        }
    }
    if ($environment.MC_VERSION -ne '1.10.2' -or $environment.FORGE_VERSION -ne '12.18.3.2511' -or
        $environment.MCP_MAPPINGS -ne 'stable_29-1.10.2' -or
        $attributes['org.eclipse.jdt.launching.MAIN_TYPE'] -ne 'net.minecraftforge.launcher.Main' -or
        $attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'] -notmatch '--main net\.minecraftforge\.legacydev\.MainClient\b' -or
        $attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'] -match '--(?:server|port|gameDir|world)\b|1\.(?:12\.2|13\.2|14\.4|15\.2|16\.5)' -or
        $attributes['org.eclipse.jdt.launching.VM_ARGUMENTS'] -match '(?i)advantageworks\.|-javaagent:|--(?:server|port|gameDir|world)\b') {
        throw 'Regenerate a native 1.10.2 / Forge 12.18.3.2511 stable-29 client launch without injected probe arguments.'
    }
    $inputReport.java = Get-Record $java
    $inputReport.launch = Get-Record $launchPath
    $inputReport.runner = Get-Record $PSCommandPath
    $inputReport.expectations = Get-Record (Join-Path $PSScriptRoot 'resources/gui-label-expectations.json')
    New-Item -ItemType Directory -Path (Join-Path $runtime 'mods') | Out-Null
    $candidates = @{ poweradvantage = $PowerJar; steamadvantage = $SteamJar; electricadvantage = $ElectricJar; advantageclientresources = $ProbeJar }
    $candidateRecords = [ordered]@{}
    $versions = @{}
    foreach ($id in $candidates.Keys) {
        $source = (Resolve-Path -LiteralPath $candidates[$id]).Path
        $before = Get-Record $source
        $metadata = @(Read-ModMetadata $source | Where-Object { $_.modid -eq $id })
        if ($metadata.Count -ne 1 -or $metadata[0].mcversion -ne '1.10.2' -or -not $metadata[0].version) {
            throw "Missing/incorrect native metadata: $id"
        }
        $target = Join-Path $runtime "mods/$id.jar"
        Copy-Item -LiteralPath $source -Destination $target
        $record = Get-Record $target
        if ($record.sha256 -ne $before.sha256) { throw "Candidate changed while snapshotting: $source" }
        $record.origin_path = $source
        $record.modid = $id
        $record.version = [string]$metadata[0].version
        $candidateRecords[$id] = $record
        $versions[$id] = $record.version
    }
    $inputReport.candidates = $candidateRecords
    $classpath = @()
    $classpathRecords = @()
    $classpathNode = @($launch.launchConfiguration.listAttribute | Where-Object { $_.key -eq 'org.eclipse.jdt.launching.CLASSPATH' })
    foreach ($entry in $classpathNode.listEntry) {
        [xml]$cp = $entry.value
        $archive = $cp.runtimeClasspathEntry.externalArchive
        if (-not $archive) { continue }
        if ($archive -match '(?:forge|client-extra)-1\.(?:12\.2|13\.2|14\.4|15\.2|16\.5)|[\\/]PA11(?:2|3|4|5|6)[\\/]') { throw "Wrong-engine runtime archive: $archive" }
        if ([IO.Path]::GetExtension($archive) -eq '.jar') {
            $mods = @(Read-ModMetadata $archive)
            if (@($mods | Where-Object { $candidates.ContainsKey([string]$_.modid) }).Count -gt 0) { continue }
        }
        $classpath += $archive
        $classpathRecords += Get-Record $archive
    }
    if (-not ($classpath -match 'forge-1\.10\.2-12\.18\.3\.2511-stable-29-1\.10\.2\.jar$') -or
        -not ($classpath -match 'legacydev-.*\.jar$')) { throw 'Missing native mapped Forge/legacydev bootstrap.' }
    $inputReport.archives = $classpathRecords
    $arguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
    $vmArguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.VM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
    $vmArguments += '-Dadvantageworks.clientResources=true','-Dadvantageworks.requireExtensions=true','-Dadvantageworks.machineGuiLayouts=true','-Xmx2G'
    foreach ($id in 'poweradvantage','steamadvantage','electricadvantage') { $vmArguments += "-Dadvantageworks.expectedVersion.$id=$($versions[$id])" }
    $inputReport.environment = $environment
    $inputReport.program_arguments = $arguments
    $inputReport.vm_arguments = $vmArguments
    if ($PreflightOnly) { $inputReport.qualification = 'PREFLIGHT_ONLY_NOT_CLIENT_EVIDENCE' }
    $inputReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
    if ($PreflightOnly) { Write-Output "Native GUI preflight only; no client started. Evidence: $runtime"; return }
    $phase = 'native-client'
    $previous = @{}
    try {
        foreach ($key in $environment.Keys) {
            $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $environment[$key], 'Process')
        }
        $command = @($vmArguments) + @('-cp', ($classpath -join [IO.Path]::PathSeparator), $attributes['org.eclipse.jdt.launching.MAIN_TYPE']) + $arguments
        $process = Start-Process -FilePath $java -ArgumentList (($command | ForEach-Object { Quote-Argument $_ }) -join ' ') -WorkingDirectory $runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'launcher.stdout.log') -RedirectStandardError (Join-Path $runtime 'launcher.stderr.log')
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            & taskkill.exe /PID $process.Id /T /F | Out-Null
            throw "Native client timed out after $TimeoutSeconds seconds; failed evidence retained."
        }
        $process.WaitForExit()
        $process.Refresh()
        if ($process.ExitCode -ne 0) { throw "Native client exited $($process.ExitCode); evidence retained." }
    } finally {
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
    $phase = 'native-report'
    $report = Get-Content -LiteralPath (Join-Path $runtime 'client-resources.json') -Raw | ConvertFrom-Json
    Assert-GuiReport $report (Join-Path $runtime 'machine-gui-layouts')
    foreach ($id in 'poweradvantage','steamadvantage','electricadvantage') {
        if ($report.mod_versions.$id -cne $versions[$id]) { throw "Native loaded version mismatch: $id" }
    }
    foreach ($record in $candidateRecords.Values) {
        if ((Get-FileHash -LiteralPath $record.path -Algorithm SHA256).Hash -ne $record.sha256) { throw "Frozen runtime jar changed: $($record.path)" }
    }
    $inputReport.qualification = 'NATIVE_GUI_PASS'
    $inputReport.result = Get-Record (Join-Path $runtime 'client-resources.json')
    $inputReport.images = @(Get-ChildItem -LiteralPath (Join-Path $runtime 'machine-gui-layouts') -Filter '*.png' -File | Sort-Object Name | ForEach-Object { Get-Record $_.FullName })
    $inputReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
    Write-Output "Native GUI PASS: 39 screens, 351 caption cases, 355 rejected controls. Evidence: $runtime"
} catch {
    $inputReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
    [ordered]@{ status = 'FAIL'; phase = $phase; error = $_.Exception.Message } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'runner-failure.json') -Encoding utf8
    throw
}
