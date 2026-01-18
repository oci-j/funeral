#!/bin/bash

echo "OCI Helm Chart Mirroring Test"
echo "=============================="
echo ""

# Check if backend is running
echo "1. Checking if backend is running..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null; then
    echo "✓ Backend is running on port 8911"

    # Test OCI chart mirroring
    echo ""
    echo "2. Testing OCI chart mirroring..."
    echo "   Chart: bitnami/mongodb from Docker Hub"

    RESPONSE=$(curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "format=oci" \
        -d "sourceRepo=registry-1.docker.io" \
        -d "chartName=bitnami/mongodb" \
        -d "version=12.1.31" \
        -d "targetRepository=bitnami/mongodb" \
        -d "targetVersion=12.1.31" \
        | jq .)

    echo "   Response: $RESPONSE"

    SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
    if [ "$SUCCESS" = "true" ]; then
        echo "✓ Chart mirrored successfully"

        # Check manifest structure
        echo ""
        echo "3. Checking manifest structure..."
        sleep 3

        MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
            http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31)

        echo "Manifest content:"
        echo "$MANIFEST" | jq .

        # Count descriptors
        CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
        LAYER_COUNT=$(echo "$MANIFEST" | jq '.layers | length')

        echo ""
        echo "Descriptor count:"
        echo "  Config: $([ "$CONFIG_DIGEST" != "null" ] && echo "present" || echo "missing")"
        echo "  Layers: $LAYER_COUNT found"

        if [ "$CONFIG_DIGEST" != "null" ] && [ "$LAYER_COUNT" -gt 0 ]; then
            echo "✓ Manifest contains required descriptors (config + layer(s))"
        else
            echo "✗ Manifest is missing required descriptors"
        fi

        # Test pulling with Helm
        echo ""
        echo "4. Testing Helm pull..."
        helm pull oci://localhost:8911/bitnami/mongodb --version 12.1.31 --plain-http

        if [ $? -eq 0 ]; then
            echo "✓ Helm successfully pulled the chart"
        else
            echo "✗ Helm pull failed"
        fi

        echo ""
        echo "=============================="
        echo "✓ OCI chart test completed!"
        echo "=============================="
    else
        echo "✗ Chart mirroring failed"
        echo "Response: $RESPONSE"
    fi
else
    echo "✗ Backend is not running. Please start it first with:"
    echo "   cd funeral-backend && mvn quarkus:dev"
fi
