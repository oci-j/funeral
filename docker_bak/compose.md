# Docker Compose file with MongoDB and MinIO
version: '3.8'
services:
  mongodb:
    image: mongo:7.0
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: password
      MONGO_INITDB_DATABASE: oci_registry
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    command: --replSet rs0 --bind_ip_all
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/test --quiet
      interval: 10s
      timeout: 10s
      retries: 5
      start_period: 40s

  mongo-init:
    image: mongo:7.0
    depends_on:
      mongodb:
        condition: service_healthy
    command: >
      bash -c "
        mongosh --host mongodb:27017 --eval '
          rs.initiate({
            _id: \"rs0\",
            members: [
              {_id: 0, host: \"mongodb:27017\"}
            ]
          });
        '
      "

  minio:
    image: minio/minio:latest
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set myminio http://minio:9000 minioadmin minioadmin;
      mc mb myminio/oci-registry --ignore-existing;
      mc policy set public myminio/oci-registry;
      "

  registry-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      MONGO_URL: mongodb://admin:password@mongodb:27017/oci_registry?authSource=admin
      MONGO_DATABASE: oci_registry
      S3_ENDPOINT: http://minio:9000
      S3_ACCESS_KEY: minioadmin
      S3_SECRET_KEY: minioadmin
      S3_BUCKET: oci-registry
    depends_on:
      mongodb:
        condition: service_healthy
      minio:
        condition: service_healthy
      mongo-init:
        condition: service_completed_successfully
      minio-init:
        condition: service_completed_successfully

  registry-frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - registry-backend

volumes:
  mongodb_data:
  minio_data:
