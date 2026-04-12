$logDir = "C:\Java\DevSuite\logs\javadoc-services"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force }

$modules = @(
    "Database\msal4j",
    "Database\mssql-jdbc",
    "Database\postgresql",
    "Hibernate\hibernate-c3p0",
    "Hibernate\hibernate-core",
    "Hibernate\hibernate-jcache",
    "Hibernate\hibernate-reactive",
    "Hibernate\hibernate-validator",
    "Apache\CXF\apache-cxf",
    "Apache\CXF\apache-cxf-rest",
    "Apache\CXF\apache-cxf-rest-openapi",
    "Apache\CXF\apache-cxf-rt-security",
    "Apache\CXF\apache-cxf-rt-transports-http",
    "Apache\POI\apache-poi",
    "Apache\POI\apache-poi-ooxml",
    "Apache\Commons\commons-beanutils",
    "Apache\Commons\commons-collections",
    "Apache\Commons\commons-csv",
    "Apache\Commons\commons-fileupload",
    "Apache\Commons\commons-math",
    "Google\guice-assistedinject",
    "Google\guice-grapher",
    "Google\guice-jmx",
    "Google\guice-jndi",
    "Jakarta\jakarta-security-jacc",
    "JCache\ehcache",
    "JCache\hazelcast",
    "JCache\hazelcast-hibernate",
    "JNI\jna-platform",
    "JNI\nrjavaserial",
    "Libraries\bcrypt",
    "Libraries\cloudevents",
    "Libraries\ibm-mq",
    "Libraries\jandex",
    "Libraries\javassist",
    "Libraries\json",
    "Libraries\junit-jupiter",
    "Libraries\kafka-client",
    "Libraries\mapstruct",
    "Libraries\openpdf",
    "Libraries\rabbitmq-client",
    "Libraries\scram",
    "Libraries\swagger",
    "Libraries\testcontainers",
    "MicroProfile\config-core",
    "MicroProfile\health-core",
    "MicroProfile\metrics-core",
    "MicroProfile\telemetry-core",
    "Vert.x\vertx-mutiny",
    "Vert.x\vertx-rabbitmq"
)

$results = @()
foreach ($mod in $modules) {
    $modDir = "C:\Java\DevSuite\GuicedEE\services\$mod"
    $safeName = $mod -replace '\\', '-'
    $logFile = "$logDir\verify-$safeName.log"

    Write-Host "=== Running verify for $mod ===" -ForegroundColor Cyan

    Push-Location $modDir
    & mvn verify 2>&1 | Out-File -FilePath $logFile -Encoding ASCII
    $exitCode = $LASTEXITCODE
    Pop-Location

    # Extract missing packages
    $missingPkgs = @()
    Select-String -Path $logFile -Pattern "package .* does not exist" | ForEach-Object {
        if ($_.Line -match 'package (\S+) does not exist') { $missingPkgs += $Matches[1] }
    }
    $missingPkgs = $missingPkgs | Sort-Object -Unique

    $errorCount = (Select-String -Path $logFile -Pattern "^\[ERROR\].*error:" -AllMatches | Measure-Object).Count
    $javadocErrors = (Select-String -Path $logFile -Pattern ": error:" -AllMatches | Measure-Object).Count

    if ($missingPkgs.Count -gt 0 -or $javadocErrors -gt 0) {
        Write-Host "  FAIL ($javadocErrors javadoc errors, $($missingPkgs.Count) missing packages)" -ForegroundColor Red
        if ($missingPkgs.Count -gt 0) {
            Write-Host "  Missing: $($missingPkgs -join ', ')" -ForegroundColor Yellow
        }
        $results += [PSCustomObject]@{ Module = $mod; Status = "FAIL"; JavadocErrors = $javadocErrors; MissingPkgs = ($missingPkgs -join ', ') }
    } elseif ($exitCode -ne 0) {
        Write-Host "  BUILD FAIL" -ForegroundColor Red
        $results += [PSCustomObject]@{ Module = $mod; Status = "BUILD_FAIL"; JavadocErrors = 0; MissingPkgs = "" }
    } else {
        Write-Host "  PASS" -ForegroundColor Green
        $results += [PSCustomObject]@{ Module = $mod; Status = "PASS"; JavadocErrors = 0; MissingPkgs = "" }
    }
}

Write-Host "`n=== SUMMARY ===" -ForegroundColor Yellow
$results | Format-Table -Property Module,Status,JavadocErrors -AutoSize

$failed = $results | Where-Object { $_.Status -ne "PASS" }
if ($failed) {
    Write-Host "`nFailed modules and missing packages:" -ForegroundColor Red
    $failed | ForEach-Object {
        Write-Host "  - $($_.Module)" -ForegroundColor Red
        if ($_.MissingPkgs) { Write-Host "    Missing: $($_.MissingPkgs)" -ForegroundColor Yellow }
    }
}

$results | Export-Csv -Path "$logDir\verify-SUMMARY.csv" -NoTypeInformation
$results | Format-Table -AutoSize | Out-String | Set-Content "$logDir\verify-SUMMARY.txt"

