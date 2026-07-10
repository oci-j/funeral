package io.oci.docker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.docker.overlay2.TarSplitReassembler;
import io.oci.service.DigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Overlay2FileResolverTest {

    @TempDir
    Path tempDir;

    private Overlay2FileResolver resolver;

    private DigestService digestService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        digestService = new DigestService();
        objectMapper = new ObjectMapper();
        resolver = new Overlay2FileResolver();
        resolver.dockerRoot = tempDir.resolve(
                "docker"
        );
        resolver.directReadEnabled = true;
        resolver.digestService = digestService;
    }

    @Test
    void notAvailableWhenDirectoryMissing() {
        assertFalse(
                resolver.isAvailable()
        );
    }

    @Test
    void resolveManifestAndBlobByTag() throws Exception {
        String diffId = "sha256:aabbccdd";
        String imageConfigJson = "{\"architecture\":\"amd64\",\"config\":{},\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[\""
                + diffId + "\"]}}";
        byte[] imageConfig = imageConfigJson.getBytes(
                StandardCharsets.UTF_8
        );
        String imageId = digestService.calculateDigest(
                imageConfig
        )
                .substring(
                        7
                );

        createOverlay2Store(
                imageId,
                imageConfig,
                diffId
        );

        byte[] layerContent = "overlay2 layer content".getBytes(
                StandardCharsets.UTF_8
        );
        String layerDigest = digestService.calculateDigest(
                layerContent
        );
        resolver.layerReassembler = (
                tarSplitFile,
                diffDir
        ) -> {
            Path tempFile = Files.createTempFile(
                    "mock-layer-",
                    ".tar.gz"
            );
            Files.write(
                    tempFile,
                    layerContent
            );
            return new TarSplitReassembler.ReassembledLayer(
                    tempFile,
                    layerContent.length,
                    layerDigest
            );
        };

        Optional<ResolvedManifest> manifest = resolver.resolveManifest(
                "myrepo",
                "mytag"
        );
        assertTrue(
                manifest.isPresent()
        );
        JsonNode manifestNode = objectMapper.readTree(
                manifest.get().bytes
        );
        assertEquals(
                2,
                manifestNode.get(
                        "schemaVersion"
                ).asInt()
        );
        assertEquals(
                "application/vnd.docker.distribution.manifest.v2+json",
                manifestNode.get(
                        "mediaType"
                ).asText()
        );
        JsonNode layers = manifestNode.get(
                "layers"
        );
        assertEquals(
                1,
                layers.size()
        );
        assertEquals(
                layerDigest,
                layers.get(
                        0
                )
                        .get(
                                "digest"
                        )
                        .asText()
        );
        assertEquals(
                layerContent.length,
                layers.get(
                        0
                )
                        .get(
                                "size"
                        )
                        .asLong()
        );

        String configDigest = digestService.calculateDigest(
                imageConfig
        );
        Optional<ResolvedBlob> configBlob = resolver.resolveBlob(
                configDigest,
                "myrepo"
        );
        assertTrue(
                configBlob.isPresent()
        );
        try (java.io.InputStream is = configBlob.get().stream) {
            assertArrayEquals(
                    imageConfig,
                    is.readAllBytes()
            );
        }

        Optional<ResolvedBlob> layerBlob = resolver.resolveBlob(
                layerDigest,
                "myrepo"
        );
        assertTrue(
                layerBlob.isPresent()
        );
        try (java.io.InputStream is = layerBlob.get().stream) {
            assertArrayEquals(
                    layerContent,
                    is.readAllBytes()
            );
        }
    }

    @Test
    void resolveManifestByImageId() throws Exception {
        String diffId = "sha256:11223344";
        String imageConfigJson = "{\"architecture\":\"amd64\",\"config\":{},\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[\""
                + diffId + "\"]}}";
        byte[] imageConfig = imageConfigJson.getBytes(
                StandardCharsets.UTF_8
        );
        String imageId = digestService.calculateDigest(
                imageConfig
        )
                .substring(
                        7
                );

        createOverlay2Store(
                imageId,
                imageConfig,
                diffId
        );

        byte[] layerContent = "layer".getBytes(
                StandardCharsets.UTF_8
        );
        String layerDigest = digestService.calculateDigest(
                layerContent
        );
        resolver.layerReassembler = (
                tarSplitFile,
                diffDir
        ) -> {
            Path tempFile = Files.createTempFile(
                    "mock-layer-",
                    ".tar.gz"
            );
            Files.write(
                    tempFile,
                    layerContent
            );
            return new TarSplitReassembler.ReassembledLayer(
                    tempFile,
                    layerContent.length,
                    layerDigest
            );
        };

        Optional<ResolvedManifest> manifest = resolver.resolveManifest(
                "myrepo",
                "sha256:" + imageId
        );
        assertTrue(
                manifest.isPresent()
        );
        JsonNode manifestNode = objectMapper.readTree(
                manifest.get().bytes
        );
        assertEquals(
                1,
                manifestNode.get(
                        "layers"
                ).size()
        );
    }

    @Test
    void resolveMissingBlob() throws Exception {
        String imageConfigJson = "{\"architecture\":\"amd64\",\"config\":{},\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[]}}";
        byte[] imageConfig = imageConfigJson.getBytes(
                StandardCharsets.UTF_8
        );
        String imageId = digestService.calculateDigest(
                imageConfig
        )
                .substring(
                        7
                );
        createOverlay2Store(
                imageId,
                imageConfig,
                null
        );

        Optional<ResolvedBlob> blob = resolver.resolveBlob(
                "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                "myrepo"
        );
        assertFalse(
                blob.isPresent()
        );
    }

    private void createOverlay2Store(
            String imageId,
            byte[] imageConfig,
            String diffId
    )
            throws Exception {
        Path dockerRoot = resolver.dockerRoot;
        Path imageDbDir = dockerRoot.resolve(
                "image/overlay2/imagedb/content/sha256"
        );
        Path layerDbDir = dockerRoot.resolve(
                "image/overlay2/layerdb/sha256"
        );
        Path overlay2Dir = dockerRoot.resolve(
                "overlay2"
        );
        Files.createDirectories(
                imageDbDir
        );
        Files.createDirectories(
                layerDbDir
        );
        Files.createDirectories(
                overlay2Dir
        );

        Files.write(
                imageDbDir.resolve(
                        imageId
                ),
                imageConfig
        );

        if (diffId != null) {
            String diffHex = diffId.startsWith(
                    "sha256:"
            )
                    ? diffId.substring(
                            7
                    )
                    : diffId;
            Path layerDb = layerDbDir.resolve(
                    diffHex
            );
            Files.createDirectories(
                    layerDb
            );
            String cacheId = "cache" + diffHex;
            Files.writeString(
                    layerDb.resolve(
                            "cache-id"
                    ),
                    cacheId
            );
            Files.createFile(
                    layerDb.resolve(
                            "tar-split.json.gz"
                    )
            );
            Files.createDirectories(
                    overlay2Dir.resolve(
                            cacheId
                    )
                            .resolve(
                                    "diff"
                            )
            );
        }

        String repositoriesJson = "{\"Repositories\":{\"myrepo\":{\"myrepo:mytag\":\"sha256:" + imageId + "\"}}}";
        Files.writeString(
                dockerRoot.resolve(
                        "image/overlay2/repositories.json"
                ),
                repositoriesJson
        );
    }
}
