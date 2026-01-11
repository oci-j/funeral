package io.oci.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.List;

@RegisterForReflection
@MongoEntity(collection = "users")
public class User extends PanacheMongoEntity {

    public String username;

    public String passwordHash;

    public String email;

    public Boolean enabled = true;

    public List<String> allowedRepositories;

    public List<String> roles;

    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    public User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasAccessToRepository(String repositoryName) {
        if (roles != null && roles.contains("ADMIN")) {
            return true;
        }
        if (allowedRepositories == null || allowedRepositories.isEmpty()) {
            return true;
        }
        return allowedRepositories.contains(repositoryName);
    }

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}
