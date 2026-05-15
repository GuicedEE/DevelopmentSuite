#!/bin/bash

mvn source:jar install \
  "-DskipTests" \
  "-Pjwebmp" \
  -T 8 \
  "$@"

