#!/usr/bin/env bash
# Rebuild the website-backend jlink runtime image under Linux.
#
# WHY WSL AND NOT WINDOWS
# -----------------------
# website-backend-jlink/Dockerfile COPYs target/maven-jlink/default/{bin,conf,lib,release}
# straight into debian:trixie-slim. A jlink image produced on Windows contains Windows
# binaries (java.exe, ...) and is useless in that container, so the runtime MUST be linked
# on Linux/amd64.
#
# WHY SUDO
# --------
# jlink sets the executable bit on the launchers it emits. Under WSL the Windows drives are
# mounted as DrvFs, and without elevation that chmod is refused:
#
#   Error: java.io.UncheckedIOException: java.nio.file.FileSystemException:
#     .../target/maven-jlink/default/bin/java: Operation not permitted
#
# Running the build with sudo is what makes the permission change succeed on /mnt/c.
#
# WHICH DISTRO
# ------------
# The default WSL distro (Ubuntu-24.04) currently fails to attach its ext4.vhdx:
#   Wsl/Service/CreateInstance/MountDisk/HCS/ERROR_PATH_NOT_FOUND
# so invoke this via the working distro explicitly, e.g. from PowerShell:
#
#   wsl -d Ubuntu -e bash /mnt/c/Java/DevSuite/build-jlink.sh
#
# NOTE ON VERSIONS
# ----------------
# website-backend and website-backend-jlink use 2.4.0 with GuicedEE 2.4.0.
# Install the release reactor and backend before packaging the runtime image.

set -euo pipefail
M2=/mnt/c/Users/GedMarc/.m2/repository
JL=/mnt/c/Java/DevSuite/GuicedEE/website-backend-jlink

if [ "$(id -u)" -ne 0 ]; then
  echo "jlink needs to set the executable bit on its launchers, which DrvFs (/mnt/c)"
  echo "refuses unelevated - re-running under sudo..."
  exec sudo -E "$0" "$@"
fi

cd "$JL"
rm -rf target/maven-jlink
mvn -B -ntp -DskipTests -nsu -Dmaven.repo.local="$M2" install
echo "JLINK_REBUILD_DONE"
# Sanity: the runtime must be Linux (no .exe) and carry the module image.
ls "$JL/target/maven-jlink/default/bin"
ls "$JL/target/maven-jlink/default/lib" | grep -i modules || true
