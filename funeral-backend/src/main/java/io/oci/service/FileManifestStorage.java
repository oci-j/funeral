package io.oci.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import io.oci.model.Manifest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(
    "file-manifest-storage"
)
public class FileManifestStorage implements ManifestStorage {

    private static final String COLLECTION = "manifests";

    @Inject
    FileStorageBase fileStorage;

    @Override
    public Manifest findById(
            Object id
    ) {
        return fileStorage.readFromFile(
                Manifest.class,
                COLLECTION,
                id.toString()
        );
    }

    @Override
    public List<Manifest> listAll() {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        );
    }

    @Override
    public void persist(
            Manifest manifest
    ) {
        if (manifest.id == null) {
            manifest.id = new org.bson.types.ObjectId();
        }
        manifest.updatedAt = LocalDateTime.now();
        if (manifest.createdAt == null) {
            manifest.createdAt = LocalDateTime.now();
        }
        fileStorage.writeToFile(
                manifest,
                COLLECTION,
                manifest.id.toString()
        );
    }

    @Override
    public Manifest findByRepositoryAndDigest(
            String repositoryName,
            String digest
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> digest.equals(
                                m.digest
                        )
                )
                .findFirst()
                .orElse(
                        null
                );
    }

    @Override
    public Manifest findByRepositoryAndTag(
            String repositoryName,
            String tag
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> tag.equals(
                                m.tag
                        )
                )
                .findFirst()
                .orElse(
                        null
                );
    }

    @Override
    public List<Manifest> findByRepository(
            String repositoryName
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .collect(
                        Collectors.toList()
                );
    }

    @Override
    public List<Manifest> findByRepositoryAndTagList(
            String repositoryName,
            String last,
            int limit
    ) {
        var manifests = findByRepository(
                repositoryName
        );
        return manifests.stream()
                .filter(
                        m -> m.tag != null
                )
                .filter(
                        m -> last == null || m.tag.compareTo(
                                last
                        ) > 0
                )
                .sorted(
                        (
                                m1,
                                m2
                        ) -> m2.updatedAt.compareTo(
                                m1.updatedAt
                        )
                )
                .limit(
                        limit
                )
                .collect(
                        Collectors.toList()
                );
    }

    @Override
    public List<Manifest> findBySubjectDigest(
            String repositoryName,
            String subjectDigest
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> m.subject != null && subjectDigest.equals(
                                m.subject.digest
                        )
                )
                .collect(
                        Collectors.toList()
                );
    }

    @Override
    public List<Manifest> findBySubjectDigestAndArtifactType(
            String repositoryName,
            String subjectDigest,
            String artifactType
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> m.subject != null && subjectDigest.equals(
                                m.subject.digest
                        )
                )
                .filter(
                        m -> artifactType.equals(
                                m.artifactType
                        )
                )
                .collect(
                        Collectors.toList()
                );
    }

    @Override
    public List<String> findTagsByRepository(
            String repositoryName,
            String last,
            int limit
    ) {
        return fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> m.tag != null
                )
                .filter(
                        m -> last == null || m.tag.compareTo(
                                last
                        ) > 0
                )
                .sorted(
                        (
                                m1,
                                m2
                        ) -> m2.updatedAt.compareTo(
                                m1.updatedAt
                        )
                )
                .limit(
                        limit
                )
                .map(
                        m -> m.tag
                )
                .collect(
                        Collectors.toList()
                );
    }

    @Override
    public long countByRepository(
            String repositoryName
    ) {
        return fileStorage.countWithFilter(
                COLLECTION,
                (
                        Manifest m
                ) -> repositoryName.equals(
                        m.repositoryName
                ) && m.tag != null,
                Manifest.class
        );
    }

    @Override
    public void deleteByRepositoryAndTag(
            String repositoryName,
            String tag
    ) {
        List<Manifest> manifests = fileStorage.readAllFromFiles(
                Manifest.class,
                COLLECTION
        )
                .stream()
                .filter(
                        m -> repositoryName.equals(
                                m.repositoryName
                        )
                )
                .filter(
                        m -> tag.equals(
                                m.tag
                        )
                )
                .collect(
                        Collectors.toList()
                );

        for (Manifest m : manifests) {
            if (m.id != null) {
                fileStorage.deleteFile(
                        COLLECTION,
                        m.id.toString()
                );
            }
        }
    }

    @Override
    public void delete(
            Object id
    ) {
        fileStorage.deleteFile(
                COLLECTION,
                id.toString()
        );
    }
}
