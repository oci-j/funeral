package io.oci.config;

import io.oci.service.AbstractStorageService;
import io.oci.service.S3StorageService;
import io.oci.service.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StorageConfig {

    @Produces
    @Named("storage")
    public AbstractStorageService storageService(
            S3StorageService s3StorageService,
            StorageService localStorageService,
            @ConfigProperty(name = "oci.storage.no-minio", defaultValue = "false") boolean noMinio
    ) {
        if (noMinio) {
            return localStorageService;
        } else {
            return s3StorageService;
        }
    }
}
