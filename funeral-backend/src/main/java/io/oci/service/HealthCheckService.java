package io.oci.service;

import io.minio.MinioClient;
import io.oci.dto.HealthCheckResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(
            HealthCheckService.class
    );

    @Inject
    @Named(
        "repositoryStorage"
    )
    RepositoryStorage repositoryStorage;

    @Inject
    @Named(
        "manifestStorage"
    )
    ManifestStorage manifestStorage;

    @Inject
    @Named(
        "storage"
    )
    AbstractStorageService abstractStorageService;

    @Inject
    MinioClient minioClient;

    @ConfigProperty(
            name = "quarkus.application.version",
            defaultValue = "unknown"
    )
    String version;

    @ConfigProperty(
            name = "oci.storage.bucket",
            defaultValue = "oci-registry"
    )
    String bucketName;

    private long startTime;

    void onStart(
            @Observes
            StartupEvent ev
    ) {
        startTime = System.currentTimeMillis();
        log.info(
                "HealthCheckService started at {}",
                startTime
        );
    }

    public HealthCheckResponse checkHealth() {
        long checkStartTime = System.currentTimeMillis();

        HealthCheckResponse.ServiceStatus mongoStatus = checkMongoDB();
        HealthCheckResponse.ServiceStatus storageStatus = checkStorage();

        long checkDuration = System.currentTimeMillis() - checkStartTime;

        String overallStatus = ("UP".equals(
                mongoStatus.status
        ) && "UP".equals(
                storageStatus.status
        )) ? "UP" : "DOWN";

        HealthCheckResponse.Services services = new HealthCheckResponse.Services(
                mongoStatus,
                storageStatus
        );

        long uptime = System.currentTimeMillis() - startTime;

        return new HealthCheckResponse(
                overallStatus,
                services,
                version,
                uptime
        );
    }

    private HealthCheckResponse.ServiceStatus checkMongoDB() {
        long start = System.currentTimeMillis();

        try {
            // Check if MongoDB is configured and accessible by attempting a simple query
            repositoryStorage.count();
            long duration = System.currentTimeMillis() - start;
            return new HealthCheckResponse.ServiceStatus(
                    "UP",
                    "MongoDB is accessible",
                    duration
            );
        }
        catch (Exception e) {
            log.warn(
                    "MongoDB health check failed: {}",
                    e.getMessage()
            );
            long duration = System.currentTimeMillis() - start;
            return new HealthCheckResponse.ServiceStatus(
                    "DOWN",
                    "MongoDB is not accessible: " + e.getMessage(),
                    duration
            );
        }
    }

    private HealthCheckResponse.ServiceStatus checkStorage() {
        long start = System.currentTimeMillis();

        try {
            // Check if MinIO/S3 storage is accessible
            if (minioClient != null) {
                // Try to check if the bucket exists as a basic connectivity test
                boolean bucketExists = minioClient.bucketExists(
                        io.minio.BucketExistsArgs.builder()
                                .bucket(
                                        bucketName
                                )
                                .build()
                );

                if (bucketExists) {
                    long duration = System.currentTimeMillis() - start;
                    return new HealthCheckResponse.ServiceStatus(
                            "UP",
                            "S3/MinIO storage is accessible",
                            duration
                    );
                }
                else {
                    long duration = System.currentTimeMillis() - start;
                    return new HealthCheckResponse.ServiceStatus(
                            "DOWN",
                            "S3/MinIO bucket '" + bucketName + "' does not exist",
                            duration
                    );
                }
            }
            else {
                // If no MinIO client, check if file-based storage is working
                // by attempting to check a non-existent blob
                try {
                    abstractStorageService.blobExists(
                            "health-check-test"
                    );
                    long duration = System.currentTimeMillis() - start;
                    return new HealthCheckResponse.ServiceStatus(
                            "UP",
                            "File storage is accessible",
                            duration
                    );
                }
                catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage()
                            .contains(
                                    "No such file"
                            )) {
                        // This is expected for non-existent blobs in file storage
                        long duration = System.currentTimeMillis() - start;
                        return new HealthCheckResponse.ServiceStatus(
                                "UP",
                                "File storage is accessible",
                                duration
                        );
                    }
                    log.warn(
                            "File storage health check failed: {}",
                            e.getMessage()
                    );
                    long duration = System.currentTimeMillis() - start;
                    return new HealthCheckResponse.ServiceStatus(
                            "DOWN",
                            "File storage is not accessible: " + e.getMessage(),
                            duration
                    );
                }
            }

        }
        catch (Exception e) {
            log.warn(
                    "Storage health check failed: {}",
                    e.getMessage()
            );
            long duration = System.currentTimeMillis() - start;
            return new HealthCheckResponse.ServiceStatus(
                    "DOWN",
                    "Storage is not accessible: " + e.getMessage(),
                    duration
            );
        }
    }
}
