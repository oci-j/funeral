package io.oci.docker;

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
    void setUp() throws Exception {
        resolver = new ContainerdFileResolver();
        digestService = new DigestService();
        resolver.digestService = digestService;
        resolver.directReadEnabled = true;
        resolver.containerdRoot = tempDir.resolve(
                "containerd"
        );
        resolver.dockerRoot = copyFixtureToDockerRoot();
        resolver.imageIdFinder = new MetadataDbImageIdFinder();

        Files.createDirectories(
                resolver.containerdRoot.resolve(
                        "io.containerd.content.v1.content/blobs"
                )
        );
    }

    @Test
    void notAvailableWhenDirectoryMissing() {
        resolver.containerdRoot = tempDir.resolve(
                "missing"
        );
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
        Path blobPath = writeBlob(
                digest,
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
        writeBlob(
                digest,
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
    void resolveManifestByTag() throws Exception {
        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.index.v1+json\",\"manifests\":[]}";
        byte[] content = manifestJson.getBytes(
                StandardCharsets.UTF_8
        );
        writeBlob(
                "sha256:abc123",
                content
        );

        Optional<ResolvedManifest> resolved = resolver.resolveManifest(
                "3.20",
                "alpine"
        );
        assertTrue(
                resolved.isPresent()
        );
        assertEquals(
                "application/vnd.oci.image.index.v1+json",
                resolved.get().mediaType
        );
        assertArrayEquals(
                content,
                resolved.get().bytes
        );
    }

    @Test
    void resolveManifestByTagWithoutMetadataReturnsEmpty() throws Exception {
        resolver.dockerRoot = tempDir.resolve(
                "docker-no-metadata"
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

    private Path writeBlob(
            String digest,
            byte[] content
    )
            throws Exception {
        int colon = digest.indexOf(
                ':'
        );
        String algorithm = digest.substring(
                0,
                colon
        );
        String hex = digest.substring(
                colon + 1
        );
        Path blobPath = resolver.containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs"
        )
                .resolve(
                        algorithm
                )
                .resolve(
                        hex
                );
        Files.createDirectories(
                blobPath.getParent()
        );
        Files.write(
                blobPath,
                content
        );
        return blobPath;
    }

    private Path copyFixtureToDockerRoot() throws Exception {
        Path dockerRoot = tempDir.resolve(
                "docker"
        );
        Path dbDir = dockerRoot.resolve(
                "containerd/daemon/io.containerd.metadata.v1.bolt"
        );
        Files.createDirectories(
                dbDir
        );
        Path dbPath = dbDir.resolve(
                "meta.db"
        );
        Path fixture = Path.of(
                ContainerdFileResolverTest.class.getResource(
                        "/io/oci/docker/containerd/meta.db"
                ).toURI()
        );
        Files.copy(
                fixture,
                dbPath
        );
        return dockerRoot;
    }
}
