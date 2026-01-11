package io.oci.service;

import io.oci.model.Repository;
import java.util.List;

public interface RepositoryStorage {
    Repository findByName(String name);

    List<Repository> listAll();

    void persist(Repository repository);

    long count();

    void deleteByName(String name);

    List<Repository> findByNameWithMultipleEntries(String name);
}
