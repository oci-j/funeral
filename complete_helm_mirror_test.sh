#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║          HELM CHART MIRRORING - COMPLETE SOLUTION            ║"
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

# Check if backend is running
echo "Step 1: Verifying backend..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null 2>&1; then
    print_success "Backend is running"
else
    print_error "Backend is NOT running!"
    echo ""
    echo "Please start the backend in another terminal:"
    echo "  cd funeral-backend && mvn clean compile quarkus:dev"
    echo ""
    echo "Then run this script again."
    exit 1
fi
echo ""

# Step 2: Mirror the chart
echo "Step 2: Mirroring bitnami/mongodb:12.1.31..."
echo "This will download the chart from Docker Hub and store it..."
echo ""

echo "Sending mirror request..."
RESPONSE=$(curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=oci" \
  -d "sourceRepo=registry-1.docker.io" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=bitnami/mongodb" \
  -d "targetVersion=12.1.31")

# Check if response has success=true
SUCCESS=$(echo "$RESPONSE" | jq -r '.success' 2>/dev/null)

if [ "$SUCCESS" != "true" ]; then
    print_error "Mirror request failed!"
    echo ""
    echo "Response:"
    echo "$RESPONSE" | jq .
    echo ""
    echo "Check backend logs for errors."
    exit 1
fi

print_success "Chart mirrored successfully!"
echo ""

# Extract info from response
CHART=$(echo "$RESPONSE" | jq -r '.chart')
VERSION=$(echo "$RESPONSE" | jq -r '.version')
BLOBS=$(echo "$RESPONSE" | jq -r '.blobsCount')
DIGEST=$(echo "$RESPONSE" | jq -r '.digest')

print_success "Chart: $CHART:$VERSION"
print_success "Blobs stored: $BLOBS"
print_success "Digest: $DIGEST"
echo ""

# Step 3: Verify the manifest structure
echo "Step 3: Verifying manifest structure..."
sleep 3  # Wait for storage to complete

MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "Config digest: $CONFIG_DIGEST"
echo "Layer count: $LAYER_COUNT"
echo ""

if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
    print_error "CRITICAL: Config digest is null!"
    echo ""
    echo "The manifest is incomplete. This should not happen with the fix."
    echo "Possible issues:"
    echo "  1. Backend was not restarted with new code"
    echo "  2. Fix was not properly applied"
    echo "  3. Old code still running"
    echo ""
    echo "SOLUTION:"
    echo "  cd funeral-backend"
    echo "  mvn clean compile quarkus:dev"
    echo "  Then run this script again"
    exit 1
fi

if [ "$LAYER_COUNT" -eq 0 ]; then
    print_error "CRITICAL: No layers found!"
    exit 1
fi

print_success "Manifest structure is correct!"
echo ""

# Step 4: Test blob accessibility
echo "Step 4: Verifying blob accessibility..."

CONFIG_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:8911/v2/bitnami/mongodb/blobs/$CONFIG_DIGEST)

if [ "$CONFIG_HTTP" = "200" ]; then
    print_success "Config blob accessible"
else
    print_error "Config blob NOT accessible (HTTP $CONFIG_HTTP)"
    exit 1
fi

LAYER_ERROR=0
echo "$MANIFEST" | jq -r '.layers[].digest' | while read digest; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8911/v2/bitnami/mongodb/blobs/$digest)

    if [ "$HTTP_CODE" = "200" ]; then
        echo "  ✓ Layer accessible: $(echo $digest | cut -c1-16)..."
    else
        echo "  ✗ Layer NOT accessible (HTTP $HTTP_CODE)"
        LAYER_ERROR=1
    fi
done

if [ $LAYER_ERROR -eq 1 ]; then
    exit 1
fi

echo ""

# Step 5: Test Helm pull
echo "Step 5: Testing Helm pull command..."
echo ""
echo "Command: helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http"
echo ""

helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http

if [ $? -eq 0 ]; then
    print_success "HELM PULL SUCCESSFUL!"
    echo ""

    # Check if file was downloaded
    if [ -f "mongodb-12.1.31.tgz" ]; then
        echo "Downloaded file:"
        ls -lh mongodb-12.1.31.tgz
        echo ""
    fi

    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                    ✓ SUCCESS! ✓                              ║"
    echo "║                                                               ║"
    echo "║  Helm charts can now be properly:                            ║"
    echo "║  - Mirrored from external registries                         ║"
    echo "║  - Stored with correct OCI manifest structure                ║"
    echo "║  - Pulled using Helm CLI                                     ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Tested with: bitnami/mongodb:12.1.31"
    echo ""
    echo "Next steps:"
    echo "  - Try other charts from Docker Hub (registry-1.docker.io)"
    echo "  - Try charts from ChartMuseum repositories"
    echo "  - Use the web UI at http://192.168.8.9:3001"
else
    print_error "HELM PULL FAILED!"
    echo ""
    echo "This should not happen if the above checks passed."
    echo "Possible causes:"
    echo "  - Network issue"
    echo "  - Helm version incompatibility"
    echo "  - Registry authentication issue"
    echo ""
    echo "Debug steps:"
    echo "  1. Check manifest directly:"
    echo "     curl -H 'Accept: application/vnd.oci.image.manifest.v1+json' \\"
    echo "       http://192.168.8.9:8911/v2/bitnami/mongodb/manifests/12.1.31 | jq ."
    echo ""
    echo "  2. Check backend logs for errors"
fi

echo ""
