package io.oci.service;

import io.oci.model.Blob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Named("file-blob-storage")
public class FileBlobStorage implements BlobStorage {

    private static final String COLLECTION = "blobs";

    @Inject
    FileStorageBase fileStorage;

    @Override
    public Blob findByDigest(String digest) {
        return listAll().stream()
                .filter(b -> digest.equals(b.digest))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void persist(Blob blob) {
        if (blob.id == null) {
            blob.id = new org.bson.types.ObjectId();
        }
        fileStorage.writeToFile(blob, COLLECTION, blob.id.toString());
    }

    @Override
    public void deleteByDigest(String digest) {
        List<Blob> blobs = fileStorage.readAllFromFiles(Blob.class, COLLECTION).stream()
                .filter(b -> digest.equals(b.digest))
                .collect(Collectors.toList());

        for (Blob b : blobs) {
            if (b.id != null) {
                fileStorage.deleteFile(COLLECTION, b.id.toString());
            }
        }
    }

    @Override
    public void delete(Object id) {
        fileStorage.deleteFile(COLLECTION, id.toString());
    }

    private List<Blob> listAll() {
        return fileStorage.readAllFromFiles(Blob.class, COLLECTION);
    }
}
