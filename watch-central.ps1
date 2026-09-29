# watch-central.ps1
# Polls Maven Central for the GuicedEE 2.2.0 release artifacts and appends a compact
# status line to _central_status.txt every $IntervalSeconds until everything is live
# (or -MaxMinutes elapses). Purely read-only - it never publishes anything.

param(
    [int] $IntervalSeconds = 60,
    [int] $MaxMinutes      = 240
)

$ErrorActionPreference = 'SilentlyContinue'
$out = Join-Path $PSScriptRoot '_central_status.txt'

# wave -> artifacts (groupId path, artifactId)
$targets = [ordered]@{
    # NOTE: the leading comma is required - it stops PowerShell unrolling a
    # single-element list of pairs into a flat 2-element array.
    'w1 versioner' = @(, @('com/guicedee', 'versioner'))
    'w2 boms'      = @(
        @('com/guicedee','standalone-bom'), @('com/guicedee','tests-bom'),
        @('com/guicedee','swagger-bom'),    @('com/guicedee','jboss-bom'),
        @('com/guicedee','jakarta-bom'),    @('com/guicedee','hibernate-bom'),
        @('com/guicedee','google-bom'),     @('com/guicedee','fasterxml-bom'),
        @('com/guicedee','apache-bom'),     @('com/guicedee','apache-cxf-bom'),
        @('com/guicedee','smallrye-bom'),   @('com/guicedee','vertx-bom'),
        @('com/guicedee','guicedee-bom')
    )
    'w2 parent'    = @(, @('com/guicedee', 'parent'))
    'w3 modules'   = @(
        @('com/guicedee','inject'),  @('com/guicedee','client'),
        @('com/guicedee','vertx'),   @('com/guicedee','rest'),
        @('com/guicedee','persistence'), @('com/guicedee','web'),
        @('com/guicedee','telemetry')
    )
}

function Test-Live([string] $g, [string] $a) {
    $url = "https://repo1.maven.org/maven2/$g/$a/2.2.0/$a-2.2.0.pom"
    try { (Invoke-WebRequest -Uri $url -Method Head -TimeoutSec 15 -UseBasicParsing).StatusCode -eq 200 }
    catch { $false }
}

$deadline = (Get-Date).AddMinutes($MaxMinutes)

while ((Get-Date) -lt $deadline) {
    $parts = foreach ($wave in $targets.Keys) {
        $live = @($targets[$wave] | Where-Object { Test-Live $_[0] $_[1] }).Count
        "{0} {1}/{2}" -f $wave, $live, $targets[$wave].Count
    }
    $line = "{0}  {1}" -f (Get-Date -Format 'HH:mm:ss'), ($parts -join '  |  ')
    Add-Content -Path $out -Value $line

    # stop once every tracked artifact is live
    $total   = ($targets.Keys | ForEach-Object { $targets[$_].Count } | Measure-Object -Sum).Sum
    $liveAll = ($parts | ForEach-Object { [int]($_ -split '[ /]')[-2] } | Measure-Object -Sum).Sum
    if ($liveAll -ge $total) {
        Add-Content -Path $out -Value "ALL TRACKED ARTIFACTS LIVE ON CENTRAL"
        break
    }
    Start-Sleep -Seconds $IntervalSeconds
}



