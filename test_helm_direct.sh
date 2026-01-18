#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║           Testing Helm Direct Push/Pull Workflow                   ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# Create a minimal test chart
mkdir -p /tmp/helm-test2/test-chart/templates
cd /tmp/helm-test2

cat > test-chart/Chart.yaml << 'EOF'
apiVersion: v2
name: test-chart
description: A minimal test chart
type: application
version: 0.1.0
appVersion: "1.0"
EOF

cat > test-chart/values.yaml << 'EOF'
# Default values for test-chart
namespace: default
EOF

cat > test-chart/templates/pod.yaml << 'EOF'
apiVersion: v1
kind: Pod
metadata:
  name: {{ .Release.Name }}-pod
spec:
  containers:
  - name: test
    image: nginx:latest
    command: ["sleep", "3600"]
EOF

# Package the chart
echo "1. Packaging test chart..."
helm package test-chart

if [ ! -f test-chart-0.1.0.tgz ]; then
    echo "✗ Failed to package chart"
    exit 1
fi

ls -lh test-chart-0.1.0.tgz
echo ""

# Push to registry
echo "2. Pushing to registry..."
helm push test-chart-0.1.0.tgz oci://192.168.8.9:8911/direct-test --plain-http

if [ $? -ne 0 ]; then
    echo "✗ Push failed"
    exit 1
fi

echo ""
echo "3. Checking stored manifest..."

# Get token and check manifest
TOKEN=$(curl -s "http://192.168.8.9:8911/v2/token?scope=repository:direct-test/test-chart:pull" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

MANIFEST=$(curl -s -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.oci.image.manifest.v1+json" \
  http://192.168.8.9:8911/v2/direct-test/test-chart/manifests/0.1.0
)

echo "$MANIFEST" | jq .
echo ""

CONFIG_DIGEST=$(echo "$MANIFEST" | jq -r '.config.digest')
LAYERS=$(echo "$MANIFEST" | jq '.layers | length')
echo "Descriptors: Config=$CONFIG_DIGEST, Layers=$LAYERS"
echo ""

# Pull from registry
echo "4. Pulling from registry..."
helm pull oci://192.168.8.9:8911/direct-test/test-chart --version 0.1.0 --plain-http

if [ $? -eq 0 ]; then
    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║            ✓ HELM PUSH/PULL WORKS! ✓                         ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Registry can correctly handle Helm CLI push/pull"
    echo ""
    echo "The issue is specific to mirrored charts from ChartMuseum"
    echo "or OCI registries that use our code path, not the standard"
    echo "Helm CLI push/pull path."
else
    echo ""
    echo "✗ Even direct Helm push/pull fails"
    echo "This confirms a deeper issue with the registry implementation"
fi
