package io.oci.model;

import java.time.LocalDateTime;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@MongoEntity(
        collection = "repository_permissions"
)
public class RepositoryPermission extends PanacheMongoEntity {

    public String username;

    public String repositoryName;

    public Boolean canPull = false;

    public Boolean canPush = false;

    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    public RepositoryPermission() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public RepositoryPermission(
            String username,
            String repositoryName
    ) {
        this();
        this.username = username;
        this.repositoryName = repositoryName;
    }
}
