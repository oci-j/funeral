package io.oci.config;

import io.oci.service.BlobStorage;
import io.oci.service.FileBlobStorage;
import io.oci.service.FileManifestStorage;
import io.oci.service.FileRepositoryPermissionStorage;
import io.oci.service.FileRepositoryStorage;
import io.oci.service.FileUserStorage;
import io.oci.service.ManifestStorage;
import io.oci.service.MongoBlobStorage;
import io.oci.service.MongoManifestStorage;
import io.oci.service.MongoRepositoryPermissionStorage;
import io.oci.service.MongoRepositoryStorage;
import io.oci.service.MongoUserStorage;
import io.oci.service.RepositoryPermissionStorage;
import io.oci.service.RepositoryStorage;
import io.oci.service.UserStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MongoDBConfig {

    @Inject
    MongoManifestStorage mongoManifestStorage;

    @Inject
    FileManifestStorage fileManifestStorage;

    @Inject
    MongoRepositoryStorage mongoRepositoryStorage;

    @Inject
    FileRepositoryStorage fileRepositoryStorage;

    @Inject
    MongoUserStorage mongoUserStorage;

    @Inject
    FileUserStorage fileUserStorage;

    @Inject
    MongoBlobStorage mongoBlobStorage;

    @Inject
    FileBlobStorage fileBlobStorage;

    @Inject
    MongoRepositoryPermissionStorage mongoRepositoryPermissionStorage;

    @Inject
    FileRepositoryPermissionStorage fileRepositoryPermissionStorage;

    @ConfigProperty(
            name = "oci.storage.no-mongo",
            defaultValue = "false"
    )
    boolean noMongo;

    @Produces
    @Named(
        "manifestStorage"
    )
    public ManifestStorage manifestStorage() {
        return noMongo ? fileManifestStorage : mongoManifestStorage;
    }

    @Produces
    @Named(
        "repositoryStorage"
    )
    public RepositoryStorage repositoryStorage() {
        return noMongo ? fileRepositoryStorage : mongoRepositoryStorage;
    }

    @Produces
    @Named(
        "userStorage"
    )
    public UserStorage userStorage() {
        return noMongo ? fileUserStorage : mongoUserStorage;
    }

    @Produces
    @Named(
        "blobStorage"
    )
    public BlobStorage blobStorage() {
        return noMongo ? fileBlobStorage : mongoBlobStorage;
    }

    @Produces
    @Named(
        "repositoryPermissionStorage"
    )
    public RepositoryPermissionStorage repositoryPermissionStorage() {
        return noMongo ? fileRepositoryPermissionStorage : mongoRepositoryPermissionStorage;
    }
}
