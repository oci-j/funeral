package io.oci.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import io.oci.model.Repository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(
    "file-repository-storage"
)
public class FileRepositoryStorage implements RepositoryStorage {

    private final String COLLECTION = "repositories";

    @Inject
    FileStorageBase fileStorage;

    @Override
    public Repository findByName(
            String name
    ) {
        return fileStorage.findFirst(
                Repository.class,
                COLLECTION,
                repo -> repo.name.equals(
                        name
                )
        )
                .orElse(
                        null
                );
    }

    @Override
    public List<Repository> listAll() {
        return fileStorage.readAllFromFiles(
                Repository.class,
                COLLECTION
        );
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
        fileStorage.writeToFile(
                repository,
                COLLECTION,
                repository.id.toString()
        );
    }

    @Override
    public long count() {
        return fileStorage.countFiles(
                COLLECTION
        );
    }

    @Override
    public void deleteByName(
            String name
    ) {
        Repository repo = findByName(
                name
        );
        if (repo != null && repo.id != null) {
            fileStorage.deleteFile(
                    COLLECTION,
                    repo.id.toString()
            );
        }
    }

    @Override
    public List<Repository> findByNameWithMultipleEntries(
            String name
    ) {
        return fileStorage.readAllFromFiles(
                Repository.class,
                COLLECTION
        )
                .stream()
                .filter(
                        repo -> repo.name.equals(
                                name
                        )
                )
                .collect(
                        Collectors.toList()
                );
    }
}
