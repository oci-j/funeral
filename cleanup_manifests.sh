#!/bin/bash

echo "Cleaning up existing manifests..."
echo "=================================="
echo ""

# Delete existing mongodb manifest if it exists
echo "Deleting bitnami/mongodb:12.1.31 manifest..."
curl -s -X DELETE http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31

# Also delete by tag reference
echo "Deleting any existing tags for bitnami/mongodb..."
curl -s http://localhost:8911/v2/bitnami/mongodb/tags/list | jq -r '.tags[]' | while read tag; do
    echo "  Deleting tag: $tag"
    curl -s -X DELETE http://localhost:8911/v2/bitnami/mongodb/manifests/$tag
done

echo ""
echo "Cleanup complete!"
echo ""
echo "Now you can re-mirror the chart:"
echo "curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \\"
echo "  -H \"Content-Type: application/x-www-form-urlencoded\" \\"
echo "  -d \"format=oci\" \\"
echo "  -d \"sourceRepo=registry-1.docker.io\" \\"
echo "  -d \"chartName=bitnami/mongodb\" \\"
echo "  -d \"version=12.1.31\" \\"
echo "  -d \"targetRepository=bitnami/mongodb\" \\"
echo "  -d \"targetVersion=12.1.31\""
