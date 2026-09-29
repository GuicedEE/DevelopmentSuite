# build-guicedee-website.ps1
#
# Builds the GuicedEE FRONT website (com.guicedee:website) and optionally pushes its
# Docker image.
#
# The module is a JWebMP/Angular app: jwebmp angular-maven-plugin regenerates the Angular
# project under target/webroot/guicedee-website, runs npm install + ng build, and then
# builds an nginx image from the generated Dockerfile (which COPYs dist/jwebmp/browser).
#
# NOTE: this is the front site only. The backend (website-backend / website-backend-jlink)
# is a separate deliverable and is NOT touched here.
#
# Usage:
#   .\build-guicedee-website.ps1              # maven install -> angular build -> docker image
#   .\build-guicedee-website.ps1 -Push        # also push to Docker Hub
#   .\build-guicedee-website.ps1 -Tag 2.3.0   # override the image tag
#   .\build-guicedee-website.ps1 -AngularOnly # skip maven, just npm install + ng build

param(
    [switch] $Push,
    [string] $Tag,
    [switch] $AngularOnly
)

$ErrorActionPreference = 'Stop'
$root    = $PSScriptRoot
$module  = Join-Path $root 'GuicedEE\website'
$webroot = Join-Path $module 'target\webroot\guicedee-website'
$logDir  = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Get-PomImage {
    $pom = Get-Content (Join-Path $module 'pom.xml') -Raw
    if ($pom -match '<dockerImageName>(.+?)</dockerImageName>') { return $Matches[1] }
    throw "No <dockerImageName> in the website pom"
}

$image = Get-PomImage
if ($Tag) {
    $repo  = ($image -split ':')[0]
    $image = "${repo}:$Tag"
}
Write-Host "Target image: $image" -ForegroundColor Cyan

# ---------------------------------------------------------------- maven / angular
if (-not $AngularOnly) {
    Write-Host "---- mvn install (drives angular-maven-plugin:build)" -ForegroundColor Cyan
    Push-Location $root
    try {
        & mvn '-B' '-ntp' 'install' '-f' 'GuicedEE/website/pom.xml' '-DskipTests' |
            Tee-Object -FilePath (Join-Path $logDir 'website-install.log')
        if ($LASTEXITCODE -ne 0) { throw "maven install failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

# The Dockerfile COPYs dist/jwebmp/browser - verify it is actually fresh, because the
# plugin regenerating src/ does not guarantee ng build produced new output.
$browser = Join-Path $webroot 'dist\jwebmp\browser'
if (-not (Test-Path $browser)) { throw "Angular output missing: $browser" }

$newest = (Get-ChildItem $browser -Recurse -File | Sort-Object LastWriteTime -Descending |
           Select-Object -First 1).LastWriteTime
$ageHrs = [math]::Round(((Get-Date) - $newest).TotalHours, 1)
Write-Host "Angular output newest file: $newest  (${ageHrs}h old)" -ForegroundColor Yellow

if ($ageHrs -gt 1) {
    Write-Host "Angular output looks stale - running npm install + ng build directly" -ForegroundColor Yellow
    Push-Location $webroot
    try {
        & npm install       2>&1 | Tee-Object -FilePath (Join-Path $logDir 'website-npm-install.log')
        if ($LASTEXITCODE -ne 0) { throw "npm install failed (exit $LASTEXITCODE)" }
        & npm run build-prod 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'website-ng-build.log')
        if ($LASTEXITCODE -ne 0) { throw "ng build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }

    $newest = (Get-ChildItem $browser -Recurse -File | Sort-Object LastWriteTime -Descending |
               Select-Object -First 1).LastWriteTime
    Write-Host "Angular output now: $newest" -ForegroundColor Green
}

# ---------------------------------------------------------------- docker
Write-Host "---- docker build $image" -ForegroundColor Cyan
& docker build -t $image $webroot
if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit $LASTEXITCODE)" }

if ($Push) {
    # Docker Desktop's credential store already holds the Docker Hub login, so an
    # explicit docker login (and DOCKERHUB_USERNAME/PAT) is not required.
    Write-Host "---- docker push $image" -ForegroundColor Cyan
    & docker push $image
    if ($LASTEXITCODE -ne 0) { throw "docker push failed (exit $LASTEXITCODE)" }
    Write-Host "Pushed $image" -ForegroundColor Green
} else {
    Write-Host "Built $image (not pushed - re-run with -Push)" -ForegroundColor Green
}

