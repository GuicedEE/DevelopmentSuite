#!/bin/sh
# Maven Wrapper - Downloads and runs Maven 4.0.0-rc-5

set -e

MAVEN_VERSION="4.0.0-rc-5"
MAVEN_HOME="${HOME}/.m2/apache-maven-${MAVEN_VERSION}"
MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"

# Create .m2 directory if it doesn't exist
mkdir -p "${HOME}/.m2"

# Download and extract Maven if not already present
if [ ! -f "${MAVEN_BIN}" ]; then
    MAVEN_ZIP="${HOME}/.m2/apache-maven-${MAVEN_VERSION}-bin.zip"
    echo "Downloading Maven ${MAVEN_VERSION}..."

    if command -v curl > /dev/null; then
        curl -L -o "${MAVEN_ZIP}" "${MAVEN_URL}"
    elif command -v wget > /dev/null; then
        wget -O "${MAVEN_ZIP}" "${MAVEN_URL}"
    else
        echo "Error: Neither curl nor wget found. Please install one of them." >&2
        exit 1
    fi

    echo "Extracting Maven..."
    cd "${HOME}/.m2"
    unzip -q "${MAVEN_ZIP}"
    rm "${MAVEN_ZIP}"

    echo "Maven ${MAVEN_VERSION} installed to ${MAVEN_HOME}"
fi

# Execute Maven
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m}"
exec "${MAVEN_BIN}" "$@"


