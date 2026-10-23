param([Parameter(Mandatory = $true)][string]$JavaHome,
      [ValidateSet('runClient.launch', 'runClientAllAdvantage.launch')][string]$LaunchProfile = 'runClient.launch',
      [ValidateSet('Gradle', 'Eclipse')][string]$OutputSource = 'Gradle',
      [switch]$RequireExtensions,
      [string]$RecipeBookFixtures)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw "Java 8 runtime not found: $java" }
$probe = Join-Path $project 'build/client-resource-probe/advantageclientresources-deobf.jar'
if (-not (Test-Path -LiteralPath $probe)) { throw 'Run gradlew.bat clientResourceProbeJar verifyEclipseProductionClasspath first.' }
[xml]$launch = Get-Content -LiteralPath (Join-Path $project $LaunchProfile) -Raw
$attributes = @{}
foreach ($attribute in $launch.launchConfiguration.stringAttribute) { $attributes[$attribute.key] = $attribute.value }
$runtime = Join-Path $project ('build/client-resource-runs/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$modOutput = Join-Path $runtime 'poweradvantage'
New-Item -ItemType Directory -Path $modOutput,(Join-Path $runtime 'mods') -Force | Out-Null
if ($OutputSource -eq 'Eclipse') {
    Copy-Item -Path (Join-Path $project 'bin/main/*') -Destination $modOutput -Recurse
} else {
    Copy-Item -Path (Join-Path $project 'build/classes/java/main/*') -Destination $modOutput -Recurse
    Copy-Item -Path (Join-Path $project 'build/resources/main/*') -Destination $modOutput -Recurse -Force
}
$metadataPath = Join-Path $modOutput 'META-INF/mods.toml'
$inputReport = [ordered]@{ output_source = $OutputSource; launch_profile = $LaunchProfile; require_extensions = [bool]$RequireExtensions }
if (Test-Path -LiteralPath $metadataPath) { $inputReport.metadata_sha256 = (Get-FileHash -LiteralPath $metadataPath).Hash }
$inputReport | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'client-input.json') -Encoding utf8
if (-not (Test-Path -LiteralPath $metadataPath) -or (Get-Content -LiteralPath $metadataPath -Raw) -match '\$\{[^}]+\}') {
    $failure = "The $OutputSource output contains missing or unexpanded mod metadata; inspect $runtime. Refresh Gradle/Eclipse before launching."
    [ordered]@{ status = 'FAIL'; phase = 'launch-preflight'; error = $failure } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $runtime 'client-resources.json') -Encoding utf8
    throw $failure
}
Copy-Item -LiteralPath $probe -Destination (Join-Path $runtime 'mods/advantageclientresources.jar')
if ($RecipeBookFixtures) {
    $fixtures = (Resolve-Path -LiteralPath $RecipeBookFixtures).Path
    $destination = Join-Path $runtime 'recipe-book-fixtures'
    New-Item -ItemType Directory -Path $destination | Out-Null
    $packets = @(Get-ChildItem -LiteralPath $fixtures -Filter 'recipes-*-sweep.bin')
    if ($packets.Count -ne 3) { throw 'Recipe grouping requires Normal, Tech and Apocalyptic sweep packets.' }
    foreach ($packet in $packets) {
        Copy-Item -LiteralPath $packet.FullName -Destination $destination
        Copy-Item -LiteralPath ([IO.Path]::ChangeExtension($packet.FullName, '.json')) -Destination $destination
    }
    $registrySource = Join-Path $fixtures 'AdvantageWorksMigration/level.dat'
    Copy-Item -LiteralPath $registrySource -Destination (Join-Path $destination 'server-registries.dat')
}
$archives = foreach ($entry in $launch.launchConfiguration.listAttribute.listEntry) {
    [xml]$classpathEntry = $entry.value
    if ($classpathEntry.runtimeClasspathEntry.externalArchive) { $classpathEntry.runtimeClasspathEntry.externalArchive }
}
$arguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
$vmArguments = @([regex]::Matches($attributes['org.eclipse.jdt.launching.VM_ARGUMENTS'], '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
if ($RequireExtensions) { $vmArguments += '-Dadvantageworks.requireExtensions=true' }
$previous = @{}
try {
    foreach ($entry in $launch.launchConfiguration.mapAttribute.mapEntry) {
        $previous[$entry.key] = [Environment]::GetEnvironmentVariable($entry.key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.key, $entry.value, 'Process')
    }
    $env:MOD_CLASSES = "poweradvantage%%$modOutput;poweradvantage%%$modOutput"
    Push-Location -LiteralPath $runtime
    try {
        & $java @vmArguments '-Dadvantageworks.clientResources=true' '-Xmx2G' '-cp' ((@($modOutput) + $archives) -join [IO.Path]::PathSeparator) $attributes['org.eclipse.jdt.launching.MAIN_TYPE'] @arguments
        if ($LASTEXITCODE -ne 0) { throw "Client exited with code $LASTEXITCODE; inspect $runtime/logs" }
    } finally { Pop-Location }
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
$resultPath = Join-Path $runtime 'client-resources.json'
if (-not (Test-Path -LiteralPath $resultPath)) { throw "Client did not produce a result; inspect $runtime/logs" }
$result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
if ($result.status -ne 'PASS') { throw "Client resource check failed: $($result.error); inspect $runtime" }
$log = Get-Content -LiteralPath (Join-Path $runtime 'logs/latest.log') -Raw
$namespaces = if ($RequireExtensions) { '(?:poweradvantage|steamadvantage|electricadvantage)' } else { 'poweradvantage' }
if ($log -match "Unable to load model:.*${namespaces}:|Exception loading blockstate definition:.*${namespaces}:|\[FML\.TEXTURE_ERRORS/\]") {
    throw "Client logged a model or texture error; inspect $runtime/logs/latest.log"
}
Write-Output "PASS: $($result.items) items, $($result.block_states) block states, $($result.creative_entries) creative entries. Evidence: $runtime"
