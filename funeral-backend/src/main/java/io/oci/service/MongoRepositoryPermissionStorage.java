package io.oci.service;

import io.oci.model.RepositoryPermission;
import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@ApplicationScoped
public class MongoRepositoryPermissionStorage implements RepositoryPermissionStorage {

    @Inject
    @Named("userStorage")
    UserStorage userStorage;

    @Override
    public RepositoryPermission findByUsernameAndRepository(String username, String repositoryName) {
        return RepositoryPermission.find("username = ?1 and repositoryName = ?2", username, repositoryName).firstResult();
    }

    @Override
    public List<RepositoryPermission> findByUsername(String username) {
        return RepositoryPermission.list("username", username);
    }

    @Override
    public List<RepositoryPermission> findByRepository(String repositoryName) {
        return RepositoryPermission.list("repositoryName", repositoryName);
    }

    @Override
    public List<RepositoryPermission> listAll() {
        return RepositoryPermission.listAll();
    }

    @Override
    public void persist(RepositoryPermission permission) {
        if (permission.id == null) {
            permission.id = new org.bson.types.ObjectId();
        }
        permission.updatedAt = java.time.LocalDateTime.now();
        permission.persist();
    }

    @Override
    public void deleteByUsernameAndRepository(String username, String repositoryName) {
        RepositoryPermission.delete("username = ?1 and repositoryName = ?2", username, repositoryName);
    }

    @Override
    public void deleteByUsername(String username) {
        RepositoryPermission.delete("username", username);
    }

    @Override
    public boolean hasPullPermission(String username, String repositoryName) {
        // Admin has all permissions
        User user = userStorage.findByUsername(username);
        if (user != null && user.isAdmin()) {
            return true;
        }

        RepositoryPermission permission = findByUsernameAndRepository(username, repositoryName);
        return permission != null && permission.canPull;
    }

    @Override
    public boolean hasPushPermission(String username, String repositoryName) {
        // Admin has all permissions
        User user = userStorage.findByUsername(username);
        if (user != null && user.isAdmin()) {
            return true;
        }

        RepositoryPermission permission = findByUsernameAndRepository(username, repositoryName);
        return permission != null && permission.canPush;
    }
}
