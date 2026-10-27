param([switch]$StaticOnly)
$ErrorActionPreference = 'Stop'
$tokens = $null
$errors = $null
$script = Join-Path $PSScriptRoot 'run.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile($script, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw ($errors | Out-String) }
# Load helper definitions only. Never execute the runner or start Java here.
foreach ($definition in $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$checks = 1
function Assert-Throws([scriptblock]$Action) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    if (-not $threw) { throw 'Expected invalid native metadata to be rejected.' }
    $script:checks++
}
$native = @'
modLoader="javafml"
loaderVersion="[31,)" # native Forge
[[mods]] # mandatory header
modId="poweradvantage" # inline comment
version="2.4.6.115021" # native candidate
[[dependencies.poweradvantage]]
modId="minecraft"
mandatory=true
versionRange="[1.15.2]"
'@
Assert-NativeMetadata $native 'poweradvantage'
$checks++
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.6.115021','2.4.4.113021')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.6.115021','2.4.5.114041')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.6.115021','2.4.5.115021')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('[31,)','[28,)')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.6.115021','${version}')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata $native 'electricadvantage' }
Assert-Throws { Assert-NativeMetadata '' 'poweradvantage' }
foreach ($id in 'steamadvantage','electricadvantage') {
    $expansion = $native.Replace('poweradvantage', $id).Replace('2.4.6.115021','2.2.7.115021')
    Assert-NativeMetadata $expansion $id
    $checks++
    Assert-Throws { Assert-NativeMetadata ($expansion.Replace('2.2.7.115021','2.2.6.115021')) $id }
}
$environment = @{ MC_VERSION = '1.15.2'; FORGE_VERSION = '31.2.57'; MCP_MAPPINGS = 'snapshot_20200514-1.15.1'; MCP_VERSION = '20200515.085601'; target = 'fmluserdevclient' }
$attributes = @{ 'org.eclipse.jdt.launching.PROGRAM_ARGUMENTS' = '--main net.minecraftforge.userdev.LaunchTesting --cache D:\native\1.15.2-31.2.57 --'; 'org.eclipse.jdt.launching.MAIN_TYPE' = 'net.minecraftforge.launcher.Main' }
Assert-NativeLaunch $environment $attributes
$checks++
foreach ($key in 'MC_VERSION','FORGE_VERSION','MCP_MAPPINGS','MCP_VERSION','target') {
    $invalid = $environment.Clone()
    $invalid[$key] = 'legacy'
    Assert-Throws { Assert-NativeLaunch $invalid $attributes }
}
foreach ($argument in '--server localhost','--port 25565','--gameDir D:\protected','--world protected','--cache D:\native\1.14.4-28.2.26') {
    $invalid = $attributes.Clone()
    $invalid['org.eclipse.jdt.launching.PROGRAM_ARGUMENTS'] += ' ' + $argument
    Assert-Throws { Assert-NativeLaunch $environment $invalid }
}
$invalid = $attributes.Clone()
$invalid['org.eclipse.jdt.launching.MAIN_TYPE'] = 'net.minecraft.client.main.Main'
Assert-Throws { Assert-NativeLaunch $environment $invalid }
Assert-NativeArchive 'D:\native\forge-1.15.2-31.2.57-snapshot-20200514-1.15.1.jar'
$checks++
foreach ($path in 'D:\PA114\client.jar','D:\PA113\client.jar','D:\native\forge-1.14.4-28.2.26.jar','D:\native\client-extra-1.13.2.jar') {
    Assert-Throws { Assert-NativeArchive $path }
}
if ((Quote-Argument 'D:\native path\') -ne '"D:\native path\\"') { throw 'Trailing-backslash command-line quoting changed.' }
$checks++
if ((Quote-Argument 'text"quoted') -ne '"text\"quoted"') { throw 'Embedded-quote command-line quoting changed.' }
$checks++
$starts = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Start-Process' }, $true))
if ($starts.Count -ne 1 -or $starts[0].Extent.Text -notmatch '-WindowStyle Hidden') { throw 'Native helper must launch hidden.' }
$checks++
if ($StaticOnly) {
    Write-Output "PASS: $checks static launcher parser, target guards and quoting checks. Native candidate/launch checks NOT RUN. No Java/client/world was started."
    return
}
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$power = Join-Path $project 'build/libs-dev/PowerAdvantage-2.4.6.115021-deobf.jar'
Assert-NativeMetadata (Read-JarMetadata $power) 'poweradvantage'
$checks++
[xml]$launch = Get-Content -LiteralPath (Join-Path $project 'runClientAllAdvantage.launch') -Raw
$found = @{}
foreach ($entry in ($launch.launchConfiguration.listAttribute | Where-Object { $_.key -eq 'org.eclipse.jdt.launching.CLASSPATH' }).listEntry) {
    [xml]$classpath = $entry.value
    $path = $classpath.runtimeClasspathEntry.externalArchive
    if (-not $path -or [IO.Path]::GetExtension($path) -ne '.jar') { continue }
    $metadata = Read-JarMetadata $path
    $header = if ($metadata) { [regex]::Match($metadata, '(?ms)^\[\[mods\]\]\s*(.*?)(?=^\[|\z)').Groups[1].Value }
    foreach ($id in 'basemetals','orespawn','steamadvantage','electricadvantage') {
        if ($header -match ('(?m)^modId\s*=\s*"' + $id + '"[ \t]*(?:#[^\r\n]*)?\r?$')) {
            Assert-NativeMetadata $metadata $id
            $found[$id] = $true
            $checks++
        }
    }
}
if ($found.Count -ne 4) { throw 'The actual combined launch must expose all four native dependency/expansion candidates.' }
Write-Output "PASS: $checks launcher parser, metadata, native input and quoting checks. No Java/client/world was started."
