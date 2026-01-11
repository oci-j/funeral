#!/bin/bash

# Sync frontend version from backend pom.xml
# This script reads version from pom.xml and updates package.json

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POM_FILE="${PROJECT_ROOT}/funeral-backend/pom.xml"
PACKAGE_JSON="${PROJECT_ROOT}/funeral-frontend/package.json"

echo "=== Syncing frontend version from backend pom.xml ==="

# Check if pom.xml exists
if [ ! -f "$POM_FILE" ]; then
    echo "ERROR: pom.xml not found at $POM_FILE"
    exit 1
fi

# Extract version from pom.xml
VERSION=$(grep -A 1 "<artifactId>funeral</artifactId>" "$POM_FILE" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')

if [ -z "$VERSION" ]; then
    echo "ERROR: Could not extract version from pom.xml"
    exit 1
fi

echo "Backend version: $VERSION"

# Check if package.json exists
if [ ! -f "$PACKAGE_JSON" ]; then
    echo "ERROR: package.json not found at $PACKAGE_JSON"
    exit 1
fi

# Backup package.json
cp "$PACKAGE_JSON" "$PACKAGE_JSON.bak"

# Update version in package.json
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS version
    sed -i '' "s/\"version\": \"[0-9]\+\.[0-9]\+\.[0-9]\+\"/\"version\": \"${VERSION}\"/" "$PACKAGE_JSON"
else
    # Linux version
    sed -i "s/\"version\": \"[0-9]\+\.[0-9]\+\.[0-9]\+\"/\"version\": \"${VERSION}\"/" "$PACKAGE_JSON"
fi

# Verify the update
if grep -q "\"version\": \"${VERSION}\"" "$PACKAGE_JSON"; then
    echo "✓ Frontend version synced to $VERSION"
else
    echo "ERROR: Failed to update frontend version"
    mv "$PACKAGE_JSON.bak" "$PACKAGE_JSON"
    exit 1
fi

# Remove backup
rm -f "$PACKAGE_JSON.bak"

# Also update the VERSION file to keep consistency
echo "$VERSION" > "${PROJECT_ROOT}/VERSION"
echo "✓ VERSION file updated to $VERSION"
