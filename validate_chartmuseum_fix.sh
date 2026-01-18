#!/bin/bash

echo "ChartMuseum Fix Validation"
echo "=========================="
echo ""

# Check if backend is running
echo "1. Checking if backend is running..."
if curl -s http://localhost:8911/v2/_catalog > /dev/null; then
    echo "✓ Backend is running on port 8911"

    # Test with a simple chart
    echo ""
    echo "2. Testing ChartMuseum chart mirroring..."
    echo "   Chart: nginx from Bitnami"

    RESPONSE=$(curl -s -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "format=chartmuseum" \
        -d "sourceRepo=https://charts.bitnami.com/bitnami" \
        -d "chartName=nginx" \
        -d "version=18.2.3" \
        | jq .)

    echo "   Response: $RESPONSE"

    SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
    if [ "$SUCCESS" = "true" ]; then
        echo "✓ Chart mirrored successfully"

        # Check manifest structure
        echo ""
        echo "3. Checking manifest structure..."
        sleep 2

        MANIFEST=$(curl -s -H "Accept: application/vnd.oci.image.manifest.v1+json" \
            http://localhost:8911/v2/nginx/manifests/18.2.3)

        CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
        LAYER_DIGESTS=$(echo "$MANIFEST" | jq -r '.layers[].digest')

        echo "   Config digest: $CONFIG_DIGEST"
        echo "   Layer digests:"
        for digest in $LAYER_DIGESTS; do
            echo "     - $digest"
        done

        # Verify blobs exist
        echo ""
        echo "4. Verifying blobs in storage..."

        if [ "$CONFIG_DIGEST" != "null" ] && [ "$CONFIG_DIGEST" != "" ]; then
            if curl -s "http://localhost:8911/v2/nginx/blobs/$CONFIG_DIGEST" > /dev/null; then
                echo "✓ Config blob exists and is accessible"
            else
                echo "✗ Config blob not found"
            fi
        fi

        for digest in $LAYER_DIGESTS; do
            if [ "$digest" != "null" ] && [ "$digest" != "" ]; then
                if curl -s "http://localhost:8911/v2/nginx/blobs/$digest" > /dev/null; then
                    echo "✓ Layer blob exists and is accessible"
                else
                    echo "✗ Layer blob not found"
                fi
            fi
        done

        echo ""
        echo "=========================="
        echo "✓ ChartMuseum fix validation complete!"
        echo "=========================="
    else
        echo "✗ Chart mirroring failed"
        echo "Response: $RESPONSE"
    fi
else
    echo "✗ Backend is not running. Please start it first with:"
    echo "   cd funeral-backend && mvn quarkus:dev"
fi
