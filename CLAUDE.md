# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FUNERAL is an OCI (Open Container Initiative) image registry implemented in Java that follows the OCI Distribution Specification. It's designed to be lightweight and production-ready with a focus on usability and bandwidth optimization.

## Architecture

- **Backend**: Java 17/Quarkus REST API server (port 8911)
- **Frontend**: Vue.js 3 web interface (port 3000)
- **Storage**: MongoDB for metadata + MinIO S3 for blob storage
- **Container Support**: Docker with GraalVM native compilation

## Development Commands

### Backend Development
```bash
# Navigate to backend directory
cd funeral-backend

# Run in development mode (hot reload)
mvn quarkus:dev

# Build for JVM
mvn clean package

# Build native binary (requires GraalVM)
mvn clean package -Pnative

# Run tests (minimal test coverage currently)
mvn test
```

### Frontend Development
```bash
# Navigate to frontend directory
cd funeral-frontend

# Install dependencies
npm install

# Run development server (hot reload)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Infrastructure Setup
```bash
# Start MongoDB and MinIO using Docker Compose
cd docker_bak
docker-compose up -d

# Or run individually:
# MongoDB on localhost:27017
# MinIO on localhost:9000 (API) and 9001 (Console)
```

## Key Configuration

Backend configuration in `funeral-backend/src/main/resources/application.yml`:
- **HTTP Server**: Port 8911, CORS enabled for all origins
- **MongoDB**: Configurable via MONGO_URL environment variable
- **MinIO S3**: Configurable via S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET
- **Max Upload Size**: 4GB

## Code Structure

### Backend (`funeral-backend/src/main/java/io/oci/`)
- **`resource/`**: REST API endpoints following OCI Distribution Spec
  - `v2/` - OCI v2 API endpoints (manifests, blobs, tags)
  - `RootResource` - Main API entry point
- **`service/`**: Business logic for OCI operations
  - `ManifestService` - Manifest management
  - `BlobService` - Blob storage operations
  - `RepositoryService` - Repository operations
- **`model/`**: MongoDB entities with Panache ORM
- **`dto/`**: Data transfer objects for API requests/responses
- **`util/`**: Utilities (MongoDB client, MinIO client, digest calculation)

### Frontend (`funeral-frontend/src/`)
- **`views/`**: Vue components (Home, Repository, Upload)
- **`stores/`**: Pinia state management
- **`api/`**: API client utilities for backend communication
- **`router/`**: Vue Router configuration

## OCI Compliance Status

Current conformance: 74/79 tests passing from OCI Distribution Spec test suite. Focus areas:
1. **Pull API** - Highest priority (must work)
2. **Push API** - Secondary priority
3. **Content Discovery** - Tag listing
4. **Content Management** - Lowest priority

## Key Development Patterns

### Adding New OCI Endpoints
1. Implement endpoint in appropriate resource class under `resource/v2/`
2. Add service method in corresponding service class
3. Follow existing patterns for request/response handling
4. Ensure proper HTTP status codes and headers per OCI spec

### Working with Storage
- **MongoDB**: Use Panache ORM entities in `model/` package
- **MinIO S3**: Use `MinioClient` from `util/` package
- **Configuration**: Environment variables override defaults in `application.yml`

### Frontend Integration
- Backend API runs on port 8911, frontend dev server proxies requests
- Use existing `api/` utilities for consistent error handling
- Follow Vue 3 Composition API patterns in components

## Testing Approach

- **Backend**: Limited test coverage - focus on manual testing with OCI conformance suite
- **Frontend**: No automated tests currently - manual testing through UI
- **Integration**: Use OCI Distribution Spec conformance tests as primary validation

## Deployment Options

1. **Development**: `mvn quarkus:dev` + `npm run dev`
2. **JVM Production**: `mvn clean package` then run JAR
3. **Native Binary**: `mvn clean package -Pnative` for minimal footprint
4. **Docker**: Multi-stage build with UBI base images (see `docker_bak/Dockerfile`)

## Important Notes

- No security features implemented - designed for trusted environments only
- Focus on OCI Pull/Push API compliance over advanced features
- GraalVM native compilation supported for lightweight deployments
- Frontend is optional - backend provides full OCI API functionality