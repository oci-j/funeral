#!/bin/bash

# Script to retry Docker build with better error handling

set -e

echo "Cleaning up previous Docker builds..."
docker builder prune -f 2>/dev/null || true

# Remove any existing node_modules from host that might cause issues
echo "Cleaning node_modules to prevent Docker build issues..."
rm -rf funeral-frontend/node_modules 2>/dev/null || true

# Build the Docker image
echo "Starting Docker build..."
docker build --no-cache -t funeral-oci-registry:latest .

if [ $? -eq 0 ]; then
    echo "✓ Docker image built successfully!"
else
    echo "✗ Docker build failed"
    exit 1
fi
