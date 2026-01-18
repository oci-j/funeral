#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  Testing ChartMuseum Format - bitnami/mongodb:12.1.31        ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

# Step 1: Check backend
echo "Step 1: Verifying backend..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null 2>&1; then
    print_success "Backend is running"
else
    print_error "Backend not responding!"
    exit 1
fi
echo ""

# Step 2: Mirror the ChartMuseum chart
echo "Step 2: Mirroring ChartMuseum chart..."
echo ""
echo "Parameters:"
echo "  - Format: ChartMuseum"
echo "  - Source: https://charts.bitnami.com/bitnami"
echo "  - Chart: bitnami/mongodb"
echo "  - Version: 12.1.31"
echo " "

curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=chartmuseum" \
  -d "sourceRepo=https://charts.bitnami.com/bitnami" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=test-mongodb-cm" \
  -d "targetVersion=12.1.31" | jq .

if [ $? -ne 0 ]; then
    print_error "Failed to send mirror request"
    exit 1
fi

print_success "Mirror request completed"
echo ""

# Wait for storage
echo "Waiting for storage to complete..."
sleep 5
echo ""

# Step 3: Verify manifest structure
echo "Step 3: Checking manifest structure..."

MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    http://localhost:8911/v2/test-mongodb-cm/manifests/12.1.31)

CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
    print_error "Config digest is null!"
    echo "Manifest:"
    echo "$MANIFEST" | jq .
    exit 1
fi

if [ "$LAYER_COUNT" -eq 0 ]; then
    print_error "No layers found!"
    exit 1
fi

print_success "Manifest structure is correct"
echo "  Config: $CONFIG_DIGEST"
echo "  Layers: $LAYER_COUNT"
echo ""

# Step 4: Test Helm pull
echo "Step 4: Testing Helm pull for ChartMuseum-mirrored chart..."
echo ""
echo "Command: helm pull oci://192.168.8.9:8911/test-mongodb-cm --version 12.1.31 --plain-http"
echo ""

helm pull oci://192.168.8.9:8911/test-mongodb-cm --version 12.1.31 --plain-http

if [ $? -eq 0 ]; then
    print_success "SUCCESS! ChartMuseum format works!"
    echo ""

    if [ -f "test-mongodb-cm-12.1.31.tgz" ]; then
        echo "Downloaded chart file:"
        ls -lh test-mongodb-cm-12.1.31.tgz
    fi

    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║  ChartMuseum format mirroring is WORKING correctly!          ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
else
    print_error "FAILED! ChartMuseum format still has issues"
    echo ""
    echo "Possible reasons:"
    echo "  1. URL was not built correctly (check backend logs)"
    echo "  2. Chart download failed"
    echo "  3. Blobs were not stored properly"
    exit 1
fi

echo ""
