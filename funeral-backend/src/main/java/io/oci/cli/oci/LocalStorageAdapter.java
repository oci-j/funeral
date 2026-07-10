package io.oci.cli.oci;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.DigestService;
import io.oci.service.FileStorageBase;
import org.bson.types.ObjectId;

public class LocalStorageAdapter {

    private final String storagePath;

    private final FileStorageBase fileStorage;

    private final DigestService digestService;

    public LocalStorageAdapter(
            String storagePath
    ) {
        this.storagePath = storagePath;
        this.fileStorage = new FileStorageBase(
                storagePath
        );
        this.digestService = new DigestService();
    }

    public boolean isAvailable() {
        if (storagePath == null || storagePath.isEmpty()) {
            return false;
        }
        return Files.isDirectory(
                Paths.get(
                        storagePath
                )
        );
    }

    public byte[] readManifest(
            String repositoryName,
            String reference
    ) {
        Manifest manifest = findManifest(
                repositoryName,
                reference
        );
        if (manifest == null || manifest.content == null) {
            return null;
        }
        return manifest.content.getBytes(
                StandardCharsets.UTF_8
        );
    }

    public String readManifestMediaType(
            String repositoryName,
            String reference
    ) {
        Manifest manifest = findManifest(
                repositoryName,
                reference
        );
        return manifest == null ? null : manifest.mediaType;
    }

    public byte[] readBlob(
            String digest
    ) {
        Path path = resolveBlobPath(
                digest
        );
        if (path == null || !Files.isRegularFile(
                path
        )) {
            return null;
        }
        try {
            return Files.readAllBytes(
                    path
            );
        }
        catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read blob " + digest,
                    e
            );
        }
    }

    public void writeManifest(
            String repositoryName,
            String reference,
            byte[] manifestBytes,
            String mediaType
    ) {
        String digest = digestService.calculateDigest(
                manifestBytes
        );
        String content = new String(
                manifestBytes,
                StandardCharsets.UTF_8
        );
        ensureRepository(
                repositoryName
        );

        Manifest existing = findManifestByDigest(
                repositoryName,
                digest
        );
        if (existing != null) {
            if (!reference.startsWith(
                    "sha256:"
            )) {
                existing.tag = reference;
                existing.updatedAt = LocalDateTime.now();
                fileStorage.writeToFile(
                        existing,
                        "manifests",
                        existing.id.toString()
                );
            }
            return;
        }

        Manifest manifest = new Manifest();
        manifest.id = new ObjectId();
        manifest.repositoryId = findRepositoryId(
                repositoryName
        );
        manifest.repositoryName = repositoryName;
        manifest.digest = digest;
        manifest.mediaType = mediaType != null ? mediaType : "application/vnd.oci.image.manifest.v1+json";
        manifest.content = content;
        manifest.contentLength = (long) manifestBytes.length;
        if (!reference.startsWith(
                "sha256:"
        )) {
            manifest.tag = reference;
        }
        manifest.createdAt = LocalDateTime.now();
        manifest.updatedAt = manifest.createdAt;
        fileStorage.writeToFile(
                manifest,
                "manifests",
                manifest.id.toString()
        );
    }

    public void writeBlob(
            String digest,
            byte[] content
    ) {
        Path path = resolveBlobPath(
                digest
        );
        if (path == null) {
            throw new IllegalArgumentException(
                    "Invalid digest: " + digest
            );
        }
        try {
            Files.createDirectories(
                    path.getParent()
            );
            Files.write(
                    path,
                    content
            );
        }
        catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write blob " + digest,
                    e
            );
        }

        String existingId = findBlobMetadataId(
                digest
        );
        if (existingId == null) {
            Blob blob = new Blob();
            blob.id = new ObjectId();
            blob.digest = digest;
            blob.contentLength = (long) content.length;
            blob.createdAt = LocalDateTime.now();
            blob.updatedAt = blob.createdAt;
            fileStorage.writeToFile(
                    blob,
                    "blobs",
                    blob.id.toString()
            );
        }
    }

    public void ensureRepository(
            String repositoryName
    ) {
        if (findRepository(
                repositoryName
        ) != null) {
            return;
        }
        Repository repo = new Repository(
                repositoryName
        );
        repo.id = new ObjectId();
        fileStorage.writeToFile(
                repo,
                "repositories",
                repo.id.toString()
        );
    }

    private Manifest findManifest(
            String repositoryName,
            String reference
    ) {
        List<Manifest> manifests = fileStorage.readAllFromFiles(
                Manifest.class,
                "manifests"
        );
        for (Manifest m : manifests) {
            if (!repositoryName.equals(
                    m.repositoryName
            )) {
                continue;
            }
            if (reference.startsWith(
                    "sha256:"
            )) {
                if (reference.equals(
                        m.digest
                )) {
                    return m;
                }
            }
            else {
                if (reference.equals(
                        m.tag
                )) {
                    return m;
                }
            }
        }
        return null;
    }

    private Manifest findManifestByDigest(
            String repositoryName,
            String digest
    ) {
        List<Manifest> manifests = fileStorage.readAllFromFiles(
                Manifest.class,
                "manifests"
        );
        for (Manifest m : manifests) {
            if (repositoryName.equals(
                    m.repositoryName
            ) && digest.equals(
                    m.digest
            )) {
                return m;
            }
        }
        return null;
    }

    private Repository findRepository(
            String repositoryName
    ) {
        List<Repository> repos = fileStorage.readAllFromFiles(
                Repository.class,
                "repositories"
        );
        for (Repository r : repos) {
            if (repositoryName.equals(
                    r.name
            )) {
                return r;
            }
        }
        return null;
    }

    private ObjectId findRepositoryId(
            String repositoryName
    ) {
        Repository r = findRepository(
                repositoryName
        );
        return r == null ? null : r.id;
    }

    private String findBlobMetadataId(
            String digest
    ) {
        List<Blob> blobs = fileStorage.readAllFromFiles(
                Blob.class,
                "blobs"
        );
        for (Blob b : blobs) {
            if (digest.equals(
                    b.digest
            )) {
                return b.id.toString();
            }
        }
        return null;
    }

    private Path resolveBlobPath(
            String digest
    ) {
        int colon = digest.indexOf(
                ':'
        );
        if (colon < 0) {
            return null;
        }
        String algorithm = digest.substring(
                0,
                colon
        );
        String hex = digest.substring(
                colon + 1
        );
        return Paths.get(
                storagePath,
                "blobs",
                algorithm,
                hex
        );
    }
}
