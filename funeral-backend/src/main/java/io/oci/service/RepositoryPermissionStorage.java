package io.oci.service;

import java.util.List;

import io.oci.model.RepositoryPermission;

public interface RepositoryPermissionStorage {
    RepositoryPermission findByUsernameAndRepository(
            String username,
            String repositoryName
    );

    List<RepositoryPermission> findByUsername(
            String username
    );

    List<RepositoryPermission> findByRepository(
            String repositoryName
    );

    List<RepositoryPermission> listAll();

    void persist(
            RepositoryPermission permission
    );

    void deleteByUsernameAndRepository(
            String username,
            String repositoryName
    );

    void deleteByUsername(
            String username
    );

    boolean hasPullPermission(
            String username,
            String repositoryName
    );

    boolean hasPushPermission(
            String username,
            String repositoryName
    );
}
