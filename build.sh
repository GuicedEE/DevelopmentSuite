#!/bin/bash

# Bootstrap independently before the GuicedEE BOM/parent reactor.
"$(dirname "$0")/mvnw" -B -ntp -f "$(dirname "$0")/GuicedEE/services/Maven/flatten-maven-plugin/pom.xml" install -DskipTests -Dmaven.javadoc.skip=true || exit $?

"$(dirname "$0")/mvnw" source:jar install \
  "-Dmaven.consumer.pom=false" \
  "-DskipTests" \
  "-Dmaven.javadoc.skip=true" \
  "-Pguicedee-boms,jwebmp-boms,guicedee,services,entityassist,jwebmp,activity-master" \
  -T 8 \
  "$@"

