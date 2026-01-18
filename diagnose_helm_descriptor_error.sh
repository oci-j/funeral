#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║           Diagnosing 'descriptors found: 1' Error                  ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

echo "1. Checking Helm version..."
HELM_VERSION=$(helm version --short 2>&1)
echo "   Helm version: $HELM_VERSION"
echo ""

echo "2. Fetching manifest directly from registry..."
echo ""

# Get token
TOKEN_RESPONSE=$(curl -s -X POST "http://localhost:8911/v2/token?scope=repository:bitnami/mongodb:pull")
TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "   ✗ Failed to get auth token"
  exit 1
fi

echo "   ✓ Got auth token"
echo ""

# Fetch manifest
MANIFEST=$(curl -s -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.oci.image.manifest.v1+json" http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

echo "3. Raw manifest content:"
echo "$MANIFEST" | jq .
echo ""

# Validate structure
echo "4. Validating manifest structure..."
SCHEMA_VERSION=$(echo "$MANIFEST" | jq -r '.schemaVersion // "missing"')
MEDIA_TYPE=$(echo "$MANIFEST" | jq -r '.mediaType // "missing"')
CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest // "missing"')
CONFIG_MEDIA_TYPE=$(echo "$MANIFEST" | jq -r '.config.mediaType // "missing"')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "   Schema Version: $SCHEMA_VERSION"
echo "   Media Type: $MEDIA_TYPE"
echo "   Config Digest: $CONFIG_DIGEST"
echo "   Config Media Type: $CONFIG_MEDIA_TYPE"
echo "   Layer Count: $LAYER_COUNT"
echo ""

# Count total descriptors
TOTAL_DESCRIPTORS=0
if [ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "missing" ]; then
  TOTAL_DESCRIPTORS=$((TOTAL_DESCRIPTORS + 1))
fi

if [ "$LAYER_COUNT" != "null" ]; then
  TOTAL_DESCRIPTORS=$((TOTAL_DESCRIPTORS + LAYER_COUNT))
fi

echo "   Total descriptors found: $TOTAL_DESCRIPTORS"
echo ""

# Check if there are issues
if [ "$TOTAL_DESCRIPTORS" -lt 2 ]; then
  echo "   ⚠ ONLY $TOTAL_DESCRIPTORS descriptors found!"
  echo "   Helm expects at least 2 (config + at least 1 layer)"
else
  echo "   ✓ Sufficient descriptors found"
fi

echo ""
echo "5. Checking config blob..."
if [ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "missing" ]; then
  CONFIG_BLOB=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8911/v2/bitnami/mongodb/blobs/$CONFIG_DIGEST)
  CONFIG_SIZE=$(echo "$CONFIG_BLOB" | wc -c)
  echo "   Config blob size: $CONFIG_SIZE bytes"

  if [ "$CONFIG_SIZE" -lt 10 ]; then
    echo "   ⚠ Config blob is suspiciously small!"
  else
    echo "   ✓ Config blob has content"
  fi
fi

echo ""
echo "6. Checking layer blobs..."
if [ "$LAYER_COUNT" -gt 0 ]; then
  echo "$MANIFEST" | jq -r '.layers[].digest' | while read digest; do
    LAYER_BLOB=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8911/v2/bitnami/mongodb/blobs/$digest)
    LAYER_SIZE=$(echo "$LAYER_BLOB" | wc -c)
    echo "   Layer $digest: $LAYER_SIZE bytes"
  done
fi

echo ""
echo "7. Testing Helm pull with verbose output..."
echo ""
helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http 2>&1 | while IFS= read -r line; do
  echo "   $line"
done

echo ""
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                     DIAGNOSIS COMPLETE                              ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Key findings:"
echo "- Total descriptors in manifest: $TOTAL_DESCRIPTORS"
echo "- Config present: $([ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "missing" ] && echo 'Yes' || echo 'No')"
echo "- Layers present: $([ "$LAYER_COUNT" -gt 0 ] && echo 'Yes' || echo 'No')"
echo ""
if [ "$TOTAL_DESCRIPTORS" -lt 2 ]; then
  echo "⚠ MANIFEST TOO SMALL - This is why Helm fails!"
  echo ""
  echo "Possible causes:"
  echo "1. Manifest was stored with incorrect structure"
  echo "2. Blobs were not stored properly"
  echo "3. Backend code still has bugs"
else
  echo "✓ Manifest structure looks correct"
  echo ""
  echo "If Helm still fails, possible reasons:"
  echo "1. Helm version incompatibility"
  echo "2. Network/proxy issues"
  echo "3. Registry configuration issues"
  echo "4. Manifest validation in Helm is stricter than expected"
fi
echo ""
