package io.oci.service;

import io.oci.model.Blob;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("mongo-blob-storage")
public class MongoBlobStorage implements BlobStorage {

    @Override
    public Blob findByDigest(String digest) {
        return Blob.find("digest", Sort.by("updated_at", Sort.Direction.Descending), digest).firstResult();
    }

    @Override
    public void persist(Blob blob) {
        if (blob.id == null) {
            blob.id = new org.bson.types.ObjectId();
        }
        blob.persist();
    }

    @Override
    public void deleteByDigest(String digest) {
        Blob.delete("digest", digest);
    }

    @Override
    public void delete(Object id) {
        Blob.deleteById(id);
    }
}
