# The GuicedEE parent binds sources at package and Javadocs at verify.
# Running the install lifecycle generates both once and preserves shaded-source preparation order.
# Refresh JPMS descriptor timestamps so incremental javac records the current project version.
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'GuicedEE') -Recurse -Filter 'module-info.java' |
  ForEach-Object { [System.IO.File]::SetLastWriteTimeUtc($_.FullName, [DateTime]::UtcNow) }

mvn install `
  "-DskipTests" `
  "-Pguicedee-boms,jwebmp-boms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args

exit $LASTEXITCODE
