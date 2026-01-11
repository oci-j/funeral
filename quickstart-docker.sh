#!/bin/bash

set -e

echo "=========================================="
echo "  Funeral OCI Registry - Quick Start"
echo "=========================================="
echo "This script will:"
echo "1. Build the Docker image"
echo "2. Start all services using Docker Compose"
echo "=========================================="
echo ""

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "${YELLOW}Creating .env file from .env.example...${NC}"
    cp .env.example .env
    echo "${GREEN}✓ Created .env file${NC}"
    echo "You can modify .env to customize settings."
    echo ""
fi

# Build Docker image
echo "${YELLOW}Building Docker image...${NC}"
./build-docker.sh

if [ $? -ne 0 ]; then
    echo ""
    echo "Failed to build Docker image."
    exit 1
fi

echo ""
echo "${GREEN}✓ Docker image built successfully!${NC}"
echo ""

# Start services with docker-compose
echo "${YELLOW}Starting services with Docker Compose...${NC}"
echo "This will start: funeral-registry, mongo, and minio"
echo ""

docker-compose up -d

# Wait for services to start
echo "Waiting for services to start..."
sleep 20

# Check if containers are running
echo ""
echo "Checking service status..."
docker-compose ps

echo ""
echo "=========================================="
echo "  ✓ Services Started Successfully!"
echo "=========================================="
echo ""
echo "${GREEN}Access Points:${NC}"
echo "  Web UI:         http://localhost:8911"
echo "  OCI API:        http://localhost:8911/v2/"
echo "  MongoDB:        localhost:27017"
echo "  MinIO Console:  http://localhost:19001"
echo ""
echo "${GREEN}Default Credentials:${NC}"
echo "  Registry: admin / password"
echo "  MinIO:    minioadmin / minioadmin"
echo ""
echo "${YELLOW}Next Steps:${NC}"
echo "  1. Open http://localhost:8911 in your browser"
echo "  2. Use docker commands with localhost:8911"
echo "  3. Check logs: docker-compose logs -f funeral-registry"
echo ""
echo "${YELLOW}Example Docker commands:${NC}"
echo "  docker login localhost:8911 -u admin -p password"
echo "  docker tag alpine:latest localhost:8911/alpine:latest"
echo "  docker push localhost:8911/alpine:latest"
echo ""
echo "=========================================="
