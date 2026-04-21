# Clean .locks directories from project-local-repo (Maven 4 artifact)
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Deploy Versioner BOM first (foundation for all other BOMs)
mvn -B -ntp clean deploy `
  --file GuicedEE/bom/Versioner/pom.xml `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "Versioner deploy failed"; exit $LASTEXITCODE }

# Clean .locks again before batch deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Deploy GuicedEE BOMs individually (reactor staging breaks POM-only modules)
$guicedeeBoms = @(
  "GuicedEE/bom/StandaloneBOM/pom.xml",
  "GuicedEE/bom/TestLayoutBOM/pom.xml",
  "GuicedEE/bom/SwaggerBOM/pom.xml",
  "GuicedEE/bom/JBossBOM/pom.xml",
  "GuicedEE/bom/JakartaBOM/pom.xml",
  "GuicedEE/bom/HibernateBOM/pom.xml",
  "GuicedEE/bom/GoogleBOM/pom.xml",
  "GuicedEE/bom/FasterXMLBOM/pom.xml",
  "GuicedEE/bom/ApacheBOM/pom.xml",
  "GuicedEE/bom/ApacheCXFBOM/pom.xml",
  "GuicedEE/bom/SmallRyeBOM/pom.xml",
  "GuicedEE/bom/pom.xml",
  "GuicedEE/parent/pom.xml"
)

foreach ($pom in $guicedeeBoms) {
  Write-Host "──── Deploying $pom ────"
  Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
  mvn -B -ntp deploy `
    --file $pom `
    -DskipTests `
    "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
    "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
    -U @args
  if ($LASTEXITCODE -ne 0) { Write-Host "$pom deploy failed"; exit $LASTEXITCODE }
}

# Clean .locks before GuicedEE modules deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy GuicedEE modules namespace (modules + services + entityassist)
mvn -B -ntp deploy `
  "-Pguicedee,services,entityassist" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "GuicedEE deploy failed"; exit $LASTEXITCODE }

# Clean .locks before JWebMP deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Deploy JWebMP BOMs individually
$jwebmpBoms = @(
  "JWebMP/bom/pom.xml",
  "JWebMP/parent/pom.xml"
)

foreach ($pom in $jwebmpBoms) {
  Write-Host "──── Deploying $pom ────"
  Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
  mvn -B -ntp deploy `
    --file $pom `
    -DskipTests `
    "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
    "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
    -U @args
  if ($LASTEXITCODE -ne 0) { Write-Host "$pom deploy failed"; exit $LASTEXITCODE }
}

# Clean .locks before JWebMP modules deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy JWebMP modules namespace
mvn -B -ntp deploy `
  "-Pjwebmp" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "JWebMP deploy failed"; exit $LASTEXITCODE }

# Clean .locks before Activity Master deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy Activity Master namespace (bom + parent + modules)
mvn -B -ntp deploy `
  "-Pactivity-master" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args
