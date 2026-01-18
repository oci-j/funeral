# Create Final Runtime Image
FROM ubuntu:25.10

ENV AUTH_ENABLED="false"

## Install required packages
#RUN apt update;apt install -y curl wget bash

# Set working directory
WORKDIR /opt/funeral

# Copy backend JAR from builder stage
COPY funeral-*-runner /app/funeral

# Copy entrypoint script
COPY docker-entrypoint.sh /opt/funeral/docker-entrypoint.sh
RUN chmod +x /opt/funeral/docker-entrypoint.sh

# Expose the single port for all services
EXPOSE 8911

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8911/funeral_addition/health/ready || exit 1

# Run the application
ENTRYPOINT ["/opt/funeral/docker-entrypoint.sh"]
