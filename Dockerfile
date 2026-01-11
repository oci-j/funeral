# Multi-stage Docker build for Funeral OCI Registry with integrated Frontend

# Stage 1: Build Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /build

# Install pnpm
RUN npm install -g pnpm

# Copy package files first (better layer caching)
COPY funeral-frontend/package.json funeral-frontend/pnpm-lock.yaml* ./

# Install dependencies (this layer is cached unless package.json changes)
RUN pnpm install --frozen-lockfile --prefer-offline

# Copy the rest of the source code
COPY funeral-frontend/ .

# Build frontend
RUN pnpm build

# Stage 2: Build Backend
FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /build

# Copy backend source code
COPY funeral-backend/pom.xml .
COPY funeral-backend/src ./src

# Copy frontend build files to Quarkus static resources
# This ensures frontend files are included in the final JAR
COPY --from=frontend-builder /build/dist ./src/main/resources/META-INF/resources

# Build backend (frontend files will be included)
RUN mvn clean package -DskipTests

# Stage 3: Create Final Runtime Image
FROM eclipse-temurin:17-jre-alpine

# Install required packages
RUN apk add --no-cache curl wget bash

# Create non-root user
RUN addgroup -g 1001 funeral && \
    adduser -D -u 1001 -G funeral funeral

# Set working directory
WORKDIR /opt/funeral

# Copy backend JAR from builder stage
COPY --from=backend-builder /build/target/quarkus-app/lib/ ./lib/
COPY --from=backend-builder /build/target/quarkus-app/*.jar ./
COPY --from=backend-builder /build/target/quarkus-app/app/ ./app/
COPY --from=backend-builder /build/target/quarkus-app/quarkus/ ./quarkus/

# Copy entrypoint script
COPY docker-entrypoint.sh /opt/funeral/docker-entrypoint.sh
RUN chmod +x /opt/funeral/docker-entrypoint.sh

# Change ownership to non-root user
RUN chown -R funeral:funeral /opt/funeral

# Switch to non-root user
USER funeral

# Expose the single port for all services
EXPOSE 8911

# Set environment variables
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="quarkus-run.jar"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8911/v2/ || exit 1

# Run the application
ENTRYPOINT ["/opt/funeral/docker-entrypoint.sh"]
