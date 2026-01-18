#!/bin/bash

echo "╔════════════════════════════════════════════════════════════╗"
echo "║        HELM CHART MIRRORING - FIX AND TEST SCRIPT         ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

# Step 1: Check if backend is running
echo "Step 1: Checking backend status..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null 2>&1; then
    print_success "Backend is running on port 8911"
    print_warning "Make sure you restarted with 'mvn clean compile quarkus:dev'!"
else
    print_error "Backend is NOT running!"
    echo ""
    echo "Please start the backend first:"
    echo "  cd funeral-backend && mvn clean compile quarkus:dev"
    exit 1
fi
echo ""

# Step 2: Clean up any existing bad data
echo "Step 2: Cleaning up existing manifests..."
./cleanup_manifests.sh > /dev/null 2>&1
print_success "Cleanup completed"
echo ""

# Step 3: Mirror the chart
echo "Step 3: Mirroring bitnami/mongodb:12.1.31..."
echo "This may take a minute..."

curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=oci" \
  -d "sourceRepo=registry-1.docker.io" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=bitnami/mongodb" \
  -d "targetVersion=12.1.31" | jq .

if [ $? -ne 0 ]; then
    print_error "Failed to mirror chart"
    exit 1
fi

print_success "Mirror request completed"
echo ""

# Wait for storage to complete
echo "Waiting for storage to complete..."
sleep 5
echo ""

# Step 4: Verify the manifest
echo "Step 4: Verifying manifest structure..."

MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "  Config digest: $CONFIG_DIGEST"
echo "  Layer count: $LAYER_COUNT"
echo ""

if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
    print_error "Manifest is missing config descriptor!"
    echo ""
    echo "This means the fix didn't work. Check:"
    echo "  1. Did you restart backend with 'mvn clean compile quarkus:dev'?"
    echo "  2. Check backend logs for 'OCI INDEX DETECTED' messages"
    exit 1
fi

if [ "$LAYER_COUNT" -eq 0 ]; then
    print_error "Manifest has no layers!"
    exit 1
fi

print_success "Manifest structure looks correct!"
echo ""

# Step 5: Verify blobs are accessible
echo "Step 5: Checking blob accessibility..."

CONFIG_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:8911/v2/bitnami/mongodb/blobs/$CONFIG_DIGEST)

if [ "$CONFIG_HTTP" = "200" ]; then
    print_success "Config blob is accessible"
else
    print_error "Config blob NOT accessible (HTTP $CONFIG_HTTP)"
    exit 1
fi

echo "$MANIFEST" | jq -r '.layers[].digest' | while read digest; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8911/v2/bitnami/mongodb/blobs/$digest)

    if [ "$HTTP_CODE" = "200" ]; then
        echo "  ✓ Layer $digest accessible"
    else
n        echo "  ✗ Layer $digest NOT accessible (HTTP $HTTP_CODE)"
        exit 1
    fi
done

echo ""

# Step 6: Test Helm pull
echo "Step 6: Testing Helm pull..."
echo "Running: helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http"
echo ""

helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http

if [ $? -eq 0 ]; then
    print_success "HELM PULL SUCCESSFUL!"
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║                    ✓ FIX VERIFIED! ✓                      ║"
    echo "║   Helm charts can now be properly mirrored and pulled     ║"
    echo "╚════════════════════════════════════════════════════════════╝"

    # Show downloaded file
    if [ -f "mongodb-12.1.31.tgz" ]; then
        echo ""
        echo "Downloaded file:"
        ls -lh mongodb-12.1.31.tgz
    fi
else
    print_error "HELM PULL FAILED!"
    echo ""
    echo "The fix did not resolve the issue."
    echo "Please check:"
    echo "  1. Backend logs for errors during mirroring"
    echo "  2. Run: ./diagnose_helm_issue.sh"
    echo "  3. Check if 'mvn clean compile' was run before starting backend"
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "Test completed successfully!"
echo ""
echo "You can now use:"
echo "  helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http"
echo ""
echo "Or mirror other charts:"
echo "  - ChartMuseum format: nginx from https://charts.bitnami.com/bitnami"
echo "  - OCI format: Any chart from registry-1.docker.io"
echo "═══════════════════════════════════════════════════════════════"
