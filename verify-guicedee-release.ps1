param(
    [ValidateSet('Validate', 'Package', 'Test', 'Website')]
    [string] $Stage = 'Validate',
    [switch] $IncludeLiveInfrastructure
)

$ErrorActionPreference = 'Stop'
$releaseRoot = $PSScriptRoot
$releaseOutput = Join-Path $releaseRoot 'target/release-2.3.0'
New-Item -ItemType Directory -Force -Path $releaseOutput | Out-Null

# Include the versioner in the same reactor as its BOM imports so unpublished
# release coordinates resolve locally. No deployment or clean lifecycle is used.
[xml] $builder = Get-Content -LiteralPath (Join-Path $releaseRoot 'pom.xml') -Raw
$releaseModules = @('GuicedEE/bom/Versioner')
foreach ($profileName in @('guicedee-boms', 'services', 'guicedee')) {
    $profile = $builder.project.profiles.profile | Where-Object { $_.id -eq $profileName }
    $releaseModules += @($profile.modules.module)
}
$moduleXml = ($releaseModules | ForEach-Object {
    $absoluteModule = (Join-Path $releaseRoot $_).Replace('\', '/')
    '<module>' + [System.Security.SecurityElement]::Escape($absoluteModule) + '</module>'
}) -join "`n"
$reactor = Join-Path $releaseOutput 'reactor.xml'
$reactorXml = @"
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guicedee.build</groupId>
  <artifactId>release-verification</artifactId>
  <version>2.3.0</version>
  <packaging>pom</packaging>
  <modules>$moduleXml</modules>
</project>
"@
[System.IO.File]::WriteAllText($reactor, $reactorXml)
$releaseArgs = @('-B', '-ntp', '-f', $reactor,
    '-Dcentral.publishing.skip=true', '-Dmaven.deploy.skip=true',
    '-Dmaven.test.failure.ignore=false', '-DreuseForks=false')
if (-not $IncludeLiveInfrastructure) {
    # These tests require the separately deployed Kubernetes demo and NodePort.
    $releaseArgs += '-DexcludedGroups=kubernetes'
}
switch ($Stage) {
    'Validate' { $releaseArgs += 'validate' }
    'Package' { $releaseArgs += @('install', '-DskipTests') }
    # Shaded services acquire their JPMS descriptors in package, so a full
    # reactor must reach verify before downstream modules can run their tests.
    'Test' { $releaseArgs += @('verify', '-fae') }
    'Website' {
        $releaseArgs = @('-B', '-ntp', '-f', (Join-Path $releaseRoot 'GuicedEE/website/pom.xml'),
            'install', '-Dcentral.publishing.skip=true', '-Dmaven.deploy.skip=true')
    }
}
$testStarted = Get-Date
if ($Stage -eq 'Package') {
    # javac's incremental cache does not always notice a project version change.
    # Refresh descriptor timestamps to regenerate module versions without deleting outputs.
    foreach ($module in $releaseModules) {
        $descriptor = Join-Path $releaseRoot "$module/src/main/java/module-info.java"
        if (Test-Path -LiteralPath $descriptor) {
            [System.IO.File]::SetLastWriteTimeUtc($descriptor, [DateTime]::UtcNow)
        }
    }
}
# Windows PowerShell represents native stderr (including JVM warnings) as error records.
# Maven's exit code and test reports determine failure.
try {
    $ErrorActionPreference = 'Continue'
    & mvn @releaseArgs 2>&1 | Tee-Object -FilePath (Join-Path $releaseOutput "$($Stage.ToLowerInvariant()).log")
    $releaseExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
}
if ($releaseExitCode -ne 0) { throw "GuicedEE release $Stage failed; see $releaseOutput" }

if ($Stage -eq 'Package') {
    foreach ($module in $releaseModules) {
        [xml] $modulePom = Get-Content -LiteralPath (Join-Path $releaseRoot "$module/pom.xml") -Raw
        if ($modulePom.project.packaging -eq 'pom') { continue }
        $moduleVersion = [string]$modulePom.project.version
        if (-not $moduleVersion) { $moduleVersion = [string]$modulePom.project.parent.version }
        $artifactId = [string]$modulePom.project.artifactId
        $moduleJar = Join-Path $releaseRoot "$module/target/$artifactId-$moduleVersion.jar"
        if (-not (Test-Path -LiteralPath $moduleJar)) { throw "Missing release JAR: $moduleJar" }
        $description = & jar --describe-module --file $moduleJar
        if ($LASTEXITCODE -ne 0 -or -not ($description -match "@$([regex]::Escape($moduleVersion)) ")) {
            throw "Missing or stale JPMS descriptor: $moduleJar"
        }
    }
}

# Some existing modules configure testFailureIgnore directly in their POMs.
# Inspect fresh reports as well as Maven's exit status.
if ($Stage -eq 'Test') {
    foreach ($module in $releaseModules) {
        $reportDirectory = Join-Path $releaseRoot "$module/target/surefire-reports"
        if (-not (Test-Path -LiteralPath $reportDirectory)) { continue }
        foreach ($report in Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml') {
            if ($report.LastWriteTime -lt $testStarted) { continue }
            [xml] $result = Get-Content -LiteralPath $report.FullName -Raw
            if ([int]$result.testsuite.failures -gt 0 -or [int]$result.testsuite.errors -gt 0) {
                throw "Release tests failed: $($report.FullName)"
            }
        }
    }
}
