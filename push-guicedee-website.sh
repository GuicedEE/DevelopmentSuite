#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POM_FILE="$ROOT_DIR/GuicedEE/website/pom.xml"
LOGIN_SCRIPT="$ROOT_DIR/dockerhub-login.sh"

if [ ! -f "$POM_FILE" ]; then
  echo "Could not find $POM_FILE" >&2
  exit 1
fi

if [ -f "$HOME/.bashrc" ]; then
  # Load DOCKERHUB_USERNAME and DOCKERHUB_PAT from the user's shell config.
  # shellcheck disable=SC1090
  source "$HOME/.bashrc"
fi

: "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME is not set. Add it to ~/.bashrc or export it before running.}"

POM_IMAGE="$(
  sed -n 's:.*<dockerImageName>\(.*\)</dockerImageName>.*:\1:p' "$POM_FILE" | head -n 1
)"

if [ -z "$POM_IMAGE" ]; then
  echo "Could not find <dockerImageName> in $POM_FILE" >&2
  exit 1
fi

IMAGE_NAME_AND_TAG="${POM_IMAGE#*/}"
PUSH_IMAGE="$DOCKERHUB_USERNAME/$IMAGE_NAME_AND_TAG"

"$LOGIN_SCRIPT"

mvn -f "$POM_FILE" -DskipTests install

if [ "$POM_IMAGE" != "$PUSH_IMAGE" ]; then
  docker tag "$POM_IMAGE" "$PUSH_IMAGE"
fi

docker push "$PUSH_IMAGE"

