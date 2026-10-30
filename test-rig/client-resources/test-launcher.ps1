param([switch]$StaticOnly, [switch]$CrossVersionEvidenceRoots, [string]$WorkspaceRoot)
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
function Assert-GuiEvidenceRoot([Management.Automation.Language.ScriptBlockAst]$RunnerAst) {
    $assignments = @($RunnerAst.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] }, $true))
    $projects = @($assignments | Where-Object { $_.Left.Extent.Text -eq '$project' })
    $runtimes = @($assignments | Where-Object { $_.Left.Extent.Text -eq '$runtime' })
    if ($projects.Count -ne 1 -or $runtimes.Count -ne 1) { throw 'GUI evidence needs one unambiguous project/runtime assignment.' }
    $projectExpression = [regex]::Replace($projects[0].Right.Extent.Text, '\s+', '')
    if ($projectExpression -cne "(Resolve-Path(Join-Path`$PSScriptRoot'../..')).Path") {
        throw 'GUI evidence must be based at the runner project root, not a build directory.'
    }
    $runtimeExpression = [regex]::Replace($runtimes[0].Right.Extent.Text, '\s+', '')
    if (-not $runtimeExpression.StartsWith("Join-Path`$project('run-client-resource-runs/'+", [StringComparison]::Ordinal)) {
        throw 'GUI captures must use project/run-client-resource-runs, outside Gradle clean output.'
    }
    $variables = @($runtimes[0].Right.FindAll({ param($node) $node -is [Management.Automation.Language.VariableExpressionAst] }, $true))
    if (@($variables | Where-Object { $_.VariablePath.UserPath -ne 'project' }).Count) {
        throw 'GUI evidence suffix must not introduce a different root variable.'
    }
    $literals = @($runtimes[0].Right.FindAll({ param($node) $node -is [Management.Automation.Language.StringConstantExpressionAst] }, $true))
    foreach ($literal in $literals) {
        if ($literal.Value -ceq 'run-client-resource-runs/') { continue }
        if ($literal.Value -match '[\\/:]|\.\.') { throw 'GUI evidence suffix contains a path or traversal component.' }
    }
}

function Test-GuiEvidenceRootContract([string]$RunnerSource) {
    $rootTokens = $null; $rootErrors = $null
    $runnerAst = [Management.Automation.Language.Parser]::ParseInput($RunnerSource, [ref]$rootTokens, [ref]$rootErrors)
    if ($rootErrors.Count) { throw 'GUI evidence source must parse before its path is audited.' }
    Assert-GuiEvidenceRoot $runnerAst
    $script:checks++
    foreach ($replacement in "'build/client-resource-runs/'", "'run-client-resource-runs/../build/'", "'C:/build/client-resource-runs/'") {
        $broken = $RunnerSource.Replace("'run-client-resource-runs/'", $replacement)
        if ($broken -ceq $RunnerSource) { throw 'Evidence-root negative control did not mutate the source.' }
        $brokenAst = [Management.Automation.Language.Parser]::ParseInput($broken, [ref]$rootTokens, [ref]$rootErrors)
        if ($rootErrors.Count) { throw 'Evidence-root negative control must remain valid PowerShell.' }
        Assert-Throws { Assert-GuiEvidenceRoot $brokenAst }
    }
    $broken = $RunnerSource.Replace("'../..'", "'../../build'")
    if ($broken -ceq $RunnerSource) { throw 'Project-root negative control did not mutate the source.' }
    $brokenAst = [Management.Automation.Language.Parser]::ParseInput($broken, [ref]$rootTokens, [ref]$rootErrors)
    if ($rootErrors.Count) { throw 'Project-root negative control must remain valid PowerShell.' }
    Assert-Throws { Assert-GuiEvidenceRoot $brokenAst }
}

Test-GuiEvidenceRootContract (Get-Content -LiteralPath $script -Raw)

if ($CrossVersionEvidenceRoots) {
    $workspace = if ($WorkspaceRoot) { (Resolve-Path -LiteralPath $WorkspaceRoot).Path }
        else { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path }
    foreach ($line in 'PA110','PA112','PA113','PA114','PA115','PA116') {
        $runner = Join-Path $workspace ($line + '/PowerAdvantage/test-rig/client-resources/run.ps1')
        if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) { throw "Cross-version GUI contract requires all six runners; missing $runner" }
        Test-GuiEvidenceRootContract (Get-Content -LiteralPath $runner -Raw)
    }
    Write-Output 'PASS: all six GUI evidence roots are outside build; old-build, traversal, absolute-build and changed-project controls rejected.'
}

$native = @'
modLoader="javafml"
loaderVersion="[36,)" # native Forge
[[mods]] # mandatory header
modId="poweradvantage" # inline comment
version="2.4.7.116051" # native candidate
[[dependencies.poweradvantage]]
modId="minecraft"
mandatory=true
versionRange="[1.16.5]"
'@
Assert-NativeMetadata $native 'poweradvantage'
$checks++
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.7.116051','2.4.4.113021')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.7.116051','2.4.5.114041')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.7.116051','2.4.5.115021')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('[36,)','[31,)')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata ($native.Replace('2.4.7.116051','${version}')) 'poweradvantage' }
Assert-Throws { Assert-NativeMetadata $native 'electricadvantage' }
Assert-Throws { Assert-NativeMetadata '' 'poweradvantage' }
foreach ($id in 'steamadvantage','electricadvantage') {
    $expansion = $native.Replace('poweradvantage', $id).Replace('2.4.7.116051','2.2.8.116051')
    Assert-NativeMetadata $expansion $id
    $checks++
    Assert-Throws { Assert-NativeMetadata ($expansion.Replace('2.2.8.116051','2.2.6.115021')) $id }
}
$environment = @{ MC_VERSION = '1.16.5'; FORGE_VERSION = '36.2.34'; MCP_MAPPINGS = 'official_1.16.5'; MCP_VERSION = '20210115.111550'; target = 'fmluserdevclient' }
$attributes = @{ 'org.eclipse.jdt.launching.PROGRAM_ARGUMENTS' = '--main net.minecraftforge.userdev.LaunchTesting --cache D:\native\1.16.5-36.2.34 --'; 'org.eclipse.jdt.launching.MAIN_TYPE' = 'net.minecraftforge.launcher.Main' }
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
Assert-NativeArchive 'D:\native\forge-1.16.5-36.2.34.jar'
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
$power = Join-Path $project 'build/libs-dev/PowerAdvantage-2.4.7.116051-deobf.jar'
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
