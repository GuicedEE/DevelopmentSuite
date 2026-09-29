# deploy-central-common.ps1
# Shared helpers for the parallel Maven Central release scripts.
# Dot-source this: . "$PSScriptRoot\deploy-central-common.ps1"

$ErrorActionPreference = 'Stop'

# Maven 4's file-locking creates .locks directories inside central-staging, which corrupts the
# uploaded Central bundle. Disabling the named sync context avoids them being created at all.
$script:NoLocks = '-Daether.syncContext.named.factory=noop'

function Remove-LockDirs {
    <#  Removes .locks dirs. Scope it to the paths a given group actually builds so that
        parallel groups never delete each other's in-flight lock directories. #>
    param([string[]] $Paths = @('.'))
    foreach ($p in $Paths) {
        if (Test-Path $p) {
            Get-ChildItem -Path $p -Filter '.locks' -Recurse -Force -Directory -ErrorAction SilentlyContinue |
                Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

function Get-CentralArgs {
    param([Parameter(Mandatory)][string] $DeploymentName)
    if (-not $env:MAVEN_GPG_PASSPHRASE) {
        throw "MAVEN_GPG_PASSPHRASE is not set - artifacts cannot be signed and Central will reject the bundle."
    }
    @(
        '-DskipTests'
        '-Dmaven.consumer.pom=false'
        '-Dcentral.publishing.skip=false'
        '-Dmaven.deploy.skip=true'
        "-Dcentral.publishing.deploymentName=$DeploymentName"
        "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE"
        $script:NoLocks
        # Maven 4 caches per-repository "prefix" indexes and will refuse to fetch a path it
        # believes central does not serve, e.g.
        #   Prefix org/junit/junit-bom/... NOT allowed from central  (present, but unavailable)
        # A stale cache took out guicedee-bom (junit-bom) and both hibernate-bom / jboss-bom
        # (bouncycastle range for gpg) in the 2.2.0 run. Releases cannot afford that.
        '-Daether.remoteRepositoryFilter.prefixes.enabled=false'
        '-U'
    )
}

function Invoke-CentralDeploy {
    <#  Runs a single `mvn clean deploy` against Central.
        NOTE: `clean` is deliberate. Non-clean builds let moditect reuse a stale
        module-info.class from target/, which is how 2.2.0 nearly shipped four jars
        stamped @2.1.1-SNAPSHOT. Never drop it for a release. #>
    param(
        [string]   $Pom,
        [string[]] $Profiles = @(),
        [Parameter(Mandatory)][string] $DeploymentName,
        [string[]] $ExtraArgs = @()
    )

    $mvnArgs = @('-B', '-ntp', 'clean', 'deploy')
    if ($Pom)              { $mvnArgs += @('--file', $Pom) }
    if ($Profiles.Count)   { $mvnArgs += "-P$($Profiles -join ',')" }
    $mvnArgs += Get-CentralArgs -DeploymentName $DeploymentName
    $mvnArgs += $ExtraArgs

    $label = if ($Pom) { $Pom } else { "-P$($Profiles -join ',')" }
    Write-Host "---- deploying $label  (deployment: $DeploymentName)" -ForegroundColor Cyan

    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Central deploy FAILED for $label (exit $LASTEXITCODE)"
    }
    Write-Host "---- OK $label" -ForegroundColor Green
}

function Assert-ReleaseVersion {
    <#  Refuses to publish a SNAPSHOT to Central. #>
    $v = (Select-Xml -Path (Join-Path $PSScriptRoot 'GuicedEE\bom\Versioner\pom.xml') `
            -XPath "//*[local-name()='project']/*[local-name()='version']").Node.InnerText
    if ($v -match 'SNAPSHOT') { throw "Versioner is $v - refusing to publish a SNAPSHOT to Central." }
    Write-Host "Release version: $v" -ForegroundColor Yellow
    return $v
}

