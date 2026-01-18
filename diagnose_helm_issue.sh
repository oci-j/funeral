#!/bin/bash

echo "=== HELM PULL ISSUE DIAGNOSTIC ==="
echo ""

# Check 1: Backend running and responding
echo "1. Checking if backend is running..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null 2>&1; then
    echo "✓ Backend is responding"
else
    echo "✗ Backend is NOT responding"
    echo "  Please start backend: cd funeral-backend && mvn quarkus:dev"
    exit 1
fi
echo ""

# Check 2: Check for existing manifest
echo "2. Checking for existing bitnami/mongodb manifest..."
EXISTING=$(curl -s http://localhost:8911/v2/bitnami/mongodb/tags/list)
echo "Existing tags: $EXISTING"

if echo "$EXISTING" | grep -q "12.1.31"; then
    echo "✓ Found existing tag 12.1.31"
    echo ""
    echo "3. Fetching manifest content..."
    MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
        http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

    echo "Raw manifest:"
    echo "$MANIFEST" | jq .

    # Count descriptors
    CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
    LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

    echo ""
    echo "Descriptor Analysis:"
    echo "  Config digest: $CONFIG_DIGEST"
    echo "  Layer count: $LAYER_COUNT"

    if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
        echo ""
        echo "=== ISSUE FOUND: Missing config descriptor! ==="
        echo "The manifest is missing the config section."
        echo ""
        echo "Possible causes:"
        echo "  1. Old code version still running (not restarted after fix)"
        echo "  2. Manifest stored before the OCI index fix was applied"
        echo "  3. Code not properly compiled/redeployed"
        echo ""
        echo "SOLUTION:"
        echo "  1. Stop backend (Ctrl+C)"
        echo "  2. cd funeral-backend && mvn clean compile quarkus:dev"
        echo "  3. In another terminal: ./cleanup_manifests.sh"
        echo "  4. Re-mirror the chart"
        exit 1
    fi

    if [ "$LAYER_COUNT" -eq 0 ]; then
        echo ""
        echo "=== ISSUE FOUND: Missing layer descriptors! ==="
        echo "The manifest has no layers."
        exit 1
    fi

else
    echo "✗ No existing manifest found"
    echo "You need to mirror the chart first!"
    exit 1
fi

echo ""
echo "4. Checking config blob accessibility..."
if [ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8911/v2/bitnami/mongodb/blobs/$CONFIG_DIGEST)

    if [ "$HTTP_CODE" = "200" ]; then
        echo "✓ Config blob is accessible (HTTP 200)"
    else
        echo "✗ Config blob NOT accessible (HTTP $HTTP_CODE)"
    fi
fi

echo ""
echo "5. Checking layer blobs..."
echo "$MANIFEST" | jq -r '.layers[].digest' | while read digest; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8911/v2/bitnami/mongodb/blobs/$digest)

    if [ "$HTTP_CODE" = "200" ]; then
        echo "  ✓ Layer $digest accessible"
    else
        echo "  ✗ Layer $digest NOT accessible (HTTP $HTTP_CODE)"
    fi
done

echo ""
echo "6. Testing Helm pull command..."
helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http 2>&1

if [ $? -eq 0 ]; then
    echo ""
    echo "=== SUCCESS: Helm pull works! ==="
else
    echo ""
    echo "=== ISSUE: Helm pull still failing ==="
    echo ""
    echo "If you see 'descriptors found: 1', the manifest structure is incomplete."
    echo "The stored manifest has issues that persist even after our fixes."
    echo ""
    echo "Try this complete reset:"
    echo "  1. Stop backend"
    echo "  2. rm -rf funeral-backend/target"
    echo "  3. mvn clean compile quarkus:dev"
    echo "  4. Check MongoDB and clear manifests collection"
    echo "  5. Re-mirror the chart"
fi

echo ""
echo "=== DIAGNOSTIC COMPLETE ==="
