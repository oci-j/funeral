package io.oci.docker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.oci.docker.containerd.MetadataDbImageIdFinder;
import io.oci.service.DigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerdFileResolverTest {

    @TempDir
    Path tempDir;

    private ContainerdFileResolver resolver;

    private DigestService digestService;

    @BeforeEach
    void setUp() {
        resolver = new ContainerdFileResolver();
        digestService = new DigestService();
        resolver.digestService = digestService;
        resolver.directReadEnabled = true;
        resolver.containerdRoot = tempDir.resolve(
                "containerd"
        );
        resolver.dockerRoot = tempDir.resolve(
                "docker"
        );
        resolver.imageIdFinder = new MetadataDbImageIdFinder();
    }

    @Test
    void notAvailableWhenDirectoryMissing() {
        assertFalse(
                resolver.isAvailable()
        );
    }

    @Test
    void resolveBlob() throws Exception {
        byte[] content = "hello containerd blob".getBytes(
                StandardCharsets.UTF_8
        );
        String digest = digestService.calculateDigest(
                content
        );
        Path blobPath = resolver.containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs/sha256/" + digest.substring(
                        7
                )
        );
        Files.createDirectories(
                blobPath.getParent()
        );
        Files.write(
                blobPath,
                content
        );

        Optional<ResolvedBlob> resolved = resolver.resolveBlob(
                digest
        );
        assertTrue(
                resolved.isPresent()
        );
        assertEquals(
                content.length,
                resolved.get().size
        );
        try (java.io.InputStream is = resolved.get().stream) {
            byte[] read = new ByteArrayOutputStream().toByteArray();
            // readAllBytes is cleaner here
            byte[] actual = is.readAllBytes();
            assertArrayEquals(
                    content,
                    actual
            );
        }
    }

    @Test
    void resolveManifestByDigest() throws Exception {
        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"digest\":\"sha256:abc\"}}";
        byte[] content = manifestJson.getBytes(
                StandardCharsets.UTF_8
        );
        String digest = digestService.calculateDigest(
                content
        );
        Path blobPath = resolver.containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs/sha256/" + digest.substring(
                        7
                )
        );
        Files.createDirectories(
                blobPath.getParent()
        );
        Files.write(
                blobPath,
                content
        );

        Optional<ResolvedManifest> resolved = resolver.resolveManifest(
                digest,
                "myrepo"
        );
        assertTrue(
                resolved.isPresent()
        );
        assertEquals(
                "application/vnd.oci.image.manifest.v1+json",
                resolved.get().mediaType
        );
        assertEquals(
                digest,
                resolved.get().digest
        );
        assertArrayEquals(
                content,
                resolved.get().bytes
        );
    }

    @Test
    void resolveManifestByTagIgnored() throws Exception {
        resolver.containerdRoot = tempDir.resolve(
                "containerd"
        );
        Files.createDirectories(
                resolver.containerdRoot.resolve(
                        "io.containerd.content.v1.content/blobs"
                )
        );

        Optional<ResolvedManifest> resolved = resolver.resolveManifest(
                "latest",
                "myrepo"
        );
        assertFalse(
                resolved.isPresent()
        );
    }

    @Test
    void resolveMissingBlob() throws Exception {
        Files.createDirectories(
                resolver.containerdRoot.resolve(
                        "io.containerd.content.v1.content/blobs/sha256"
                )
        );

        Optional<ResolvedBlob> resolved = resolver.resolveBlob(
                "sha256:0000000000000000000000000000000000000000000000000000000000000000"
        );
        assertFalse(
                resolved.isPresent()
        );
    }
}
