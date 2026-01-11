package io.oci.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
@MongoEntity(collection = "repository_permissions")
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

    public RepositoryPermission(String username, String repositoryName) {
        this();
        this.username = username;
        this.repositoryName = repositoryName;
    }
}
