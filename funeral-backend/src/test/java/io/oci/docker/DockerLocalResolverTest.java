package io.oci.docker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.oci.service.DigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerLocalResolverTest {

    @TempDir
    Path tempDir;

    private DockerLocalResolver resolver;

    private DigestService digestService;

    @BeforeEach
    void setUp() {
        digestService = new DigestService();
        resolver = new DockerLocalResolver();
        DockerApiImageResolver apiResolver = new DockerApiImageResolver();
        apiResolver.enabled = false;
        resolver.apiImageResolver = apiResolver;

        ContainerdFileResolver containerdResolver = new ContainerdFileResolver();
        containerdResolver.directReadEnabled = true;
        containerdResolver.containerdRoot = tempDir.resolve(
                "containerd"
        );
        containerdResolver.dockerRoot = tempDir.resolve(
                "docker"
        );
        containerdResolver.imageIdFinder = new io.oci.docker.containerd.MetadataDbImageIdFinder();
        containerdResolver.digestService = digestService;
        resolver.containerdFileResolver = containerdResolver;

        Overlay2FileResolver overlay2Resolver = new Overlay2FileResolver();
        overlay2Resolver.dockerRoot = tempDir.resolve(
                "docker"
        );
        overlay2Resolver.directReadEnabled = true;
        overlay2Resolver.digestService = digestService;
        resolver.overlay2FileResolver = overlay2Resolver;
    }

    @Test
    void resolveManifestAndBlobFromContainerd() throws Exception {
        byte[] blobContent = "containerd blob".getBytes(
                StandardCharsets.UTF_8
        );
        String blobDigest = digestService.calculateDigest(
                blobContent
        );

        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"digest\":\"sha256:abc\"},\"layers\":[{\"digest\":\""
                + blobDigest + "\",\"size\":" + blobContent.length + "}]}";
        byte[] manifestBytes = manifestJson.getBytes(
                StandardCharsets.UTF_8
        );
        String manifestDigest = digestService.calculateDigest(
                manifestBytes
        );

        writeBlob(
                manifestDigest,
                manifestBytes
        );
        writeBlob(
                blobDigest,
                blobContent
        );

        Optional<ResolvedManifest> manifest = resolver.resolveManifest(
                "myrepo",
                manifestDigest
        );
        assertTrue(
                manifest.isPresent()
        );
        assertEquals(
                "application/vnd.oci.image.manifest.v1+json",
                manifest.get().mediaType
        );

        Optional<ResolvedBlob> blob = resolver.resolveBlob(
                blobDigest,
                "myrepo"
        );
        assertTrue(
                blob.isPresent()
        );
        assertEquals(
                blobContent.length,
                blob.get().size
        );
        try (var is = blob.get().stream) {
            assertArrayEquals(
                    blobContent,
                    is.readAllBytes()
            );
        }
    }

    @Test
    void resolveManifestByDigestUsesCache() throws Exception {
        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"digest\":\"sha256:abc\"},\"layers\":[]}";
        byte[] manifestBytes = manifestJson.getBytes(
                StandardCharsets.UTF_8
        );
        String manifestDigest = digestService.calculateDigest(
                manifestBytes
        );

        CountingApiResolver apiResolver = new CountingApiResolver();
        apiResolver.manifest = new ResolvedManifest(
                manifestBytes,
                "application/vnd.oci.image.manifest.v1+json",
                manifestDigest
        );

        resolver.apiImageResolver = apiResolver;
        resolver.containerdFileResolver.directReadEnabled = false;
        resolver.overlay2FileResolver.directReadEnabled = false;

        Optional<ResolvedManifest> first = resolver.resolveManifest(
                "myrepo",
                manifestDigest
        );
        assertTrue(
                first.isPresent()
        );
        assertEquals(
                1,
                apiResolver.calls
        );

        Optional<ResolvedManifest> second = resolver.resolveManifest(
                "myrepo",
                manifestDigest
        );
        assertTrue(
                second.isPresent()
        );
        assertEquals(
                1,
                apiResolver.calls
        );
        assertSame(
                first.get(),
                second.get()
        );
    }

    private static class CountingApiResolver extends DockerApiImageResolver {

        int calls;

        ResolvedManifest manifest;

        @Override
        public Optional<ResolvedManifest> resolveManifest(
                String repositoryName,
                String reference
        ) {
            calls++;
            return Optional.of(
                    manifest
            );
        }
    }

    private void writeBlob(
            String digest,
            byte[] content
    )
            throws Exception {
        Path blobPath = tempDir.resolve(
                "containerd/io.containerd.content.v1.content/blobs/sha256/" + digest.substring(
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
    }
}
