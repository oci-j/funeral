package io.oci.service;

import io.oci.model.Manifest;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class FileManifestStorage extends FileStorageBase {

    private static final String COLLECTION = "manifests";

    @ConfigProperty(name = "oci.storage.no-mongo", defaultValue = "false")
    boolean noMongo;

    public Manifest findById(Object id) {
        if (!noMongo) {
            return Manifest.findById(id);
        }

        return readFromFile(Manifest.class, COLLECTION, id.toString());
    }

    public List<Manifest> listAll() {
        if (!noMongo) {
            return Manifest.listAll();
        }

        return readAllFromFiles(Manifest.class, COLLECTION);
    }

    public void persist(Manifest manifest) {
        if (!noMongo) {
            manifest.persist();
            return;
        }

        if (manifest.id == null) {
            manifest.id = new org.bson.types.ObjectId();
        }
        manifest.updatedAt = LocalDateTime.now();
        if (manifest.createdAt == null) {
            manifest.createdAt = LocalDateTime.now();
        }
        writeToFile(manifest, COLLECTION, manifest.id.toString());
    }

    public Manifest findByRepositoryAndDigest(String repositoryName, String digest) {
        if (!noMongo) {
            return Manifest.findByRepositoryAndDigest(repositoryName, digest);
        }

        return readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .filter(m -> digest.equals(m.digest))
                .findFirst()
                .orElse(null);
    }

    public Manifest findByRepositoryAndTag(String repositoryName, String tag) {
        if (!noMongo) {
            return Manifest.findByRepositoryAndTag(repositoryName, tag);
        }

        return readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .filter(m -> tag.equals(m.tag))
                .findFirst()
                .orElse(null);
    }

    public List<Manifest> findByRepository(String repositoryName) {
        if (!noMongo) {
            return Manifest.findByRepository(repositoryName);
        }

        return readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .collect(Collectors.toList());
    }

    public List<Manifest> findByRepositoryAndTagList(String repositoryName, String last, int limit) {
        // Read all manifests for the repository and filter by tag
        var manifests = findByRepository(repositoryName);
        return manifests.stream()
                .filter(m -> m.tag != null)
                .filter(m -> last == null || m.tag.compareTo(last) > 0)
                .sorted((m1, m2) -> m2.updatedAt.compareTo(m1.updatedAt))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Manifest> findBySubjectDigest(String repositoryName, String subjectDigest) {
        if (!noMongo) {
            return Manifest.findBySubjectDigest(repositoryName, subjectDigest);
        }

        return readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .filter(m -> m.subject != null && subjectDigest.equals(m.subject.digest))
                .collect(Collectors.toList());
    }

    public List<String> findTagsByRepository(String repositoryName, String last, int limit) {
        if (!noMongo) {
            return Manifest.findTagsByRepository(repositoryName, last, limit);
        }

        return readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .filter(m -> m.tag != null)
                .filter(m -> last == null || m.tag.compareTo(last) > 0)
                .sorted((m1, m2) -> m2.updatedAt.compareTo(m1.updatedAt))
                .limit(limit)
                .map(m -> m.tag)
                .collect(Collectors.toList());
    }

    public long countByRepository(String repositoryName) {
        if (!noMongo) {
            return Manifest.countByRepository(repositoryName);
        }

        return countWithFilter(COLLECTION, (Manifest m) ->
                repositoryName.equals(m.repositoryName) && m.tag != null, Manifest.class);
    }

    public void deleteByRepositoryAndTag(String repositoryName, String tag) {
        if (!noMongo) {
            Manifest.delete("repository_name = ?1 and tag = ?2", repositoryName, tag);
            return;
        }

        List<Manifest> manifests = readAllFromFiles(Manifest.class, COLLECTION).stream()
                .filter(m -> repositoryName.equals(m.repositoryName))
                .filter(m -> tag.equals(m.tag))
                .collect(Collectors.toList());

        for (Manifest m : manifests) {
            if (m.id != null) {
                deleteFile(COLLECTION, m.id.toString());
            }
        }
    }

    public void delete(Object id) {
        if (!noMongo) {
            Manifest.deleteById(id);
            return;
        }

        deleteFile(COLLECTION, id.toString());
    }
}
