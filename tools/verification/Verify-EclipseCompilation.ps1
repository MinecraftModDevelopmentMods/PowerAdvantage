param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [Parameter(Mandatory = $true)][string]$Evidence,
    [Parameter(Mandatory = $true)][string]$Compiler,
    [Parameter(Mandatory = $true)][string]$Java17Home,
    [Parameter(Mandatory = $true)][string]$Java8Home,
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$Evidence = [IO.Path]::GetFullPath($Evidence)
if (Test-Path -LiteralPath $Evidence) { throw 'Refusing to overwrite compiler evidence' }
$null = New-Item -ItemType Directory -Path $Evidence
$java17 = Join-Path $Java17Home 'bin/java.exe'
$java8 = Join-Path $Java8Home 'bin/java.exe'
if ((Get-Content -LiteralPath (Join-Path $Java8Home 'release') -Raw) -notmatch '(?m)^JAVA_VERSION="1\.8\.') {
    throw 'Supply a Java 8 boot library and test runtime'
}

# The JRE container in Eclipse restricts these packages; plain ECJ does not.
$accessRules = '[-sun/**;-com/sun/**;-jdk/internal/**]'
$boot = @(Get-ChildItem -LiteralPath (Join-Path $Java8Home 'jre/lib') -File -Filter '*.jar' |
    Sort-Object Name | ForEach-Object { $_.FullName.Replace('\', '/') + $accessRules })
if (-not ($boot | Where-Object { $_ -match '/rt\.jar\[' })) { throw 'Missing Java 8 rt.jar' }
[xml]$classpath = Get-Content -LiteralPath (Join-Path $ProjectRoot '.classpath') -Raw
$libraries = @()
$scopedLibraries = @{ main=@(); test=@() }
foreach ($entry in $classpath.SelectNodes('/classpath/classpathentry[@kind="lib"]')) {
    $path = [string]$entry.path
    if (-not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $ProjectRoot $path }
    $libraries += $path
    $scopeNode = $entry.SelectSingleNode('attributes/attribute[@name="gradle_used_by_scope"]/@value')
    $scope = if ($scopeNode) { $scopeNode.Value } else { '' }
    foreach ($set in @('main', 'test')) {
        if (-not $scope -or $scope.Split(',') -contains $set) { $scopedLibraries[$set] += $path }
    }
}
if (-not $libraries.Count) {
    throw 'Run gradlew.bat eclipseClasspath first; the live Buildship container cannot be resolved by standalone ECJ'
}
foreach ($library in $libraries) {
    if (-not (Test-Path -LiteralPath $library)) { throw "Missing Eclipse library: $library" }
}
$outputs = @{}
$report = [ordered]@{
    status='RUNNING'; project=$ProjectRoot; compilerSha256=(Get-FileHash -LiteralPath $Compiler).Hash
    java8Sha256=(Get-FileHash -LiteralPath $java8).Hash; bootAccessRules=$accessRules
    restrictedReferences='ERROR'; compilations=[ordered]@{}; junit=$null
}
try {
    foreach ($set in @('main', 'test')) {
        $output = Join-Path $Evidence $set
        $null = New-Item -ItemType Directory -Path $output
        $outputs[$set] = $output
        $sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src/$set/java") -Recurse -File -Filter '*.java')
        $cp = @($scopedLibraries[$set])
        if ($set -eq 'test') { $cp += @($outputs.main, (Join-Path $ProjectRoot 'build/resources/main')) }
        $arguments = @('-1.8', '-proc:none', '-encoding', 'UTF-8', '-err:+forbidden,discouraged',
            '-bootclasspath', ('"' + ($boot -join ';') + '"'),
            '-classpath', ('"' + ($cp -join ';') + '"'), '-d', ('"' + $output + '"'),
            '-log', ('"' + (Join-Path $Evidence "$set.xml") + '"'))
        $arguments += @($sources | ForEach-Object { '"' + $_.FullName + '"' })
        $argFile = Join-Path $Evidence "$set.args"
        [IO.File]::WriteAllLines($argFile, $arguments, [Text.UTF8Encoding]::new($false))
        & $java17 -Xmx2G -jar $Compiler "@$argFile" *> (Join-Path $Evidence "$set-compile.log")
        $code = $LASTEXITCODE
        $report.compilations[$set] = @{ exitCode=$code; sourceCount=$sources.Count }
        if ($code -ne 0) { throw "$set ECJ compilation failed; see retained diagnostics" }
        foreach ($file in Get-ChildItem -LiteralPath $output -Recurse -File -Filter '*.class') {
            $bytes = [IO.File]::ReadAllBytes($file.FullName)
            if (($bytes[6] * 256 + $bytes[7]) -ne 52) { throw "Not Java 8 bytecode: $file" }
        }
    }
    if (-not $CompileOnly) {
        $classes = @()
        $expected = 0
        foreach ($file in Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'build/test-results/test') -Filter 'TEST-*.xml') {
            [xml]$suite = Get-Content -LiteralPath $file.FullName -Raw
            if ([int]$suite.testsuite.errors -or [int]$suite.testsuite.failures -or [int]$suite.testsuite.skipped) {
                throw 'Gradle suite inventory contains failed or skipped tests'
            }
            $classes += [string]$suite.testsuite.name
            $expected += [int]$suite.testsuite.tests
        }
        if (-not $expected) { throw 'Missing Gradle test inventory; run test first' }
        $cp = @($outputs.main, $outputs.test, (Join-Path $ProjectRoot 'build/resources/main'),
            (Join-Path $ProjectRoot 'build/resources/test')) + $scopedLibraries.test
        Push-Location $ProjectRoot
        try {
            & $java8 -Xmx2G -cp ($cp -join ';') org.junit.runner.JUnitCore @classes *> (Join-Path $Evidence 'junit.log')
            $code = $LASTEXITCODE
        } finally { Pop-Location }
        $report.junit = @{ exitCode=$code; expectedTests=$expected; suites=$classes }
        $summary = Get-Content -LiteralPath (Join-Path $Evidence 'junit.log') -Raw
        if ($code -ne 0 -or $summary -notmatch ('OK \(' + $expected + ' tests\)')) {
            throw 'Fresh ECJ JUnit run failed or its test count differs'
        }
    }
    $report.status = 'PASS'
} catch {
    $report.status = 'FAIL'
    $report['error'] = $_.Exception.Message
    throw
} finally {
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $Evidence 'ecj-results.json') -Encoding utf8
}
Write-Output 'PASS: fresh Eclipse compilation with restricted JDK references treated as errors'
