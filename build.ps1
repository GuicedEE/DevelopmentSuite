# Bootstrap the independent build tool before loading the BOM/parent reactor.
& (Join-Path $PSScriptRoot 'mvnw.cmd') -B -ntp -f (Join-Path $PSScriptRoot 'GuicedEE/services/Maven/flatten-maven-plugin/pom.xml') install '-DskipTests' '-Dmaven.javadoc.skip=true'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# The GuicedEE parent binds sources at package and Javadocs at verify.
# Running the install lifecycle generates both once and preserves shaded-source preparation order.
# Refresh JPMS descriptor timestamps so incremental javac records the current project version.
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'GuicedEE') -Recurse -Filter 'module-info.java' |
  ForEach-Object { [System.IO.File]::SetLastWriteTimeUtc($_.FullName, [DateTime]::UtcNow) }

& (Join-Path $PSScriptRoot 'mvnw.cmd') install `
  "-DskipTests" `
  "-Pguicedee-boms,jwebmp-boms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args

exit $LASTEXITCODE
