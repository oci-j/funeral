# Docker Files Created for Funeral OCI Registry

## Overview

Successfully created a complete Docker deployment solution that packages both frontend and backend into a single Docker image, accessible through a single port (8911).

## Files Created

### 1. Dockerfile
**Location**: `/home/xenoamess/workspace/funeral/Dockerfile`

Multi-stage build that:
- **Stage 1**: Builds Vue.js frontend with Node.js 20
- **Stage 2**: Builds Quarkus backend with Maven, includes frontend files
- **Stage 3**: Creates lightweight runtime image with JRE 17

**Key Features**:
- Frontend files automatically served by Quarkus from `META-INF/resources`
- Non-root user for security
- Health checks enabled
- Alpine Linux for minimal size

### 2. Docker Entrypoint Script
**Location**: `/home/xenoamess/workspace/funeral/docker-entrypoint.sh`

Handles:
- Graceful application startup
- Signal handling for clean shutdown
- Informative startup logs
- JVM configuration

### 3. Build Script
**Location**: `/home/xenoamess/workspace/funeral/build-docker.sh`

Features:
- Automated Docker image building
- Prerequisite checking
- Optional image testing
- Configurable image name/tag
- Colored output for better UX
- Usage: `./build-docker.sh --name my-registry --tag v1.0`

### 4. Quick Start Script
**Location**: `/home/xenoamess/workspace/funeral/quickstart-docker.sh`

One-command deployment:
```bash
./quickstart-docker.sh
```
Automatically:
- Creates `.env` file if missing
- Builds Docker image
- Starts all services with docker-compose
- Displays access information

### 5. Docker Compose Configuration
**Location**: `/home/xenoamess/workspace/funeral/docker-compose.yml`

Services included:
- `funeral-registry`: Main application (port 8911)
- `mongo`: MongoDB database (port 27017)
- `minio`: S3-compatible storage (ports 19000, 19001)

### 6. Environment Configuration
**Location**: `/home/xenoamess/workspace/funeral/.env.example`

Configuration options for:
- Database settings
- Storage backend selection (MongoDB/MinIO or file-based)
- Authentication
- Admin user auto-creation

### 7. MongoDB Initialization
**Location**: `/home/xenoamess/workspace/funeral/mongo-init.js`

Creates initial MongoDB:
- Collections (repositories, manifests, blobs, users)
- Indexes for performance
- Ready for production use

### 8. Documentation
**Location**: `/home/xenoamess/workspace/funeral/DOCKER_DEPLOYMENT.md`

Comprehensive guide covering:
- Quick start instructions
- Configuration options
- Testing procedures
- Docker commands examples
- Security considerations
- Troubleshooting
- Performance tuning
- Monitoring

## Usage

### Quick Start (Recommended)

```bash
cd /home/xenoamess/workspace/funeral
./quickstart-docker.sh
```

This will:
1. Build the Docker image (~2-5 minutes)
2. Start all services
3. Display access URLs and credentials

### Manual Build and Run

```bash
# Build Docker image
./build-docker.sh

# Start with Docker Compose
docker-compose up -d

# Or run standalone
docker run -d -p 8911:8911 funeral-oci-registry:latest
```

### Access Points

- **Web UI**: http://localhost:8911
- **OCI API**: http://localhost:8911/v2/
- **MongoDB**: localhost:27017
- **MinIO Console**: http://localhost:19001

### Default Credentials

- **Registry**: admin / password
- **MinIO**: minioadmin / minioadmin

## Features

✅ **Single Port Access**: All services on port 8911
✅ **Frontend Integration**: Vue.js app served by Quarkus
✅ **Multiple Storage Options**: MongoDB or file-based storage
✅ **S3 Compatible**: MinIO for blob storage
✅ **Production Ready**: Health checks, non-root user, Alpine base
✅ **Easy Configuration**: Environment variables for all settings
✅ **Scalable**: Docker Compose for multi-container deployment
✅ **Monitoring**: Built-in health checks and metrics

## File-based Storage Option

For lightweight deployments without MongoDB/MinIO:

```bash
docker run -d \
  -p 8911:8911 \
  -e NO_MONGO=true \
  -e NO_MINIO=true \
  -v funeral-storage:/tmp/funeral-storage \
  funeral-oci-registry:latest
```

## Security Features

- Non-root container user (UID 1001)
- Alpine Linux base (minimal attack surface)
- Health checks implemented
- Configurable authentication
- Support for HTTPS via reverse proxy

## Next Steps

1. Run `./quickstart-docker.sh` to test the deployment
2. Access http://localhost:8911 in your browser
3. Try pushing an image:
   ```bash
   docker login localhost:8911 -u admin -p password
   docker tag alpine:latest localhost:8911/alpine:latest
   docker push localhost:8911/alpine:latest
   ```

## Customization

Edit `.env` file to customize:
- Authentication settings
- Storage backend
- Network configuration
- Admin credentials

See `DOCKER_DEPLOYMENT.md` for detailed configuration options.
