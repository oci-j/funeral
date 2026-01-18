#!/bin/bash
set -e

echo "=========================================="
echo "  Funeral OCI Registry Starting..."
echo "=========================================="
echo "Version: $(date '+%Y%m%d_%H%M%S')"
echo "Port: 8911"
echo "=========================================="

# Function to handle graceful shutdown
cleanup() {
    echo "Received termination signal, shutting down gracefully..."
    kill -TERM "$child" 2>/dev/null
}

# Set up trap for graceful shutdown
trap cleanup SIGTERM SIGINT

# Start the application
/app/funeral &

child=$!

# Wait for the child process
wait $child

# Exit with the child's exit code
exit $?
