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
        containerdResolver.digestService = digestService;
        resolver.containerdFileResolver = containerdResolver;
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
