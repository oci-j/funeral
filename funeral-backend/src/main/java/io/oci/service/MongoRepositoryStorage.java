package io.oci.service;

import java.time.LocalDateTime;
import java.util.List;

import io.oci.model.Repository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MongoRepositoryStorage implements RepositoryStorage {

    @Override
    public Repository findByName(
            String name
    ) {
        return Repository.find(
                "name",
                io.quarkus.panache.common.Sort.by(
                        "updated_at",
                        io.quarkus.panache.common.Sort.Direction.Descending
                ),
                name
        ).firstResult();
    }

    @Override
    public List<Repository> listAll() {
        return Repository.listAll();
    }

    @Override
    public void persist(
            Repository repository
    ) {
        if (repository.id == null) {
            repository.id = new org.bson.types.ObjectId();
        }
        repository.updatedAt = LocalDateTime.now();
        if (repository.createdAt == null) {
            repository.createdAt = LocalDateTime.now();
        }
        repository.persistOrUpdate();
    }

    @Override
    public long count() {
        return Repository.count();
    }

    @Override
    public void deleteByName(
            String name
    ) {
        Repository.delete(
                "name",
                name
        );
    }

    @Override
    public List<Repository> findByNameWithMultipleEntries(
            String name
    ) {
        return Repository.find(
                "name",
                name
        ).list();
    }
}
