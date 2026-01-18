#!/bin/bash

echo "Manifest Debug Tool"
echo "=================="
echo ""

# Check what's actually stored for bitnami/mongodb
REPO="bitnami/mongodb"
TAG="12.1.31"

echo "1. Checking catalog..."
curl -s http://localhost:8911/v2/_catalog | jq .
echo ""

echo "2. Checking tags for $REPO..."
curl -s http://localhost:8911/v2/$REPO/tags/list | jq .
echo ""

echo "3. Fetching manifest with OCI accept header..."
MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    http://localhost:8911/v2/$REPO/manifests/$TAG)

echo "Manifest content:"
echo "$MANIFEST" | jq .
echo ""

# Count descriptors
CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "Descriptor analysis:"
echo "  Config digest: $CONFIG_DIGEST"
echo "  Number of layers: $LAYER_COUNT"
echo ""

if [ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "" ]; then
    echo "4. Checking config blob..."
    curl -s -I http://localhost:8911/v2/$REPO/blobs/$CONFIG_DIGEST
    echo ""
fi

if [ "$LAYER_COUNT" -gt 0 ]; then
    echo "5. Checking layer blobs..."
    echo "$MANIFEST" | jq -r '.layers[].digest' | while read -r digest; do
        echo "  Layer: $digest"
        curl -s -I http://localhost:8911/v2/$REPO/blobs/$digest
        echo ""
    done
fi

echo "6. Checking if manifest exists in MongoDB..."
curl -s "http://localhost:8911/funeral_addition/debug/manifest?repository=$REPO&tag=$TAG" | jq .
echo ""

# Debug the actual stored manifest
echo "=================="
echo "ANALYSIS:"
echo "If config.digest is null/empty or layers count is 0,"
echo "then the manifest wasn't stored correctly."
echo ""
echo "Expected: config.digest != null AND layers.count >= 1"
echo "Actual: config.digest=$CONFIG_DIGEST, layers.count=$LAYER_COUNT"
