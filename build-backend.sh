#!/usr/bin/env bash
set -euo pipefail

M2=/mnt/c/Users/GedMarc/.m2/repository
BE=/mnt/c/Java/DevSuite/GuicedEE/website-backend
JL=/mnt/c/Java/DevSuite/GuicedEE/website-backend-jlink

echo "=== [1/2] building website-backend jar ==="
cd "$BE"
mvn -q -DskipTests -nsu -Dmaven.repo.local="$M2" install

echo "=== [2/2] building website-backend-jlink (Linux runtime) ==="
cd "$JL"
mvn -q -DskipTests -nsu -Dmaven.repo.local="$M2" install

echo "=== JLINK_DONE ==="
ls -la "$JL/target/maven-jlink/default/bin" | head
file "$JL/target/maven-jlink/default/bin/java" || true

