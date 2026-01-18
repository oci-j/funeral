#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║   ChartMuseum Mirroring Issue - Comprehensive Debug Tool          ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# Check if repository exists
echo "1. Checking if repository exists in registry..."
REPO_EXISTS=$(curl -s http://localhost:8911/v2/bitnami/mongodb/tags/list)
echo "   Response: $REPO_EXISTS"

if echo "$REPO_EXISTS" | grep -q "errors"; then
    echo "   ✗ Repository not found or not accessible"
    echo ""
    echo "   PROBLEM: Chart has not been successfully mirrored yet!"
    echo ""
    echo "   SOLUTION: Mirror the chart first using:"
    echo "   curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \\"
    echo "     -H 'Content-Type: application/x-www-form-urlencoded' \\"
    echo "     -d 'format=chartmuseum' \\"
    echo "     -d 'sourceRepo=https://charts.bitnami.com/bitnami' \\"
    echo "     -d 'chartName=bitnami/mongodb' \\"
    echo "     -d 'version=12.1.31' \\"
    echo "     -d 'targetRepository=bitnami/mongodb' \\"
    echo "     -d 'targetVersion=12.1.31'"
    exit 1
fi

if echo "$REPO_EXISTS" | grep -q "12.1.31"; then
    echo "   ✓ Repository exists and has tag 12.1.31"
else
    echo "   ✗ Repository exists but tag 12.1.31 not found"
    echo "   Available tags: $(echo "$REPO_EXISTS" | jq -r '.tags // [] | join(", ")')"
fi
echo ""

# Get the manifest
echo "2. Fetching manifest details..."
MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
  http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

echo "   Raw manifest (first 500 chars):"
echo "   ${MANIFEST:0:500}..."
echo ""

# Check if manifest is valid JSON
if ! echo "$MANIFEST" | jq . > /dev/null 2>&1; then
    echo "   ✗ Manifest is not valid JSON!"
    echo ""
    echo "   PROBLEM: Corrupted or incomplete manifest stored"
    echo "   SOLUTION: Delete and re-mirror the chart"
    exit 1
fi

# Extract and validate structure
echo "3. Analyzing manifest structure..."

SCHEMA_VERSION=$(echo "$MANIFEST" | jq -r '.schemaVersion')
MEDIA_TYPE=$(echo "$MANIFEST" | jq -r '.mediaType')
CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
CONFIG_SIZE=$(echo "$MANIFEST" | jq -r '.config.size')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "   Schema Version: $SCHEMA_VERSION"
echo "   Media Type: $MEDIA_TYPE"
echo "   Config Digest: $CONFIG_DIGEST"
echo "   Config Size: $CONFIG_SIZE"
echo "   Layer Count: $LAYER_COUNT"
echo ""

# Check for issues
ISSUES_FOUND=0

if [ "$SCHEMA_VERSION" != "2" ]; then
    echo "   ⚠ Warning: Schema version is not 2 (OCI format)"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi

if [ "$MEDIA_TYPE" != "application/vnd.oci.image.manifest.v1+json" ]; then
    echo "   ⚠ Warning: Media type is not OCI manifest"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
fi

if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
    echo "   ✗ CRITICAL: Config digest is missing!"
    ISSUES_FOUND=$((ISSUES_FOUND + 10))
else
    # Check if config blob exists
    echo "   Checking config blob accessibility..."
    CONFIG_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
      http://localhost:8911/v2/bitnami/mongodb/blobs/$CONFIG_DIGEST)

    if [ "$CONFIG_HTTP" = "200" ]; then
        echo "   ✓ Config blob is accessible (HTTP 200)"
    else
        echo "   ✗ Config blob NOT accessible (HTTP $CONFIG_HTTP)"
        ISSUES_FOUND=$((ISSUES_FOUND + 5))
    fi
fi

if [ "$LAYER_COUNT" -eq 0 ]; then
    echo "   ✗ CRITICAL: No layers found!"
    ISSUES_FOUND=$((ISSUES_FOUND + 10))
else
    # Check each layer
    echo "   Checking layer blobs..."
    LAYER_ERROR=0
    echo "$MANIFEST" | jq -r '.layers[].digest' | while read digest; do
        LAYER_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
          http://localhost:8911/v2/bitnami/mongodb/blobs/$digest)

        if [ "$LAYER_HTTP" = "200" ]; then
            echo "   ✓ Layer accessible: ${digest:0:16}..."
        else
            echo "   ✗ Layer NOT accessible (HTTP $LAYER_HTTP): ${digest:0:16}..."
            LAYER_ERROR=1
        fi
    done

    if [ $LAYER_ERROR -eq 1 ]; then
        ISSUES_FOUND=$((ISSUES_FOUND + 5))
    fi
fi
echo ""

# Summary
echo "4. Summary:"
if [ $ISSUES_FOUND -eq 0 ]; then
    echo "   ✓ Manifest structure looks correct!"
    echo ""
    echo "   If Helm pull is still failing, the issue might be:"
    echo "   - Helm is caching an old/bad manifest (try helm repo update)"
    echo "   - Network connectivity issue"
    echo "   - Helm version incompatibility"
else
    echo "   ✗ Found $ISSUES_FOUND issues with the manifest!"
    echo ""

    if [ $ISSUES_FOUND -ge 10 ]; then
        echo "   CRITICAL ISSUES DETECTED:"
        echo "   The manifest is missing required config or layer descriptors."
        echo "   This means the chart was not properly mirrored."
        echo ""
        echo "   MOST LIKELY CAUSE:"
        echo "   1. Old backend code still running (not restarted after fix)"
        echo "   2. Chart download failed during mirroring"
        echo "   3. Manifest was stored before the fix was applied"
        echo ""
        echo "   SOLUTION:"
        echo "   1. Restart backend: cd funeral-backend && mvn clean compile quarkus:dev"
        echo "   2. Delete manifest: curl -X DELETE http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31"
        echo "   3. Re-mirror chart using UI or curl"
        echo "   4. Run this diagnostic again to verify"
    elif [ $ISSUES_FOUND -ge 5 ]; then
        echo "   MEDIUM ISSUES:"
        echo "   Blobs are missing or inaccessible."
        echo "   SOLUTION: Re-mirror the chart to ensure all blobs are stored."
    else
        echo "   MINOR ISSUES:"
        echo "   Non-critical warnings. Helm pull should still work."
    fi
fi
echo ""

# Test Helm directly
echo "5. Testing Helm pull directly..."
echo "   Command: helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http"
echo ""

helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http 2>&1
HELM_EXIT=$?

echo ""
if [ $HELM_EXIT -eq 0 ]; then
    echo "   ✓ Helm pull succeeded!"
else
    echo "   ✗ Helm pull failed with exit code $HELM_EXIT"
fi
echo ""

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                         DEBUG COMPLETE                              ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Next steps based on findings:"
echo ""

if [ $ISSUES_FOUND -ge 10 ]; then
    echo "1. STOP backend (Ctrl+C in terminal where it's running)"
    echo "2. cd funeral-backend && mvn clean compile quarkus:dev"
    echo "3. Wait for it to fully start"
    echo "4. Delete bad data:"
    echo "   curl -X DELETE http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31"
    echo "5. Re-mirror using UI or:"
    echo "   curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \\"
    echo "     -H 'Content-Type: application/x-www-form-urlencoded' \\"
    echo "     -d 'format=chartmuseum' \\"
    echo "     -d 'sourceRepo=https://charts.bitnami.com/bitnami' \\"
    echo "     -d 'chartName=bitnami/mongodb' \\"
    echo "     -d 'version=12.1.31' \\"
    echo "     -d 'targetRepository=bitnami/mongodb' \\"
    echo "     -d 'targetVersion=12.1.31'"
    echo "6. Run this script again to verify"
elif [ $ISSUES_FOUND -ge 5 ]; then
    echo "1. Re-mirror the chart"
    echo "2. Check backend logs for blob storage errors"
    echo "3. Run this script again"
else
    echo "1. If Helm pull still fails but manifest looks good:"
    echo "   - Try: helm repo update"
    echo "   - Check Helm version compatibility"
    echo "   - Verify network connectivity to registry"
fi
echo ""
