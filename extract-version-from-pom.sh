#!/bin/bash

# Extract version from backend pom.xml for frontend use

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POM_FILE="${PROJECT_ROOT}/funeral-backend/pom.xml"

# Check if pom.xml exists
if [ ! -f "$POM_FILE" ]; then
    echo "ERROR: pom.xml not found at $POM_FILE"
    exit 1
fi

# Extract version from pom.xml (version after artifactId)
VERSION=$(grep -A 1 "<artifactId>funeral</artifactId>" "$POM_FILE" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')

if [ -z "$VERSION" ]; then
    echo "ERROR: Could not extract version from pom.xml"
    exit 1
fi

echo "$VERSION"
