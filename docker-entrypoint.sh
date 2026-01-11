#!/bin/bash
set -e

echo "=========================================="
echo "  Funeral OCI Registry Starting..."
echo "=========================================="
echo "Version: $(date '+%Y%m%d_%H%M%S')"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
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
java \
    -Dquarkus.http.host=0.0.0.0 \
    -Dquarkus.http.port=8911 \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -jar quarkus-run.jar &

child=$!

# Wait for the child process
wait $child

# Exit with the child's exit code
exit $?
