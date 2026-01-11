package io.oci.config;

import io.oci.service.S3StorageService;
import io.oci.service.StorageService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
public class StorageConfig {

    @Inject
    S3StorageService s3StorageService;

    @Inject
    StorageService localStorageService;

    @ConfigProperty(name = "oci.storage.no-minio", defaultValue = "false")
    boolean noMinio;

    @Produces
    @Singleton
    @DefaultBean
    public Object storageService() {
        if (noMinio) {
            return localStorageService;
        } else {
            return s3StorageService;
        }
    }
}
