# Clean .locks directories from project-local-repo (Maven 4 artifact)
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Maven 4 file-locking creates .locks dirs inside central-staging which corrupts Central bundles
$noLocks = "-Daether.syncContext.named.factory=noop"

# Deploy Versioner BOM first (foundation for all other BOMs)
mvn -B -ntp clean deploy `
  --file GuicedEE/bom/Versioner/pom.xml `
  -DskipTests "-Dmaven.consumer.pom=false" `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  $noLocks `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "Versioner deploy failed"; exit $LASTEXITCODE }

# Clean .locks again before batch deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Deploy all GuicedEE BOMs + parent in a single reactor (single Central deployment bundle)
Write-Host "──── Deploying GuicedEE BOMs + Parent (single bundle) ────"
mvn -B -ntp deploy `
  --file GuicedEE/deploy-boms.xml `
  -DskipTests "-Dmaven.consumer.pom=false" `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  $noLocks `
  -U @args

if ($LASTEXITCODE -ne 0) { Write-Host "GuicedEE BOMs deploy failed"; exit $LASTEXITCODE }

# Clean .locks before GuicedEE modules deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy GuicedEE modules namespace (modules + services + entityassist)
mvn -B -ntp deploy `
  "-Pguicedee,services,entityassist" `
  -DskipTests "-Dmaven.consumer.pom=false" `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  $noLocks `
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
    -DskipTests "-Dmaven.consumer.pom=false" `
    "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
    "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
    $noLocks `
    -U @args
  if ($LASTEXITCODE -ne 0) { Write-Host "$pom deploy failed"; exit $LASTEXITCODE }
}

# Clean .locks before JWebMP modules deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy JWebMP modules namespace
mvn -B -ntp clean deploy `
  "-Pjwebmp" `
  -DskipTests "-Dmaven.consumer.pom=false" `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  $noLocks `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "JWebMP deploy failed"; exit $LASTEXITCODE }

# Clean .locks before Activity Master deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy Activity Master namespace (bom + parent + modules)
mvn -B -ntp deploy `
  "-Pactivity-master" `
  -DskipTests "-Dmaven.consumer.pom=false" `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  $noLocks `
  -U `
  @args
