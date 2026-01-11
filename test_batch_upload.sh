#!/bin/bash

# Test script for batch upload of Docker tar files

REGISTRY_URL="http://192.168.8.9:8911"

echo "Testing batch upload of Docker tar files..."
echo "================================================"

# Check if files are provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 file1.tar [file2.tar] [file3.tar] ..."
    echo ""
    echo "Example:"
    echo "  $0 nginx.tar.zst ubuntu.tar.zst alpine.tar"
    echo ""
    exit 1
fi

# Check if files exist
for file in "$@"; do
    if [ ! -f "$file" ]; then
        echo "Error: File '$file' not found!"
        exit 1
    fi
done

echo "Files to upload:"
for file in "$@"; do
    echo "  - $file ($(du -h "$file" | cut -f1))"
done
echo ""

# Get authentication token
read -p "Username (press Enter for no auth): " USERNAME
if [ -n "$USERNAME" ]; then
    read -s -p "Password: " PASSWORD
    echo ""

    # Login to get token
    TOKEN_RESPONSE=$(curl -s -X POST "${REGISTRY_URL}/api/v2/token" \
        -u "${USERNAME}:${PASSWORD}" \
        -d "service=funeral-registry" \
        -d "scope=repository:*:pull,push")

    TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | sed 's/"token":"//' | sed 's/"//')

    if [ -z "$TOKEN" ]; then
        echo "Login failed!"
        exit 1
    fi
fi

# Create multipart form data
BOUNDARY="----WebKitFormBoundary$(date +%s)

# Build the request body
BODY=""
for file in "$@"; do
    FILENAME=$(basename "$file")
    BODY="${BODY}--${BOUNDARY}\r\n"
    BODY="${BODY}Content-Disposition: form-data; name=\"files\"; filename=\"${FILENAME}\"\r\n"
    BODY="${BODY}Content-Type: application/octet-stream\r\n"
    BODY="${BODY}\r\n"
    BODY="${BODY}$(cat "$file")\r\n"
done
BODY="${BODY}--${BOUNDARY}--\r\n"

echo "Uploading files..."
if [ -n "$TOKEN" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${REGISTRY_URL}/funeral_addition/write/upload/dockertar/batch" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: multipart/form-data; boundary=${BOUNDARY}" \
        --data-binary "@-")
else
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${REGISTRY_URL}/funeral_addition/write/upload/dockertar/batch" \
        -H "Content-Type: multipart/form-data; boundary=${BOUNDARY}" \
        --data-binary "@-")
fi

# Extract HTTP status and response body
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)

echo ""
echo "HTTP Status: $HTTP_CODE"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Upload successful!"
    echo ""
    echo "Response:"
    echo "$RESPONSE_BODY" | jq .
else
    echo "❌ Upload failed!"
    echo ""
    echo "Response:"
    echo "$RESPONSE_BODY"
fi
