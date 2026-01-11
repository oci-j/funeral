#!/bin/bash

# Version Sync Script for FUNERAL
# This script synchronizes the version across backend (Maven) and frontend (npm)
# Can use either VERSION file or pom.xml as source

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION_FILE="${PROJECT_ROOT}/VERSION"
BACKEND_POM="${PROJECT_ROOT}/funeral-backend/pom.xml"
FRONTEND_PACKAGE="${PROJECT_ROOT}/funeral-frontend/package.json"

echo "=== FUNERAL Version Sync ==="

# Check command line argument for source preference
USE_POM=false
if [ "$1" = "--from-pom" ]; then
    USE_POM=true
fi

# Determine version source
if [ "$USE_POM" = true ] && [ -f "$BACKEND_POM" ]; then
    # Read version from pom.xml
    VERSION=$(grep -A 1 "<artifactId>funeral</artifactId>" "$BACKEND_POM" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')
    echo "Source: pom.xml"
elif [ -f "$VERSION_FILE" ]; then
    # Read version from VERSION file
    VERSION=$(cat "$VERSION_FILE" | tr -d '[:space:]')
    echo "Source: VERSION file"
else
    echo "ERROR: No version source found. Please ensure either VERSION file or pom.xml exists."
    exit 1
fi

if [ -z "$VERSION" ]; then
    echo "ERROR: Could not extract version"
    exit 1
fi

echo "Target version: $VERSION"

# Update backend pom.xml if it exists - only update the project version (first occurrence after artifactId)
if [ -f "$BACKEND_POM" ]; then
    echo "Updating backend version in $BACKEND_POM..."

    # Backup original file
    cp "$BACKEND_POM" "$BACKEND_POM.bak"

    # Extract current version for verification
    CURRENT_VERSION=$(grep -A 1 "<artifactId>funeral</artifactId>" "$BACKEND_POM" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

    # Update only the project version (the one right after artifactId)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS version - more specific replacement
        sed -i '' "/<artifactId>funeral<\/artifactId>/,/<version>/ s/<version>[^<]*<\/version>/<version>${VERSION}<\/version>/" "$BACKEND_POM"
    else
        # Linux version - more specific replacement
        sed -i "/<artifactId>funeral<\/artifactId>/,/<version>/ s/<version>[^<]*<\/version>/<version>${VERSION}<\/version>/" "$BACKEND_POM"
    fi

    # Verify the update by checking the first version after artifactId
    UPDATED_VERSION=$(grep -A 1 "<artifactId>funeral</artifactId>" "$BACKEND_POM" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

    if [ "$UPDATED_VERSION" = "$VERSION" ]; then
        echo "✓ Backend version updated successfully (from $CURRENT_VERSION to $VERSION)"
    else
        echo "ERROR: Failed to update backend version"
        mv "$BACKEND_POM.bak" "$BACKEND_POM"
        exit 1
    fi

    # Remove backup
    rm -f "$BACKEND_POM.bak"
else
    echo "WARNING: Backend pom.xml not found at $BACKEND_POM"
fi

# Update frontend package.json if it exists
if [ -f "$FRONTEND_PACKAGE" ]; then
    echo "Updating frontend version in $FRONTEND_PACKAGE..."

    # Backup original file
    cp "$FRONTEND_PACKAGE" "$FRONTEND_PACKAGE.bak"

    # Update version using sed
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS version
        sed -i '' "s/\"version\": \"[0-9]\+\.[0-9]\+\.[0-9]\+\"/\"version\": \"${VERSION}\"/" "$FRONTEND_PACKAGE"
    else
        # Linux version
        sed -i "s/\"version\": \"[0-9]\+\.[0-9]\+\.[0-9]\+\"/\"version\": \"${VERSION}\"/" "$FRONTEND_PACKAGE"
    fi

    # Verify the update
    if grep -q "\"version\": \"${VERSION}\"" "$FRONTEND_PACKAGE"; then
        echo "✓ Frontend version updated successfully"
    else
        echo "ERROR: Failed to update frontend version"
        mv "$FRONTEND_PACKAGE.bak" "$FRONTEND_PACKAGE"
        exit 1
    fi

    # Remove backup
    rm -f "$FRONTEND_PACKAGE.bak"
else
    echo "WARNING: Frontend package.json not found at $FRONTEND_PACKAGE"
fi

echo ""
echo "=== Version sync completed successfully ==="
echo "All components are now at version $VERSION"
