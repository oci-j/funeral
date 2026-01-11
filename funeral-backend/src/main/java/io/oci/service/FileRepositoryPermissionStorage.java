package io.oci.service;

import io.oci.model.RepositoryPermission;
import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class FileRepositoryPermissionStorage implements RepositoryPermissionStorage {

    @Inject
    @Named("userStorage")
    UserStorage userStorage;

    private final List<RepositoryPermission> permissions = new ArrayList<>();

    @Override
    public RepositoryPermission findByUsernameAndRepository(String username, String repositoryName) {
        return permissions.stream()
                .filter(p -> p.username.equals(username) && p.repositoryName.equals(repositoryName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<RepositoryPermission> findByUsername(String username) {
        return permissions.stream()
                .filter(p -> p.username.equals(username))
                .collect(Collectors.toList());
    }

    @Override
    public List<RepositoryPermission> findByRepository(String repositoryName) {
        return permissions.stream()
                .filter(p -> p.repositoryName.equals(repositoryName))
                .collect(Collectors.toList());
    }

    @Override
    public List<RepositoryPermission> listAll() {
        return new ArrayList<>(permissions);
    }

    @Override
    public void persist(RepositoryPermission permission) {
        // Update existing or add new
        permissions.removeIf(p -> p.username.equals(permission.username) &&
                                 p.repositoryName.equals(permission.repositoryName));
        permissions.add(permission);
    }

    @Override
    public void deleteByUsernameAndRepository(String username, String repositoryName) {
        permissions.removeIf(p -> p.username.equals(username) &&
                                 p.repositoryName.equals(repositoryName));
    }

    @Override
    public void deleteByUsername(String username) {
        permissions.removeIf(p -> p.username.equals(username));
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
