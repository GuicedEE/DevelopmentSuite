#!/bin/bash

mvn source:jar install \
  "-DskipTests" \
  "-Dmaven.javadoc.skip=true" \
  "-Pjwebmp" \
  -T 8 \
  "$@"

