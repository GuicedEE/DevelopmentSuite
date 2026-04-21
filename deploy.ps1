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

# Batch deploy GuicedEE BOMs namespace
mvn -B -ntp deploy `
  "-Pguicedee-boms" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "GuicedEE BOMs deploy failed"; exit $LASTEXITCODE }

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

# Batch deploy JWebMP BOMs namespace
mvn -B -ntp deploy `
  "-Pjwebmp-boms" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "JWebMP BOMs deploy failed"; exit $LASTEXITCODE }

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
