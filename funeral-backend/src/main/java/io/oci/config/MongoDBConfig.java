package io.oci.config;

import io.oci.service.FileManifestStorage;
import io.oci.service.FileRepositoryStorage;
import io.oci.service.FileUserStorage;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
public class MongoDBConfig {

    @Inject
    FileManifestStorage fileManifestStorage;

    @Inject
    FileRepositoryStorage fileRepositoryStorage;

    @Inject
    FileUserStorage fileUserStorage;

    @ConfigProperty(name = "oci.storage.no-mongo", defaultValue = "false")
    boolean noMongo;

    @Produces
    @Singleton
    public Object manifestStorage() {
        return fileManifestStorage;
    }

    @Produces
    @Singleton
    public Object repositoryStorage() {
        return fileRepositoryStorage;
    }

    @Produces
    @Singleton
    public Object userStorage() {
        return fileUserStorage;
    }
}