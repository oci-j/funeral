package io.oci.service;

import java.util.List;

import io.oci.model.Manifest;

public interface ManifestStorage {
    Manifest findById(
            Object id
    );

    List<Manifest> listAll();

    void persist(
            Manifest manifest
    );

    Manifest findByRepositoryAndDigest(
            String repositoryName,
            String digest
    );

    Manifest findByRepositoryAndTag(
            String repositoryName,
            String tag
    );

    List<Manifest> findByRepository(
            String repositoryName
    );

    List<Manifest> findByRepositoryAndTagList(
            String repositoryName,
            String last,
            int limit
    );

    List<Manifest> findBySubjectDigest(
            String repositoryName,
            String subjectDigest
    );

    List<Manifest> findBySubjectDigestAndArtifactType(
            String repositoryName,
            String subjectDigest,
            String artifactType
    );

    List<String> findTagsByRepository(
            String repositoryName,
            String last,
            int limit
    );

    long countByRepository(
            String repositoryName
    );

    void deleteByRepositoryAndTag(
            String repositoryName,
            String tag
    );

    void delete(
            Object id
    );
}
