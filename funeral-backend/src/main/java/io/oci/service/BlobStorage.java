package io.oci.service;

import io.oci.model.Blob;

public interface BlobStorage {
    Blob findByDigest(
            String digest
    );

    void persist(
            Blob blob
    );

    void deleteByDigest(
            String digest
    );

    void delete(
            Object id
    );
}
