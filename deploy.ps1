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

# Batch deploy GuicedEE namespace (boms + modules + services + entityassist)
mvn -B -ntp clean deploy `
  "-Pguicedee-boms,guicedee,services,entityassist" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "GuicedEE deploy failed"; exit $LASTEXITCODE }

# Clean .locks before JWebMP deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy JWebMP namespace (boms + modules)
mvn -B -ntp clean deploy `
  "-Pjwebmp-boms,jwebmp" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args

if ($LASTEXITCODE -ne 0) { Write-Host "JWebMP deploy failed"; exit $LASTEXITCODE }

# Clean .locks before Activity Master deploy
Get-ChildItem -Path . -Filter ".locks" -Recurse -Force -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Batch deploy Activity Master namespace (bom + parent + modules)
mvn -B -ntp clean deploy `
  "-Pactivity-master" `
  -DskipTests `
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" `
  "-Dgpg.passphrase=$env:MAVEN_GPG_PASSPHRASE" `
  -U `
  @args
