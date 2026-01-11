package io.oci.config;

import io.minio.MinioClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MinioConfig {

    @ConfigProperty(
            name = "oci.s3.endpoint",
            defaultValue = "http://localhost:9000"
    )
    String endpoint;

    @ConfigProperty(
            name = "oci.s3.access-key",
            defaultValue = "minioadmin"
    )
    String accessKey;

    @ConfigProperty(
            name = "oci.s3.secret-key",
            defaultValue = "minioadmin"
    )
    String secretKey;

    @Produces
    @Singleton
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(
                        endpoint
                )
                .credentials(
                        accessKey,
                        secretKey
                )
                .build();
    }
}
