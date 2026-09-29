#!/usr/bin/env pwsh
# Push the GuicedEE website Docker image to Docker Hub (Windows PowerShell version)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PomFile = Join-Path $RootDir 'GuicedEE\website\pom.xml'
$WebRoot = Join-Path $RootDir 'GuicedEE\website\target\webroot\guicedee-website'

if (-not (Test-Path $PomFile)) {
    Write-Error "Could not find $PomFile"
    exit 1
}

# Extract dockerImageName from pom.xml
$PomContent = Get-Content $PomFile -Raw
if ($PomContent -match '<dockerImageName>(.+?)</dockerImageName>') {
    $PomImage = $Matches[1]
} else {
    Write-Error "Could not find <dockerImageName> in $PomFile"
    exit 1
}

# Derive the Docker Hub push target
$DOCKERHUB_USERNAME = $env:DOCKERHUB_USERNAME
if (-not $DOCKERHUB_USERNAME) {
    Write-Error "DOCKERHUB_USERNAME environment variable is not set."
    exit 1
}

$ImageNameAndTag = ($PomImage -split '/', 2)[1]
$PushImage = "$DOCKERHUB_USERNAME/$ImageNameAndTag"
$ImageName = ($ImageNameAndTag -split ':')[0]

# Login to Docker Hub
$DOCKERHUB_PAT = $env:DOCKERHUB_PAT
if (-not $DOCKERHUB_PAT) {
    Write-Error "DOCKERHUB_PAT environment variable is not set."
    exit 1
}
$DOCKERHUB_PAT | docker login --username $DOCKERHUB_USERNAME --password-stdin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Build Docker image from the webroot
Write-Host "Building Docker image from $WebRoot ..." -ForegroundColor Cyan
docker build -t $PomImage -t "${DOCKERHUB_USERNAME}/${ImageName}:latest" $WebRoot
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Tag for push if needed
if ($PomImage -ne $PushImage) {
    docker tag $PomImage $PushImage
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Push
Write-Host "Pushing $PushImage ..." -ForegroundColor Cyan
docker push $PushImage
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Pushing ${DOCKERHUB_USERNAME}/${ImageName}:latest ..." -ForegroundColor Cyan
docker push "${DOCKERHUB_USERNAME}/${ImageName}:latest"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Done! Image pushed to Docker Hub as $PushImage" -ForegroundColor Green

