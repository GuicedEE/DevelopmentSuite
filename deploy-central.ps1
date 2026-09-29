# deploy-central.ps1
#
# Orchestrates the GuicedEE 2.2.0 release to Maven Central in dependency-correct waves,
# parallelising wherever Sonatype's validation allows it.
#
#   WAVE 1 : group 1  versioner                       (self-validating, no parent)
#   WAVE 2 : group 2  12 BOMs + parent                (one bundle, -Pguicedee-boms)
#   WAVE 3 : group 3  services   ||  group 4  modules (one bundle each, in parallel)
#
# Each wave is ONE Sonatype deployment per group, because central-publishing bundles a
# whole Maven reactor together. Splitting a group into per-POM deployments multiplies the
# ~6-15 min validation cycle by the module count for no benefit - see deploy-central-2-boms.ps1.
#
# WHY THE WAVES ARE NOT NEGOTIABLE
# --------------------------------
# Sonatype requires name/description/url/licenses/developers/scm on every published
# component, and resolves the <parent> chain FROM CENTRAL to satisfy inherited values.
# Measured against the real 2.2.0 consumer POMs:
#     versioner        -> declares everything inline        -> self-validating
#     the 12 BOMs      -> declare everything inline         -> self-validating
#     parent           -> inherits licenses/developers/scm  -> needs versioner live
#     62 of 108 others -> inherit the same via parent       -> need parent live
# Publishing a child before its parent gives a validation failure ~15 minutes in, and the
# broken deployment then has to be dropped by hand in the Central Portal.
#
# Usage:
#   .\deploy-central.ps1              # full release, all waves
#   .\deploy-central.ps1 -WhatIf      # print the plan, deploy nothing
#   .\deploy-central.ps1 -FromWave 3  # resume after a failure
#   .\deploy-central.ps1 -Tag         # also create+push the git tag / GitHub release at the end

param(
    [ValidateRange(1,3)][int] $FromWave = 1,
    [switch] $BomsIndividually,
    [string[]] $BomSkip = @(),
    [int]    $BomThrottle = 4,
    [switch] $Tag,
    [switch] $WhatIf
)

. "$PSScriptRoot\deploy-central-common.ps1"
Set-Location $PSScriptRoot

$version = Assert-ReleaseVersion
$logDir  = Join-Path $PSScriptRoot 'logs/central'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host ""
Write-Host "=== GuicedEE $version -> Maven Central ===" -ForegroundColor Magenta
Write-Host "  wave 1 : versioner                (1 bundle)"
Write-Host "  wave 2 : 12 BOMs + parent         (1 bundle)"
Write-Host "  wave 3 : services || modules      (1 bundle each)"
Write-Host ""

if ($WhatIf) {
    & "$PSScriptRoot\deploy-central-1-versioner.ps1" -WhatIf
    & "$PSScriptRoot\deploy-central-2-boms.ps1"      -WhatIf
    & "$PSScriptRoot\deploy-central-3-services.ps1"  -WhatIf
    & "$PSScriptRoot\deploy-central-4-modules.ps1"   -WhatIf
    return
}

# One global lock sweep before anything starts; after this each group only cleans its own paths
# so that parallel groups cannot delete each other's in-flight lock dirs.
Remove-LockDirs -Paths @('.')

$total = [Diagnostics.Stopwatch]::StartNew()

# ---------------- WAVE 1 ----------------
if ($FromWave -le 1) {
    Write-Host "########## WAVE 1 - versioner ##########" -ForegroundColor Magenta
    & "$PSScriptRoot\deploy-central-1-versioner.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Wave 1 failed - stopping (nothing downstream can validate)." }
}

# ---------------- WAVE 2 ----------------
if ($FromWave -le 2) {
    Write-Host "########## WAVE 2 - BOMs + parent ##########" -ForegroundColor Magenta
    $w2 = @{}
    if ($BomsIndividually) { $w2.Individual = $true; $w2.Throttle = $BomThrottle }
    if ($BomSkip.Count)    { $w2.Skip = $BomSkip }
    & "$PSScriptRoot\deploy-central-2-boms.ps1" @w2
    if ($LASTEXITCODE -ne 0) { throw "Wave 2 failed - stopping (services/modules cannot validate without parent)." }
}

# ---------------- WAVE 3 (parallel) ----------------
if ($FromWave -le 3) {
    Write-Host "########## WAVE 3 - services || modules ##########" -ForegroundColor Magenta

    $procs = @()
    foreach ($g in @(
        @{ Script = 'deploy-central-3-services.ps1'; Log = 'group3-services.log' },
        @{ Script = 'deploy-central-4-modules.ps1';  Log = 'group4-modules.log'  }
    )) {
        $log = Join-Path $logDir $g.Log
        Write-Host "-> launching $($g.Script)  (log: $log)" -ForegroundColor Cyan
        $procs += Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File', (Join-Path $PSScriptRoot $g.Script)) `
            -RedirectStandardOutput $log `
            -RedirectStandardError  ($log -replace '\.log$', '.err.log') `
            -PassThru -NoNewWindow
    }

    Write-Host "Waiting for wave 3 ($($procs.Count) deployments)..." -ForegroundColor Yellow
    $procs | Wait-Process
    $bad = $procs | Where-Object { $_.ExitCode -ne 0 }
    if ($bad) { throw "Wave 3 failed - see $logDir. Drop any bad deployment in the Central Portal before retrying." }
}

$total.Stop()
Write-Host ""
Write-Host "=== ALL GROUPS PUBLISHED in $($total.Elapsed.ToString('hh\:mm\:ss')) ===" -ForegroundColor Green

# ---------------- tag / GitHub release ----------------
if ($Tag) {
    $t = "v$version"
    if (-not (git tag -l $t)) {
        Write-Host "---- creating tag $t" -ForegroundColor Cyan
        $prev  = git describe --tags --abbrev=0 2>$null
        $notes = if ($prev) { git --no-pager log "$prev..HEAD" --pretty=format:"- %s (%h)" | Out-String } else { "Release $version" }
        if (-not $notes.Trim()) { $notes = "Release $version" }
        git tag -a $t -m "Release $version"
        git push origin $t
        if (Get-Command gh -ErrorAction SilentlyContinue) {
            gh release create $t --title "Release $version" --notes $notes
        } else {
            Write-Host "gh CLI not found - tag pushed, GitHub Release not created" -ForegroundColor Yellow
        }
    } else {
        Write-Host "Tag $t already exists - skipping" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Remaining trains (not part of this release): EntityAssist, JWebMP, ActivityMaster." -ForegroundColor Yellow

