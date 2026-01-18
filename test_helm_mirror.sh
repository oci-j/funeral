#!/bin/bash

# Test script for Helm mirror functionality

echo "Testing Helm mirror functionality..."
echo "======================================"

# Base URL - adjust as needed
BASE_URL="http://localhost:8911"

# Test 1: Mirror ChartMuseum chart (nginx)
echo ""
echo "Test 1: Mirror ChartMuseum chart (nginx)"
echo "-----------------------------------------"
curl -X POST "$BASE_URL/funeral_addition/mirror/helm/pull" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=chartmuseum" \
  -d "sourceRepo=https://charts.bitnami.com/bitnami" \
  -d "chartName=nginx" \
  -d "version=18.2.3" \
  -d "targetRepository=nginx-test" \
  -d "targetVersion=18.2.3" \
  | jq .

# Check if blobs were created properly
echo ""
echo "Checking blobs in registry..."
echo "------------------------------"
sleep 2  # Wait for storage to complete
curl -s "$BASE_URL/v2/_catalog" | jq .

# Check specific repository
echo ""
echo "Checking nginx-test repository..."
echo "----------------------------------"
curl -s "$BASE_URL/v2/nginx-test/tags/list" | jq .

# Check manifest for nginx-test:couchdb
MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" "$BASE_URL/v2/nginx-test/manifests/18.2.3")
echo ""
echo "Manifest for nginx-test:18.2.3..."
echo "------------------------------------"
echo "$MANIFEST" | jq .

# Extract and verify blob digests
CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_DIGESTS=$(echo "$MANIFEST" | jq -r '.layers[].digest')

echo ""
echo "Verifying blobs..."
echo "-------------------"
echo "Config digest: $CONFIG_DIGEST"
echo "Layer digests: $LAYER_DIGESTS"

# Test blob access
echo ""
echo "Testing config blob access..."
echo "------------------------------"
curl -s "$BASE_URL/v2/nginx-test/blobs/$CONFIG_DIGEST" | head -c 100

for digest in $LAYER_DIGESTS; do
  echo ""
  echo "Testing layer blob access ($digest)..."
  echo "----------------------------------------"
  curl -s "$BASE_URL/v2/nginx-test/blobs/$digest" | file -
done

# Test 2: Mirror OCI chart
echo ""
echo "Test 2: Mirror OCI chart"
echo "--------------------------"
echo "OCI format test would go here..."

echo ""
echo "Test completed!"
