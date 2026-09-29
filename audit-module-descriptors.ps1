# audit-module-descriptors.ps1
#
# Verifies that every published GuicedEE artifact has a JPMS module descriptor whose
# exported/opened packages actually exist inside the jar.
#
# WHY THIS EXISTS
# ---------------
# The shaded service modules carry a hand-maintained src/moditect/module-info.java. When an
# upstream library drops a package between versions, the descriptor keeps exporting it and
# moditect stamps it in regardless. Nothing fails at build time - it only blows up in a
# consumer's face at startup:
#
#   java.lang.module.FindException: Error reading module: .../javassist.jar
#   Caused by: InvalidModuleDescriptorException: Package javassist.tools.reflect not found in module
#
# That is exactly what javassist 3.32.0-GA did (it removed tools.reflect/tools.rmi/tools.web).
# `jar --describe-module` performs the same validation the module system does, so it catches
# the whole class of defect.
#
# Usage:
#   .\audit-module-descriptors.ps1                  # audit 2.2.0 in the local repo
#   .\audit-module-descriptors.ps1 -Version 2.2.1
#   .\audit-module-descriptors.ps1 -GroupPath com/guicedee/modules/services

param(
    [string] $Version   = '2.2.0',
    [string] $Repo      = "$env:USERPROFILE\.m2\repository",
    [string[]] $GroupPath = @('com/guicedee', 'com/guicedee/modules/services',
                              'com/guicedee/modules/representations'),
    [string] $OutFile   = "$PSScriptRoot\_module_audit.txt"
)

$ErrorActionPreference = 'Continue'

# NOTE: `jar --describe-module` only PRINTS the descriptor - it happily lists exports for
# packages that do not exist. Only the module system itself validates, so resolve each jar
# on a module path instead; that reproduces the consumer-side failure exactly.
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $javaExe)) { $javaExe = 'java' }

$results = @()
foreach ($gp in $GroupPath) {
    $base = Join-Path $Repo ($gp -replace '/', '\')
    if (-not (Test-Path $base)) { continue }

    # only direct children - deeper paths are covered by their own GroupPath entry
    Get-ChildItem $base -Directory | ForEach-Object {
        $jar = Join-Path $_.FullName "$Version\$($_.Name)-$Version.jar"
        if (-not (Test-Path $jar)) { return }

        $out = & $javaExe --module-path $jar --list-modules 2>&1 | Out-String

        # Two very different failures come out of module resolution and must not be conflated:
        #
        #  1. InvalidModuleDescriptorException "... not found in module"
        #     -> REAL DEFECT. The descriptor exports/opens a package the jar does not contain.
        #        Every JPMS consumer of this artifact fails at startup.
        #
        #  2. FindException "Module X not found, required by Y"
        #     -> NOT a defect. We deliberately put a single jar on the module path, so its
        #        `requires` cannot resolve. Expected noise when auditing one artifact at a time.
        $defect     = $out -match 'InvalidModuleDescriptorException'
        $unresolved = (-not $defect) -and ($out -match 'Module .+ not found, required by')
        $automatic  = $out -match 'automatic'

        $status = if ($defect) { 'BROKEN' }
                  elseif ($unresolved) { 'ok (deps unresolved)' }
                  elseif ($automatic) { 'automatic' }
                  else { 'ok' }

        $missingPkg = if ($defect -and $out -match 'Package ([\w.]+) not found in module') { $Matches[1] } else { '' }

        $results += [pscustomobject]@{
            Artifact  = "$gp/$($_.Name)"
            Jar       = $jar
            Status    = $status
            Detail    = $missingPkg
        }
    }
}

$broken = @($results | Where-Object { $_.Status -eq 'BROKEN' })

$report = @()
$report += "module descriptor audit - version $Version - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$report += "scanned              : $($results.Count) jars"
$report += "ok                   : $(@($results | Where-Object { $_.Status -eq 'ok' }).Count)"
$report += "ok (deps unresolved) : $(@($results | Where-Object { $_.Status -eq 'ok (deps unresolved)' }).Count)"
$report += "automatic            : $(@($results | Where-Object { $_.Status -eq 'automatic' }).Count)"
$report += "BROKEN               : $($broken.Count)"
if ($broken.Count) {
    $report += ''
    $report += '--- BROKEN: descriptor exports a package the jar does not contain ---'
    $report += '    (every JPMS consumer of these fails at startup)'
    $broken | ForEach-Object { $report += ("  {0,-52} missing: {1}" -f $_.Artifact, $_.Detail) }
}
$report -join "`n" | Out-File $OutFile -Encoding utf8 -Force
$report | ForEach-Object { Write-Host $_ }

if ($broken.Count) { exit 1 }




