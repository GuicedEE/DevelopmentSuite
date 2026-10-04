param(
    [string]$Ne1Root = 'C:\Java\ne1-world',
    [switch]$SkipLibraries
)
$ErrorActionPreference = 'Stop'
$verificationRoot = $PSScriptRoot
$verificationLogs = Join-Path $verificationRoot 'target\clustering-verification'
New-Item -ItemType Directory -Path $verificationLogs -Force | Out-Null
function Invoke-ClusterNative([string]$Executable, [string[]]$Arguments, [string]$Log) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Executable @Arguments *> $Log
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw "Command failed ($nativeExit): $Executable. See $Log" }
}
function Invoke-ClusterMaven([string]$Directory, [string]$Name, [string[]]$Arguments) {
    Push-Location -LiteralPath $Directory
    try {
        Invoke-ClusterNative 'mvn.cmd' ($Arguments + '-Dstyle.color=never') (Join-Path $verificationLogs ($Name + '.log'))
    } finally { Pop-Location }
}
if (!$SkipLibraries) {
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\services\JCache\hazelcast" 'hazelcast-wrapper' @('install')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\inject" 'inject' @('install', '-Dtest=FrameworkLifecycleTest')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\vertx" 'vertx' @('install')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\web" 'web' @('install')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\metrics" 'metrics' @('install', '-Dtest=MetricsOptionsCompositionTest,VertxMetricsEnumTest')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\hazelcast" 'hazelcast' @('install', '-Dtest=HazelcastBinderTest')
    Invoke-ClusterMaven "$verificationRoot\GuicedEE\websockets" 'websockets' @('install')
    Invoke-ClusterMaven "$verificationRoot\JWebMP\plugins\tsclient" 'tsclient' @('install')
    Invoke-ClusterMaven "$verificationRoot\JWebMP\plugins\angular" 'angular' @('install', '-Dtest=BoundedStompSocketTest')
}
Invoke-ClusterMaven "$verificationRoot\JWebMP\plugins\tsclient" 'eventbus-generation' @('test', '-Dtest=EventBusSecureSubscriptionRenderingTest')
Invoke-ClusterMaven "$Ne1Root\modules\lobby-boundary\lobby-web" 'lobby-generation' @('test', '-Dtest=LobbyRenderingTest,LobbyFrontendContributionTest')
$browserFixture = Join-Path $Ne1Root 'web\core\src\test\cluster-browser'
Invoke-ClusterNative 'npm.cmd' @('ci', '--prefix', $browserFixture, '--no-audit', '--no-fund') (Join-Path $verificationLogs 'npm.log')
# These are source-generation locations, not application environment settings.
$env:NE1_EVENTBUS_GENERATED = "$verificationRoot\JWebMP\plugins\tsclient\target\eventbus-runtime\EventBusService.ts"
$env:NE1_CONTEXT_GENERATED = "$verificationRoot\JWebMP\plugins\tsclient\target\eventbus-runtime\ContextIdService.ts"
Invoke-ClusterNative 'node' @("$verificationRoot\JWebMP\plugins\tsclient\src\test\scripts\eventbus-reconnect.cjs", $browserFixture) (Join-Path $verificationLogs 'reconnect.log')
Invoke-ClusterMaven "$Ne1Root\web\core" 'core-acceptance' @('package', '-Dne1.core.build.directory=target/clustering-runtime',
    '-Dne1.cluster.browser=true', '-Dtest=CoreClusterAcceptanceTest,LobbyBridgePolicyTest,LobbyRoutesTest,LobbyRuntimeTest,ShellRoutesTest,ShellRestSecurityTest,PublicRouteBoundaryTest,PrivateManagementListenerTest,ApplicationSecurityFilterTest,AppGatewayTest')
Invoke-ClusterMaven "$Ne1Root\web\core" 'core-packaged' @('test', '-Dne1.core.build.directory=target/clustering-runtime',
    '-Dne1.cluster.browser=true', '-Dne1.cluster.packaged=true', '-Dtest=CoreClusterAcceptanceTest')
$artifactReport = Join-Path $Ne1Root 'web\core\target\clustering-runtime\surefire-reports\TEST-world.ne1.core.test.CoreClusterAcceptanceTest.xml'
[xml]$assemblyTests = Get-Content -LiteralPath $artifactReport -Raw
$assemblyPath = ($assemblyTests.testsuite.properties.property | Where-Object name -eq 'jdk.module.path').value -split [IO.Path]::PathSeparator
$assemblyNames = $assemblyPath | ForEach-Object { [IO.Path]::GetFileName($_) }
$artifactDirectories = @('GuicedEE\services\JCache\hazelcast', 'GuicedEE\inject', 'GuicedEE\vertx', 'GuicedEE\web',
    'GuicedEE\metrics', 'GuicedEE\hazelcast', 'GuicedEE\websockets', 'JWebMP\plugins\tsclient', 'JWebMP\plugins\angular')
$artifactProof = foreach ($artifactDirectory in $artifactDirectories) {
    $builtJar = Get-ChildItem -LiteralPath (Join-Path $verificationRoot "$artifactDirectory\target") -Filter '*.jar' |
        Where-Object { $_.Name -in $assemblyNames } | Select-Object -First 1
    if (!$builtJar) { throw "Missing built artifact: $artifactDirectory" }
    $effectiveJar = $assemblyPath | Where-Object { [IO.Path]::GetFileName($_) -eq $builtJar.Name } | Select-Object -First 1
    if (!$effectiveJar) { throw "Core module path is missing $($builtJar.Name)" }
    $builtHash = (Get-FileHash -LiteralPath $builtJar.FullName -Algorithm SHA256).Hash
    $effectiveHash = (Get-FileHash -LiteralPath $effectiveJar -Algorithm SHA256).Hash
    if ($builtHash -ne $effectiveHash) { throw "Core resolved a stale artifact: $effectiveJar" }
    [pscustomobject]@{ source = $builtJar.FullName; effective = $effectiveJar; sha256 = $effectiveHash; matches = $true }
}
$artifactProof | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Ne1Root 'web\core\target\clustering-artifacts.json') -Encoding UTF8
Invoke-ClusterMaven "$Ne1Root\web\core" 'effective-dependencies' @('dependency:tree',
    '-Dincludes=com.guicedee:hazelcast,com.guicedee:vertx,com.guicedee:web,com.guicedee:metrics,com.guicedee:websockets,com.guicedee:inject,com.guicedee.modules.services:hazelcast-all,com.guicedee.modules.services:vertx-hazelcast,io.vertx:vertx-hazelcast,io.vertx:vertx-stomp,com.jwebmp.plugins:angular,com.jwebmp.plugins:typescript-client', '-Dverbose')
Write-Output "Clustering verification passed. Logs: $verificationLogs"
