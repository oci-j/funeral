package io.oci.service;

import io.oci.model.Repository;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class FileRepositoryStorage extends FileStorageBase {

    private static final String COLLECTION = "repositories";

    @ConfigProperty(name = "oci.storage.no-mongo", defaultValue = "false")
    boolean noMongo;

    public Repository findByName(String name) {
        if (!noMongo) {
            // Use MongoDB Panache method
            return Repository.findByName(name);
        }

        return findFirst(Repository.class, COLLECTION, repo -> repo.name.equals(name))
                .orElse(null);
    }

    public List<Repository> listAll() {
        if (!noMongo) {
            // Use MongoDB Panache method
            return Repository.listAll();
        }

        return readAllFromFiles(Repository.class, COLLECTION);
    }

    public void persist(Repository repository) {
        if (!noMongo) {
            // Use MongoDB Panache method
            repository.persist();
            return;
        }

        if (repository.id == null) {
            repository.id = new org.bson.types.ObjectId();
        }
        repository.updatedAt = LocalDateTime.now();
        if (repository.createdAt == null) {
            repository.createdAt = LocalDateTime.now();
        }
        writeToFile(repository, COLLECTION, repository.id.toString());
    }

    public long count() {
        if (!noMongo) {
            // Use MongoDB Panache method
            return Repository.count();
        }

        return countFiles(COLLECTION);
    }

    public void deleteByName(String name) {
        if (!noMongo) {
            // Use MongoDB Panache method
            Repository.delete("name", name);
            return;
        }

        Repository repo = findByName(name);
        if (repo != null && repo.id != null) {
            deleteFile(COLLECTION, repo.id.toString());
        }
    }

    public List<Repository> findByNameWithMultipleEntries(String name) {
        if (!noMongo) {
            // Use MongoDB Panache method
            return Repository.find("name", name).list();
        }

        return readAllFromFiles(Repository.class, COLLECTION).stream()
                .filter(repo -> repo.name.equals(name))
                .collect(Collectors.toList());
    }
}
