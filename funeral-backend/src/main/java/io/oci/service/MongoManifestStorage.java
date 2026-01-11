package io.oci.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import io.oci.model.Manifest;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MongoManifestStorage implements ManifestStorage {

    @Override
    public Manifest findById(
            Object id
    ) {
        return Manifest.findById(
                id
        );
    }

    @Override
    public List<Manifest> listAll() {
        return Manifest.listAll();
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
        manifest.persistOrUpdate();
    }

    @Override
    public Manifest findByRepositoryAndDigest(
            String repositoryName,
            String digest
    ) {
        return Manifest.find(
                "repository_name = ?1 and digest = ?2",
                Sort.by(
                        "updated_at",
                        Sort.Direction.Descending
                ),
                repositoryName,
                digest
        ).firstResult();
    }

    @Override
    public Manifest findByRepositoryAndTag(
            String repositoryName,
            String tag
    ) {
        return Manifest.find(
                "repository_name = ?1 and tag = ?2",
                Sort.by(
                        "updated_at",
                        Sort.Direction.Descending
                ),
                repositoryName,
                tag
        ).firstResult();
    }

    @Override
    public List<Manifest> findByRepository(
            String repositoryName
    ) {
        return Manifest.find(
                "repository_name",
                Sort.by(
                        "updated_at",
                        Sort.Direction.Descending
                ),
                repositoryName
        ).list();
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
        return Manifest.find(
                "repository_name = ?1 and subject.digest = ?2",
                Sort.by(
                        "updated_at",
                        Sort.Direction.Descending
                ),
                repositoryName,
                subjectDigest
        ).list();
    }

    @Override
    public List<Manifest> findBySubjectDigestAndArtifactType(
            String repositoryName,
            String subjectDigest,
            String artifactType
    ) {
        return Manifest.find(
                "repository_name = ?1 and subject.digest = ?2 and artifact_type = ?3",
                Sort.by(
                        "updated_at",
                        Sort.Direction.Descending
                ),
                repositoryName,
                subjectDigest,
                artifactType
        ).list();
    }

    @Override
    public List<String> findTagsByRepository(
            String repositoryName,
            String last,
            int limit
    ) {
        List<Manifest> manifests;
        if (last == null) {
            manifests = Manifest.find(
                    "repository_name = ?1 and tag != ?2",
                    Sort.by(
                            "updated_at",
                            Sort.Direction.Descending
                    ),
                    repositoryName,
                    null
            )
                    .page(
                            0,
                            limit
                    )
                    .list();
        }
        else {
            manifests = Manifest.find(
                    "repository_name = ?1 and tag != ?2 and tag > ?3",
                    Sort.by(
                            "updated_at",
                            Sort.Direction.Descending
                    ),
                    repositoryName,
                    null,
                    last
            )
                    .page(
                            0,
                            limit
                    )
                    .list();
        }
        List<String> result = manifests.stream()
                .map(
                        manifest -> manifest.tag
                )
                .toList();
        System.out.println(
                "repositoryName : " + repositoryName
        );
        System.out.println(
                "last : " + last
        );
        System.out.println(
                "limit : " + limit
        );
        System.out.println(
                result
        );
        return result;
    }

    @Override
    public long countByRepository(
            String repositoryName
    ) {
        return Manifest.count(
                "repository_name = ?1 and tag != null",
                repositoryName
        );
    }

    @Override
    public void deleteByRepositoryAndTag(
            String repositoryName,
            String tag
    ) {
        Manifest.delete(
                "repository_name = ?1 and tag = ?2",
                repositoryName,
                tag
        );
    }

    @Override
    public void delete(
            Object id
    ) {
        Manifest.deleteById(
                id
        );
    }
}
