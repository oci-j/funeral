#!/bin/bash
set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║          DEPLOY FIX AND TEST - COMPLETE SOLUTION             ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_step() {
    echo ""
    echo -e "${YELLOW}▶${NC} Step $1: $2"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Step 1: Check if backend is running
echo ""
print_step "1" "Checking backend status..."

if curl -s http://localhost:8911/v2/_catalog > /dev/null 2>&1; then
    print_success "Backend is running"
else
    print_error "Backend is NOT running"
    echo ""
    echo "Please start the backend first:"
    echo "  cd funeral-backend && mvn quarkus:dev"
    exit 1
fi

# Step 2: Compile the latest code
echo ""
print_step "2" "Compiling latest code with fixes..."

cd funeral-backend

# Force clean compilation
mvn clean compile -DskipTests -q

# Check if compilation succeeded
if [ $? -eq 0 ] && [ -f "target/classes/io/oci/resource/MirrorHelmResource.class" ]; then
    # Verify the fix is in the compiled code
    cd target/classes
    if strings io/oci/resource/MirrorHelmResource.class | grep -q "Generated URL"; then
        print_success "Code compiled successfully with fixes"
        cd ../../
    else
        print_error "Fixes not found in compiled code!"
        exit 1
    fi
else
    print_error "Compilation failed!"
    exit 1
fi

cd ..

# Step 3: Mirror the chart with ChartMuseum format
echo ""
print_step "3" "Mirroring bitnami/mongodb with ChartMuseum format..."

echo "Sending mirror request..."

RESPONSE=$(curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=chartmuseum" \
  -d "sourceRepo=https://charts.bitnami.com/bitnami" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=bitnami/mongodb" \
  -d "targetVersion=12.1.31")

# Check response
SUCCESS=$(echo "$RESPONSE" | jq -r '.success' 2>/dev/null)

if [ "$SUCCESS" = "true" ]; then
    print_success "Chart mirrored successfully!"
    
    # Show details
    BLOBS=$(echo "$RESPONSE" | jq -r '.blobsCount')
    echo "Blobs stored: $BLOBS"
else
    print_error "Mirror request failed!"
    echo "Response:"
    echo "$RESPONSE" | jq .
    exit 1
fi

# Wait for storage
echo ""
print_step "4" "Waiting for storage to complete..."
sleep 5

# Step 5: Verify manifest structure
echo ""
print_step "5" "Verifying manifest structure..."

MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

echo "   Config digest: $CONFIG_DIGEST"
echo "   Layer count: $LAYER_COUNT"

if [ "$CONFIG_DIGEST" = "null" ] || [ "$CONFIG_DIGEST" = "" ]; then
    print_error "Config digest is missing!"
    echo ""
    echo "This means the fix didn't work properly. Check backend logs."
    exit 1
fi

if [ "$LAYER_COUNT" -eq 0 ]; then
    print_error "No layers found!"
    exit 1
fi

print_success "Manifest structure is correct!"

# Step 6: Test Helm pull
echo ""
print_step "6" "Testing Helm pull..."
echo ""
echo "Command: helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http"
echo ""

helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http

if [ $? -eq 0 ]; then
    print_success "HELM PULL SUCCEEDED!"
    echo ""

    # Check if file was downloaded
    if [ -f "mongodb-12.1.31.tgz" ]; then
        ls -lh mongodb-12.1.31.tgz
        echo ""
    fi

    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                    ✓ FIX VERIFIED! ✓                         ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    exit 0
else
    print_error "HELM PULL FAILED!"
    echo ""
    echo "Even though manifest looks good, Helm pull failed."
    echo "Possible reasons:"
    echo "  - Network issue"
    echo "  - Helm caching"
    echo "  - Registry authentication issue"
    exit 1
fi
