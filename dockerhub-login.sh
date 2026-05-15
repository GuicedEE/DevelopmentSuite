#!/bin/bash
set -euo pipefail

if [ -f "$HOME/.bashrc" ]; then
  # Load DOCKERHUB_USERNAME and DOCKERHUB_PAT from the user's shell config.
  # shellcheck disable=SC1090
  source "$HOME/.bashrc"
fi

: "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME is not set. Add it to ~/.bashrc or export it before running.}"
: "${DOCKERHUB_PAT:?DOCKERHUB_PAT is not set. Add it to ~/.bashrc or export it before running.}"

export DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker-jwebmp-push}"
mkdir -p "$DOCKER_CONFIG"
chmod 700 "$DOCKER_CONFIG"

if [ ! -f "$DOCKER_CONFIG/config.json" ]; then
  printf '{}\n' > "$DOCKER_CONFIG/config.json"
fi

printf '%s\n' "$DOCKERHUB_PAT" | docker login --username "$DOCKERHUB_USERNAME" --password-stdin

