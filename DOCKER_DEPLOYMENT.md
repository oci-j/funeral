# Funeral OCI Registry - Docker Deployment Guide

This guide explains how to build and deploy Funeral OCI Registry as a single Docker image containing both frontend and backend services, accessible through a single port (8911).

## Architecture

- **Single Docker Image**: Contains both frontend (Vue.js) and backend (Quarkus)
- **Single Port**: All services accessible through port 8911
  - Frontend: http://localhost:8911/
  - Backend API: http://localhost:8911/v2/
  - OCI Distribution Spec: http://localhost:8911/v2/

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- `bash` shell

## Quick Start

### Option 1: Using Docker Compose (Recommended)

1. **Clone and setup configuration:**
   ```bash
   cd /home/xenoamess/workspace/funeral
   cp .env.example .env
   ```

2. **Build the Docker image:**
   ```bash
   ./build-docker.sh
   ```

3. **Start all services:**
   ```bash
   docker-compose up -d
   ```

4. **Access the services:**
   - Web UI: http://localhost:8911
   - OCI Registry: http://localhost:8911/v2/

### Option 2: Using Docker Only

1. **Build the Docker image:**
   ```bash
   ./build-docker.sh
   ```

2. **Run the container with environment variables:**
   ```bash
   docker run -d \
     --name funeral-registry \
     -p 8911:8911 \
     -e NO_MONGO=true \
     -e NO_MINIO=true \
     -e AUTH_ENABLED=true \
     -e AUTH_AUTO_CREATE_ENABLED=true \
     funeral-oci-registry:latest
   ```

## Configuration

### Environment Variables

#### Database Configuration
- `MONGO_URL`: MongoDB connection URL (default: `mongodb://mongo:27017`)
- `MONGO_DATABASE`: MongoDB database name (default: `oci_registry`)

#### Storage Configuration
- `S3_ENDPOINT`: MinIO/S3 endpoint (default: `http://minio:9000`)
- `S3_ACCESS_KEY`: S3 access key (default: `minioadmin`)
- `S3_SECRET_KEY`: S3 secret key (default: `minioadmin`)
- `S3_BUCKET`: S3 bucket name (default: `oci-registry`)
- `NO_MONGO`: Disable MongoDB, use file-based storage (default: `false`)
- `NO_MINIO`: Disable MinIO, use local file storage (default: `false`)
- `LOCAL_STORAGE_PATH`: Local storage path for files (default: `/tmp/funeral-storage`)

#### Authentication
- `AUTH_ENABLED`: Enable authentication (default: `true`)
- `AUTH_ALLOW_ANONYMOUS_PULL`: Allow anonymous pulls (default: `true`)
- `JWT_ISSUER`: JWT issuer (default: `funeral-registry`)
- `JWT_EXPIRATION`: JWT expiration in seconds (default: `3600`)

#### Auto-create Admin User
These settings create an initial admin user on first startup:
- `AUTH_AUTO_CREATE_ENABLED`: Enable auto-create (default: `true`)
- `AUTH_AUTO_CREATE_USERNAME`: Admin username (default: `admin`)
- `AUTH_AUTO_CREATE_PASSWORD`: Admin password (default: `password`)
- `AUTH_AUTO_CREATE_EMAIL`: Admin email (default: `admin@funeral.local`)
- `AUTH_AUTO_CREATE_ROLES`: Admin roles (default: `ADMIN;USER;PUSH_ALL;PULL_ALL`)

## Build Options

### Build Script Parameters

The `build-docker.sh` script supports the following options:

```bash
./build-docker.sh [OPTIONS]

OPTIONS:
  -h, --help          Show help message
  -n, --name NAME     Docker image name (default: funeral-oci-registry)
  -t, --tag TAG       Docker image tag (default: latest)
  --no-test          Skip testing after build

Example:
  ./build-docker.sh --name my-registry --tag v1.0
```

### Build Without Frontend

If you only want to build the backend (no frontend):

```bash
docker build --target backend --tag funeral-backend:latest .
```

### Build with Custom Registry

```bash
# Build for private registry
./build-docker.sh --name registry.mycompany.com/funeral-oci-registry --tag v1.0

# Push to registry
docker push registry.mycompany.com/funeral-oci-registry:v1.0
```

## Testing the Deployment

### Health Check

```bash
# Check if the service is running
curl http://localhost:8911/v2/
# Expected: HTTP 401 Unauthorized (service is running)

# With docker-compose
docker-compose ps

# View logs
docker-compose logs -f funeral-registry
```

### Docker Registry Commands

```bash
# Login (admin/password)
docker login localhost:8911 -u admin -p password

# Pull an image
docker pull alpine:latest

# Tag the image
docker tag alpine:latest localhost:8911/alpine:latest

# Push to Funeral registry
docker push localhost:8911/alpine:latest

# List repositories
curl http://localhost:8911/v2/_catalog

# List tags for a repository
curl http://localhost:8911/v2/alpine/tags/list
```

### Web UI Access

Open your browser and navigate to `http://localhost:8911` to access the web interface.

Default login credentials:
- Username: `admin`
- Password: `password`

## Docker Compose Services

The `docker-compose.yml` includes:

1. **funeral-registry**: Main application (port 8911)
2. **mongo**: MongoDB database (port 27017)
3. **minio**: MinIO S3-compatible storage (ports 19000, 19001)

### Using External Services

To use external MongoDB and MinIO instead of the ones provided by Docker Compose:

```bash
# Run with only the Funeral registry
docker-compose up funeral-registry

# Or with Docker directly
docker run -d \
  --name funeral-registry \
  -p 8911:8911 \
  -e MONGO_URL=mongodb://my-mongo:27017 \
  -e S3_ENDPOINT=http://my-minio:9000 \
  -e S3_ACCESS_KEY=my-key \
  -e S3_SECRET_KEY=my-secret \
  funeral-oci-registry:latest
```

## Using File-Based Storage Only (No MongoDB/MinIO)

For development or lightweight use:

```bash
docker run -d \
  --name funeral-registry \
  -p 8911:8911 \
  -e NO_MONGO=true \
  -e NO_MINIO=true \
  -e LOCAL_STORAGE_PATH=/tmp/funeral-storage \
  -v funeral-storage:/tmp/funeral-storage \
  funeral-oci-registry:latest
```

## Volume Mounts

To persist data when using file-based storage:

```bash
docker run -d \
  -v funeral-storage:/tmp/funeral-storage \
  -p 8911:8911 \
  funeral-oci-registry:latest
```

## Security Considerations

### Production Deployment

1. **Change default passwords** in `.env` file
2. **Use HTTPS** with a reverse proxy (Nginx/Traefik)
3. **Enable authentication**: Set `AUTH_ENABLED=true`
4. **Use strong secrets** for JWT keys
5. **Regular security updates** of base images

### Example with Traefik

```yaml
# docker-compose.override.yml
services:
  funeral-registry:
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.funeral.rule=Host(`registry.example.com`)"
      - "traefik.http.routers.funeral.tls=true"
      - "traefik.http.routers.funeral.tls.certresolver=letsencrypt"
```

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 8911
sudo lsof -i :8911

# Stop the service
sudo systemctl stop <service>  # or
docker stop $(docker ps -q --filter "publish=8911")
```

### Can't Connect to MongoDB

```bash
# Check MongoDB status
docker-compose logs mongo

# Ensure MongoDB is ready before starting Funeral
docker-compose up -d mongo
sleep 10
docker-compose up -d funeral-registry
```

### Storage Issues

```bash
# Check file permissions
docker-compose exec funeral-registry ls -la /tmp/funeral-storage

# View application logs
docker-compose logs -f funeral-registry
```

## Performance Tuning

### Memory and CPU Limits

```yaml
# docker-compose.yml
services:
  funeral-registry:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
```

### JVM Options

```bash
docker run -d \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  funeral-oci-registry:latest
```

## Monitoring

### Container Statistics

```bash
# View container resource usage
docker stats funeral-registry

# Check container health
docker inspect funeral-registry --format='{{.State.Health.Status}}'
```

### Application Metrics

The application exposes Prometheus metrics at:
- http://localhost:8911/q/metrics

## Logs

### View Logs

```bash
# Real-time logs
docker-compose logs -f funeral-registry

# View last 100 lines
docker-compose logs --tail=100 funeral-registry

# Search logs
docker-compose logs funeral-registry | grep ERROR
```

### Log Configuration

Add to `docker-compose.yml`:

```yaml
services:
  funeral-registry:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## Cleanup

### Remove Containers

```bash
# Stop and remove all containers
docker-compose down

# Also remove volumes (data loss!)
docker-compose down -v
```

### Remove Image

```bash
# Remove the Funeral image
docker rmi funeral-oci-registry:latest

# Remove all unused images
docker image prune -a
```

## Support

For issues and questions:
1. Check container logs: `docker-compose logs funeral-registry`
2. Check application logs in the container: `docker exec funeral-registry tail -f /tmp/funeral-storage/logs/application.log`
3. Verify network connectivity between containers
4. Ensure sufficient disk space and memory

## License

See the main project LICENSE file.
