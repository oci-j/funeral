#!/bin/bash

# Test script for Funeral OCI Registry Docker image

set -e

echo "=========================================="
echo "  Testing Funeral OCI Registry Docker Image"
echo "=========================================="
echo ""

# Configuration
CONTAINER_NAME="funeral-test-$$"
PORT="8912"

# Cleanup function
cleanup() {
    echo ""
    echo "Cleaning up..."
    docker stop $CONTAINER_NAME 2>/dev/null || true
    docker rm $CONTAINER_NAME 2>/dev/null || true
}

# Set trap to cleanup on exit
trap cleanup EXIT

# Run the container
echo "Starting container..."
docker run -d \
    --name $CONTAINER_NAME \
    -p $PORT:8911 \
    -e NO_MONGO=true \
    -e NO_MINIO=true \
    funeral-oci-registry:latest

echo "Waiting for container to start..."
sleep 25

# Test health endpoint
echo ""
echo "Testing health endpoint..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$PORT/v2/)

if [ "$HTTP_STATUS" = "401" ]; then
    echo "✓ Container is responding correctly (HTTP $HTTP_STATUS)"
    echo ""
    echo "Container logs:"
    docker logs $CONTAINER_NAME 2>&1 | tail -20
    echo ""
    echo "=========================================="
    echo "  ✓ Docker image test PASSED!"
    echo "=========================================="
    exit 0
else
    echo "✗ Unexpected HTTP status: $HTTP_STATUS"
    echo ""
    echo "Container logs:"
    docker logs $CONTAINER_NAME
    exit 1
fi
