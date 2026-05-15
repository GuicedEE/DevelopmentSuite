#!/bin/bash

set -e

# Clean .locks directories from project-local-repo (Maven 4 artifact)
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Maven 4 file-locking creates .locks dirs inside central-staging which corrupts Central bundles
noLocks="-Daether.syncContext.named.factory=noop"

# Deploy Versioner BOM first (foundation for all other BOMs)
mvn -B -ntp clean deploy \
  --file GuicedEE/bom/Versioner/pom.xml \
  -DskipTests "-Dmaven.consumer.pom=false" \
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
  "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
  "$noLocks" \
  -U \
  "$@"

# Clean .locks again before batch deploy
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Deploy all GuicedEE BOMs + parent individually (avoids reactor staging issues)
echo "──── Deploying GuicedEE BOMs + Parent ────"

guicedeeBoms=(
  "GuicedEE/bom/StandaloneBOM/pom.xml"
  "GuicedEE/bom/TestLayoutBOM/pom.xml"
  "GuicedEE/bom/SwaggerBOM/pom.xml"
  "GuicedEE/bom/JBossBOM/pom.xml"
  "GuicedEE/bom/JakartaBOM/pom.xml"
  "GuicedEE/bom/HibernateBOM/pom.xml"
  "GuicedEE/bom/GoogleBOM/pom.xml"
  "GuicedEE/bom/FasterXMLBOM/pom.xml"
  "GuicedEE/bom/ApacheBOM/pom.xml"
  "GuicedEE/bom/ApacheCXFBOM/pom.xml"
  "GuicedEE/bom/SmallRyeBOM/pom.xml"
  "GuicedEE/bom/pom.xml"
  "GuicedEE/parent/pom.xml"
)

for pom in "${guicedeeBoms[@]}"; do
  echo "──── Deploying $pom ────"
  find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true
  mvn -B -ntp clean deploy \
    --file "$pom" \
    -DskipTests "-Dmaven.consumer.pom=false" \
    "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
    "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
    "$noLocks" \
    -U "$@"
done

# Clean .locks before GuicedEE modules deploy
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Batch deploy GuicedEE modules namespace (modules + services + entityassist)
mvn -B -ntp deploy \
  "-Pguicedee,services,entityassist" \
  -DskipTests "-Dmaven.consumer.pom=false" \
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
  "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
  "$noLocks" \
  -U \
  "$@"

# Clean .locks before JWebMP deploy
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Deploy JWebMP BOMs individually
jwebmpBoms=(
  "JWebMP/bom/pom.xml"
  "JWebMP/parent/pom.xml"
)

for pom in "${jwebmpBoms[@]}"; do
  echo "──── Deploying $pom ────"
  find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true
  mvn -B -ntp deploy \
    --file "$pom" \
    -DskipTests "-Dmaven.consumer.pom=false" \
    "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
    "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
    "$noLocks" \
    -U "$@"
done

# Clean .locks before JWebMP modules deploy
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Batch deploy JWebMP modules namespace
mvn -B -ntp clean deploy \
  "-Pjwebmp" \
  -DskipTests "-Dmaven.consumer.pom=false" \
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
  "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
  "$noLocks" \
  -U \
  "$@"

# Clean .locks before Activity Master deploy
find . -type d -name ".locks" 2>/dev/null | xargs rm -rf 2>/dev/null || true

# Batch deploy Activity Master namespace (bom + parent + modules)
mvn -B -ntp deploy \
  "-Pactivity-master" \
  -DskipTests "-Dmaven.consumer.pom=false" \
  "-Dcentral.publishing.skip=false" "-Dmaven.deploy.skip=true" \
  "-Dgpg.passphrase=$MAVEN_GPG_PASSPHRASE" \
  "$noLocks" \
  -U \
  "$@"

# -- Create Git Tag & Release (non-SNAPSHOT only) ----------
version=$(grep -oP '<version>\K[^<]+' pom.xml | head -1)

if [[ ! "$version" =~ SNAPSHOT ]]; then
  tag="v$version"

  if ! git tag -l "$tag" | grep -q "$tag"; then
    echo "──── Creating tag $tag ────"

    # Build release notes from previous tag
    prevTag=$(git describe --tags --abbrev=0 2>/dev/null || true)
    if [ -n "$prevTag" ]; then
      notes=$(git --no-pager log "$prevTag..HEAD" --pretty=format:"- %s (%h)" | sed 's/$/\\n/' | tr -d '\n')
      if [ -z "$notes" ]; then
        notes="Release $version"
      fi
    else
      notes="Initial release $version"
    fi

    git tag -a "$tag" -m "Release $version"
    git push origin "$tag"

    # Create GitHub release if gh CLI is available
    if command -v gh &> /dev/null; then
      gh release create "$tag" --title "Release $version" --notes "$notes"
    else
      echo "gh CLI not found - tag pushed but GitHub Release not created"
    fi
  else
    echo "Tag $tag already exists, skipping release creation"
  fi
else
  echo "SNAPSHOT version ($version) - skipping tag/release"
fi

