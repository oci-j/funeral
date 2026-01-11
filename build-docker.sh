#!/bin/bash

set -e

# Script to build Funeral OCI Registry Docker image

# Configuration
DOCKER_IMAGE_NAME="funeral-oci-registry"
DOCKER_IMAGE_TAG="latest"
BUILD_DATE=$(date '+%Y%m%d_%H%M%S')

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "  Building Funeral OCI Registry Docker Image"
echo "  Version: ${BUILD_DATE}"
echo "=========================================="
echo ""

# Function to print colored output
print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running"
        exit 1
    fi

    print_success "Docker is available"
}

# Clean up previous builds
cleanup() {
    print_info "Cleaning up previous builds..."

    # Remove temporary build files if they exist
    if [ -d "target" ]; then
        rm -rf target
    fi

    # Clean Docker build cache if needed
    docker builder prune -f 2>/dev/null || true
}

# Build the Docker image
build_image() {
    print_info "Building Docker image ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}..."
    echo "This may take several minutes..."
    echo ""

    # Build the Docker image
    if docker build \
        --build-arg BUILD_DATE="${BUILD_DATE}" \
        -t "${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}" \
        -t "${DOCKER_IMAGE_NAME}:latest" \
        .; then
        print_success "Docker image built successfully: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
    else
        print_error "Docker image build failed"
        exit 1
    fi
}

# Test the built image
test_image() {
    print_info "Testing the built Docker image..."

    # Remove existing container if it exists
    docker rm -f funeral-test 2>/dev/null || true

    # Run the container in the background
    docker run -d \
        --name funeral-test \
        -p 8911:8911 \
        -e MONGO_URL="mongodb://192.168.8.9:27017" \
        -e S3_ENDPOINT="http://192.168.8.9:19000" \
        -e NO_MONGO=true \
        -e NO_MINIO=true \
        "${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"

    print_info "Waiting for container to start..."
    sleep 15

    # Test health endpoint
    if curl -f http://localhost:8911/v2/ 2>/dev/null; then
        print_success "Container is responding correctly"

        # Stop and remove test container
        docker stop funeral-test >/dev/null
        docker rm funeral-test >/dev/null
    else
        print_error "Container health check failed"
        docker logs funeral-test
        docker stop funeral-test 2>/dev/null || true
        docker rm funeral-test 2>/dev/null || true
        exit 1
    fi
}

# Display usage information
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "OPTIONS:"
    echo "  -h, --help          Show this help message"
    echo "  -n, --name NAME     Docker image name (default: funeral-oci-registry)"
    echo "  -t, --tag TAG       Docker image tag (default: latest)"
    echo "  --no-test          Skip testing the image after build"
    echo ""
    echo "Example:"
    echo "  $0 --name my-registry --tag v1.0"
}

# Parse command line arguments
TEST_IMAGE=true
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -n|--name)
            DOCKER_IMAGE_NAME="$2"
            shift 2
            ;;
        -t|--tag)
            DOCKER_IMAGE_TAG="$2"
            shift 2
            ;;
        --no-test)
            TEST_IMAGE=false
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main execution
echo ""
check_prerequisites
echo ""
cleanup
echo ""
build_image

if [ "$TEST_IMAGE" = true ]; then
    echo ""
    test_image
fi

echo ""
echo "=========================================="
echo "  Build Summary"
echo "=========================================="
print_success "Docker Image: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
print_success "Repository: funeral-oci-registry:latest"
print_info "To run: docker run -d -p 8911:8911 ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
print_info "To test: curl http://localhost:8911/v2/"
echo "=========================================="
exit 0
