package io.oci.service;

import java.util.List;

import io.oci.model.Repository;

public interface RepositoryStorage {
    Repository findByName(
            String name
    );

    List<Repository> listAll();

    void persist(
            Repository repository
    );

    long count();

    void deleteByName(
            String name
    );

    List<Repository> findByNameWithMultipleEntries(
            String name
    );
}
