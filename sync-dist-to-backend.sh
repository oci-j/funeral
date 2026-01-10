#!/bin/bash

# Sync built dist files to backend resources using rsync
# This script syncs files from funeral-frontend/dist to funeral-backend/src/main/resources/META-INF/resources/

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_SOURCE="${PROJECT_ROOT}/funeral-frontend/dist/"
BACKEND_TARGET="${PROJECT_ROOT}/funeral-backend/src/main/resources/META-INF/resources/"

# Check if dist folder exists
if [ ! -d "$DIST_SOURCE" ]; then
    echo "ERROR: dist folder not found at $DIST_SOURCE"
    echo "Please run 'npm run build' or 'pnpm build' first"
    exit 1
fi

# Check if rsync is available
if ! command -v rsync &> /dev/null; then
    echo "ERROR: rsync is not installed or not in PATH"
    echo "Please install rsync to use this script"
    exit 1
fi

echo "=== Syncing dist files to backend resources using rsync ==="
echo "Source: ${DIST_SOURCE#$PROJECT_ROOT/}"
echo "Target: ${BACKEND_TARGET#$PROJECT_ROOT/}"
echo ""

# Create target directory if it doesn't exist
if [ ! -d "$BACKEND_TARGET" ]; then
    echo "Creating target directory: $BACKEND_TARGET"
    mkdir -p "$BACKEND_TARGET"
fi

# Use rsync for efficient sync with content comparison
# -a: archive mode (preserve permissions, timestamps, etc.)
# -v: verbose (show what's being copied)
# -r: recursive
# -c: skip based on checksum, not mod-time & size (content comparison)
# --delete: delete extraneous files from destination dirs
# --delete-excluded: also delete excluded files on the receiving side
# --progress: show progress during transfer
# --stats: give some file-transfer stats

echo "Starting rsync..."
rsync -avrc --delete --delete-excluded --progress --stats \
    --exclude='.git/' \
    --exclude='node_modules/' \
    "$DIST_SOURCE" "$BACKEND_TARGET"

# Also create a marker file to indicate sync completion
SYNC_TIMESTAMP="${BACKEND_TARGET}.sync_timestamp"
date > "$SYNC_TIMESTAMP"

# Show summary
echo ""
echo "✓ Backend resources synced successfully"
echo "✓ Timestamp: $(cat "$SYNC_TIMESTAMP")"
echo "✓ Sync method: rsync with content comparison and cleanup"
