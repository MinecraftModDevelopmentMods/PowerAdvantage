param([Parameter(Mandatory = $true)][string]$JavaHome,
      [ValidateSet('runClient.launch', 'runClientAllAdvantage.launch')][string]$LaunchProfile = 'runClient.launch',
      [ValidateSet('Gradle', 'Eclipse')][string]$OutputSource = 'Gradle',
      [switch]$RequireExtensions,
      [string]$RecipeBookFixtures,
      [string]$PowerCandidate,
      [switch]$PreflightOnly,
      [ValidateRange(30, 1800)][int]$TimeoutSeconds = 300)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$java = Join-Path $JavaHome 'bin/java.exe'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-Record([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    [ordered]@{ path = $file.FullName; bytes = $file.Length; sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
}
function Read-JarMetadata([string]$Path) {
    $jar = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $jar.GetEntry('META-INF/mods.toml')
        if (-not $entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $jar.Dispose() }
}
function Assert-NativeMetadata([string]$Metadata, [string]$Id) {
    if (-not $Metadata -or $Metadata -match '\$\{[^}]+\}') { throw "Missing/unexpanded metadata for $Id" }
    # Match the known single-mod header, not dependency modId/versionRange fields.
    $header = [regex]::Match($Metadata, '(?ms)^\[\[mods\]\]\s*(.*?)(?=^\[|\z)').Groups[1].Value
    if ($header -notmatch ('(?m)^modId\s*=\s*"' + [regex]::Escape($Id) + '"[ \t]*(?:#[^\r\n]*)?\r?$') -or
        $header -notmatch '(?m)^version\s*=\s*"[^"\r\n]*\.114\d+"[ \t]*(?:#[^\r\n]*)?\r?$' -or
        $Metadata -notmatch '(?m)^loaderVersion\s*=\s*"\[28,') { throw "Not a native Forge 28 candidate: $Id" }
}
function Quote-Argument([string]$Value) {
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
$probe = Join-Path $project 'build/client-resource-probe/advantageclientresources-deobf.jar'
$runtime = Join-Path $project ('build/client-resource-runs/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$inputReport = [ordered]@{ minecraft = '1.14.4'; forge = '28.2.26'; qualification = 'UNQUALIFIED'; output_source = $OutputSource; launch_profile = $LaunchProfile; require_extensions = [bool]$RequireExtensions }
$phase = 'launch-preflight'
New-Item -ItemType Directory -Path $runtime | Out-Null
try {
if (-not (Test-Path -LiteralPath $java)) { throw "Java 8 runtime not found: $java" }
if ((Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw) -notmatch '(?m)^JAVA_VERSION="1\.8\.') { throw 'Supply Java 8 for the native client.' }
if (-not (Test-Path -LiteralPath $probe)) { throw 'Run gradlew.bat clientResourceProbeJar verifyEclipseProductionClasspath first.' }
[xml]$launch = Get-Content -LiteralPath (Join-Path $project $LaunchProfile) -Raw
$attributes = @{}
foreach ($attribute in $launch.launchConfiguration.stringAttribute) { $attributes[$attribute.key] = $attribute.value }
$inputReport.java = Get-Record $java
$inputReport.launch = Get-Record (Join-Path $project $LaunchProfile)
$inputReport.probe = Get-Record $probe
$probeMetadata = Read-JarMetadata $probe
if ($probeMetadata -notmatch 'loaderVersion="\[28,29\)"' -or $probeMetadata -notmatch 'versionRange="\[1\.14\.4\]"') { throw 'Rebuild the native 1.14 probe; do not substitute the 1.13 probe.' }
Copy-Item -LiteralPath (Join-Path $project $LaunchProfile) -Destination (Join-Path $runtime 'launch.xml')
$environment = @{}
foreach ($map in $launch.launchConfiguration.mapAttribute) {
    if ($map.key -eq 'org.eclipse.debug.core.environmentVariables') {
        foreach ($entry in $map.mapEntry) { $environment[$entry.key] = $entry.value }
    }
}
if ($environment.MC_VERSION -ne '1.14.4' -or $environment.FORGE_VERSION -ne '28.2.26' -or $environment.target -ne 'fmluserdevclient') { throw 'Regenerate the native 1.14.4 / Forge 28.2.26 client launch.' }
if ($attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'] -notmatch '1\.14\.4-28\.2\.26' -or
    $attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'] -match '1\.13\.2|--(?:server|port|gameDir|world)\b' -or
    $attributes['org.eclipse.jdt.launching.MAIN_TYPE'] -ne 'net.minecraftforge.launcher.Main') { throw 'Unexpected native launch arguments; no server connection or existing game directory is allowed.' }
$modOutput = Join-Path $runtime 'poweradvantage'
New-Item -ItemType Directory -Path $modOutput,(Join-Path $runtime 'mods') -Force | Out-Null
if ($OutputSource -eq 'Eclipse') {
    Copy-Item -Path (Join-Path $project 'bin/main/*') -Destination $modOutput -Recurse
} else {
    Copy-Item -Path (Join-Path $project 'build/classes/java/main/*') -Destination $modOutput -Recurse
    Copy-Item -Path (Join-Path $project 'build/resources/main/*') -Destination $modOutput -Recurse -Force
}
$metadataPath = Join-Path $modOutput 'META-INF/mods.toml'
if (Test-Path -LiteralPath $metadataPath) { $inputReport.metadata_sha256 = (Get-FileHash -LiteralPath $metadataPath).Hash }
$inputReport | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
if (-not (Test-Path -LiteralPath $metadataPath) -or (Get-Content -LiteralPath $metadataPath -Raw) -match '\$\{[^}]+\}') {
    $failure = "The $OutputSource output contains missing or unexpanded mod metadata; inspect $runtime. Refresh Gradle/Eclipse before launching."
    [ordered]@{ status = 'FAIL'; phase = 'launch-preflight'; error = $failure } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $runtime 'client-resources.json') -Encoding utf8
    throw $failure
}
Assert-NativeMetadata (Get-Content -LiteralPath $metadataPath -Raw) 'poweradvantage'
$outputFiles = @(Get-ChildItem -LiteralPath $modOutput -Recurse -File | Sort-Object FullName | ForEach-Object { Get-Record $_.FullName })
if (@($outputFiles | Where-Object { $_.path -like '*.class' }).Count -eq 0) { throw 'Selected output has no compiled production classes.' }
$outputFiles | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $runtime 'production-files.json') -Encoding utf8
$inputReport.production_files = Get-Record (Join-Path $runtime 'production-files.json')
if (-not $PowerCandidate) { $PowerCandidate = Join-Path $project 'build/libs-dev/PowerAdvantage-2.4.5.114041-deobf.jar' }
$PowerCandidate = (Resolve-Path -LiteralPath $PowerCandidate).Path
Assert-NativeMetadata (Read-JarMetadata $PowerCandidate) 'poweradvantage'
$inputReport.power_candidate = Get-Record $PowerCandidate
$inputReport.candidate_note = 'Jar provenance only; runtime uses the separately hashed selected production classes/resources.'
Copy-Item -LiteralPath $probe -Destination (Join-Path $runtime 'mods/advantageclientresources.jar')
if ((Get-FileHash -LiteralPath (Join-Path $runtime 'mods/advantageclientresources.jar')).Hash -ne $inputReport.probe.sha256) { throw 'Probe changed during snapshot.' }
if ($RecipeBookFixtures) {
    if (-not $RequireExtensions) { throw 'The thirteen recipe-cell rendering assertions require the combined profile.' }
    $fixtures = (Resolve-Path -LiteralPath $RecipeBookFixtures).Path
    $destination = Join-Path $runtime 'recipe-book-fixtures'
    New-Item -ItemType Directory -Path $destination | Out-Null
    $packets = @(Get-ChildItem -LiteralPath $fixtures -Filter 'recipes-*-sweep.bin')
    if ($packets.Count -ne 3) { throw 'Recipe grouping requires Normal, Tech and Apocalyptic sweep packets.' }
    foreach ($mode in 'normal','tech','apocalyptic') {
        if (-not (Test-Path -LiteralPath (Join-Path $fixtures "recipes-$mode-sweep.bin"))) { throw "Missing $mode recipe packet." }
    }
    $fixtureRecords = @()
    foreach ($packet in $packets) {
        $fixtureRecords += Get-Record $packet.FullName
        $fixtureRecords += Get-Record ([IO.Path]::ChangeExtension($packet.FullName, '.json'))
        Copy-Item -LiteralPath $packet.FullName -Destination $destination
        Copy-Item -LiteralPath ([IO.Path]::ChangeExtension($packet.FullName, '.json')) -Destination $destination
    }
    $registrySource = Join-Path $fixtures 'AdvantageWorksMigration/level.dat'
    $fixtureRecords += Get-Record $registrySource
    $inputReport.recipe_fixtures = $fixtureRecords
    Copy-Item -LiteralPath $registrySource -Destination (Join-Path $destination 'server-registries.dat')
}
$classpathNode = @($launch.launchConfiguration.listAttribute | Where-Object { $_.key -eq 'org.eclipse.jdt.launching.CLASSPATH' })
$archives = @(foreach ($entry in $classpathNode.listEntry) {
    [xml]$classpathEntry = $entry.value
    if ($classpathEntry.runtimeClasspathEntry.externalArchive) { $classpathEntry.runtimeClasspathEntry.externalArchive }
})
$archiveRecords = @()
$runtimeCandidates = @{}
foreach ($archive in $archives) {
    if ($archive -match '(?:forge|client-extra)-1\.13\.2|[\\/]PA113[\\/]') { throw "Legacy runtime archive: $archive" }
    $record = Get-Record $archive
    $archiveRecords += $record
    $metadata = if ([IO.Path]::GetExtension($archive) -eq '.jar') { Read-JarMetadata $archive }
    $modHeader = if ($metadata) { [regex]::Match($metadata, '(?ms)^\[\[mods\]\]\s*(.*?)(?=^\[|\z)').Groups[1].Value }
    foreach ($id in 'poweradvantage','steamadvantage','electricadvantage','basemetals','orespawn') {
        if ($modHeader -match ('(?m)^modId\s*=\s*"' + $id + '"[ \t]*(?:#[^\r\n]*)?\r?$')) {
            Assert-NativeMetadata $metadata $id
            if ($id -eq 'poweradvantage' -or $runtimeCandidates.ContainsKey($id)) { throw "Duplicate runtime candidate: $id" }
            $runtimeCandidates[$id] = $record
        }
    }
}
$inputReport.archives = $archiveRecords
$inputReport.runtime_candidates = $runtimeCandidates
if (-not ($archives -match 'forge-1\.14\.4-28\.2\.26-snapshot-20190719-1\.14\.3\.jar$')) { throw 'Missing actual native mapped Forge runtime.' }
if ($RequireExtensions -and (-not $runtimeCandidates.ContainsKey('steamadvantage') -or -not $runtimeCandidates.ContainsKey('electricadvantage'))) { throw 'Combined launch must explicitly contain both native expansion candidates.' }
$arguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
$vmArguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.VM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
if ($RequireExtensions) { $vmArguments += '-Dadvantageworks.requireExtensions=true' }
$environment.MOD_CLASSES = "poweradvantage%%$modOutput;poweradvantage%%$modOutput"
$inputReport.environment = $environment
$inputReport.program_arguments = $arguments
$inputReport.vm_arguments = $vmArguments
$inputReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
if ($PreflightOnly) {
    $inputReport.qualification = 'PREFLIGHT_ONLY_NOT_CLIENT_EVIDENCE'
    Write-Output "Native launch preflight passed; no client was started. Evidence: $runtime"
    return
}
$previous = @{}
$phase = 'native-client'
try {
    foreach ($key in $environment.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environment[$key], 'Process')
    }
    $command = @($vmArguments) + @('-Dadvantageworks.clientResources=true', '-Xmx2G', '-cp', ((@($modOutput) + $archives) -join [IO.Path]::PathSeparator), $attributes['org.eclipse.jdt.launching.MAIN_TYPE']) + $arguments
    $process = Start-Process -FilePath $java -ArgumentList (($command | ForEach-Object { Quote-Argument $_ }) -join ' ') -WorkingDirectory $runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'launcher.stdout.log') -RedirectStandardError (Join-Path $runtime 'launcher.stderr.log')
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
        throw "Native client timed out after $TimeoutSeconds seconds; evidence retained."
    }
    $process.WaitForExit()
    $process.Refresh()
    if ($process.ExitCode -ne 0) { throw "Client exited with code $($process.ExitCode); inspect $runtime/logs" }
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
$resultPath = Join-Path $runtime 'client-resources.json'
if (-not (Test-Path -LiteralPath $resultPath)) { throw "Client did not produce a result; inspect $runtime/logs" }
$result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
if ($result.status -ne 'PASS') { throw "Client resource check failed: $($result.error); inspect $runtime" }
if ($result.minecraft -ne '1.14.4' -or $result.forge -ne '28.2.26') { throw 'Result is not native 1.14.4 / Forge 28.2.26 evidence.' }
foreach ($record in $archiveRecords) {
    if ((Get-FileHash -LiteralPath $record.path).Hash -ne $record.sha256) { throw "Dependency changed during the run: $($record.path)" }
}
$log = Get-Content -LiteralPath (Join-Path $runtime 'logs/latest.log') -Raw
$namespaces = if ($RequireExtensions) { '(?:poweradvantage|steamadvantage|electricadvantage)' } else { 'poweradvantage' }
if ($log -match "Unable to load model:.*${namespaces}:|Exception loading blockstate definition:.*${namespaces}:|\[FML\.TEXTURE_ERRORS/\]") {
    throw "Client logged a model or texture error; inspect $runtime/logs/latest.log"
}
Write-Output "PASS: $($result.items) items, $($result.block_states) block states, $($result.creative_entries) creative entries. Evidence: $runtime"
$inputReport.qualification = 'NATIVE_CLIENT_RESOURCE_PASS'
} catch {
    # Preserve native partial results and all images; record launcher failures separately.
    [ordered]@{ status = 'FAIL'; phase = $phase; error = $_.Exception.Message } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $runtime 'runner-failure.json') -Encoding utf8
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'client-resources.json'))) {
        [ordered]@{ status = 'FAIL'; phase = $phase; error = $_.Exception.Message } | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $runtime 'client-resources.json') -Encoding utf8
    }
    throw "Client resource check failed; inspect $runtime. $($_.Exception.Message)"
} finally {
    $inputReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
}
