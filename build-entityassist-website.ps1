[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$')]
    [string] $Tag = '3.0.0-SNAPSHOT',
    [switch] $Push
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$module = Join-Path $PSScriptRoot 'EntityAssistWebsite'
$webroot = Join-Path $module 'target/webroot/ea-website'
$image = "gedmarc/entityassist-website:$Tag"

# Run tools directly: stderr warnings must not be mistaken for a failed build in
# Windows PowerShell. Exit codes determine success; stale output is never reused.
function Invoke-Checked {
    param([string] $Command, [string[]] $Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments
        $toolExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($toolExitCode -ne 0) { throw "$Command failed with exit code $toolExitCode" }
}

Push-Location $module
try {
    Invoke-Checked 'mvn' @('-B', '-ntp', '-f', 'examples/pom.xml', 'compile')
    Invoke-Checked 'mvn' @('-B', '-ntp', 'install', '-Dwebsite.docker=false')
} finally { Pop-Location }

if (-not (Test-Path -LiteralPath (Join-Path $webroot 'package.json'))) {
    throw "Angular generation did not produce $webroot/package.json"
}
$routes = Get-Content -Raw -LiteralPath (Join-Path $webroot 'src/app/com/jwebmp/core/base/angular/modules/services/angular/AngularRoutingModule/AngularRoutingModule.ts')
foreach ($route in @('home', 'getting-started', 'query-guide', 'capabilities', 'support')) {
    if ($routes -notmatch ('"path"\s*:\s*"' + [regex]::Escape($route) + '"')) {
        throw "Angular generation omitted route '$route'. Check EntityAssistWebsite/logs/system.log."
    }
}
Push-Location $webroot
try {
    # Always run the production compiler. Some installed generator versions only
    # emit sources even when buildAngular is enabled in the POM.
    Invoke-Checked 'npm' @('install', '--no-audit', '--no-fund')
    $buildStarted = [DateTime]::UtcNow
    Invoke-Checked 'npm' @('run', 'build-prod')
    $index = Get-Item -LiteralPath 'dist/jwebmp/browser/index.html'
    if ($index.LastWriteTimeUtc -lt $buildStarted.AddSeconds(-2)) {
        throw 'The production compiler did not produce a fresh index.html'
    }
} finally { Pop-Location }

Invoke-Checked 'docker' @('build', '--file', (Join-Path $module 'Dockerfile'), '--tag', $image, $module)
if ($Push) {
    # Uses the existing Docker credential store; no credentials are stored here.
    Invoke-Checked 'docker' @('push', $image)
}
Write-Host "Built $image"
Write-Host "Preview: docker run --rm -p 8088:80 $image"
