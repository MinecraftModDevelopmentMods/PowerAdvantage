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
loaderVersion="[28,)" # native Forge
[[mods]] # mandatory header
modId="poweradvantage" # inline comment
version="2.4.5.114041" # native candidate
[[dependencies.poweradvantage]]
modId="minecraft"
mandatory=true
versionRange="[1.14.4]"
'@
Assert-NativeMetadata $native 'poweradvantage'
$checks++
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.5.114041','2.4.4.113021')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('[28,)','[25,)')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.5.114041','${version}')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata $native 'electricadvantage' }
Assert-Throws { Assert-NativeMetadata '' 'poweradvantage' }
if ((Quote-Argument 'D:\native path\') -ne '"D:\native path\\"') { throw 'Trailing-backslash command-line quoting changed.' }
$checks++
if ((Quote-Argument 'text"quoted') -ne '"text\"quoted"') { throw 'Embedded-quote command-line quoting changed.' }
$checks++
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$power = Join-Path $project 'build/libs-dev/PowerAdvantage-2.4.5.114041-deobf.jar'
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
$starts = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Start-Process' }, $true))
if ($starts.Count -ne 1 -or $starts[0].Extent.Text -notmatch '-WindowStyle Hidden') { throw 'Native helper must launch hidden.' }
$checks++
Write-Output "PASS: $checks launcher parser, metadata, native input and quoting checks. No Java/client/world was started."
