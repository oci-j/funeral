package io.oci.cli.oci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.model.ImageReference;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImagePackagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    public void testPackageToOciLayout() throws Exception {
        ImageReference ref = ImageReference.parse(
                "docker.io/library/busybox:latest"
        );
        byte[] config = "config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "layer".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                config
        );
        String layerDigest = DigestUtil.sha256(
                layer
        );
        String manifest = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + config.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layer.length + "}]}";
        byte[] manifestBytes = manifest.getBytes(
                StandardCharsets.UTF_8
        );
        ImagePackager.BlobReader reader = digest -> {
            if (digest.equals(
                    configDigest
            )) {
                return config;
            }
            if (digest.equals(
                    layerDigest
            )) {
                return layer;
            }
            throw new IOException(
                    "unknown blob " + digest
            );
        };

        Path layoutDir = tempDir.resolve(
                "oci-layout"
        );
        ImagePackager.packageToOciLayout(
                manifestBytes,
                "application/vnd.oci.image.manifest.v1+json",
                reader,
                layoutDir
        );

        assertTrue(
                Files.isRegularFile(
                        layoutDir.resolve(
                                "index.json"
                        )
                )
        );
        assertTrue(
                Files.isRegularFile(
                        layoutDir.resolve(
                                "oci-layout"
                        )
                )
        );
        JsonNode index = MAPPER.readTree(
                layoutDir.resolve(
                        "index.json"
                ).toFile()
        );
        assertEquals(
                1,
                index.get(
                        "manifests"
                ).size()
        );
        assertTrue(
                Files.isRegularFile(
                        layoutDir.resolve(
                                "blobs/sha256/" + configDigest.replace(
                                        "sha256:",
                                        ""
                                )
                        )
                )
        );
        assertTrue(
                Files.isRegularFile(
                        layoutDir.resolve(
                                "blobs/sha256/" + layerDigest.replace(
                                        "sha256:",
                                        ""
                                )
                        )
                )
        );
    }

    @Test
    public void testPackageToDockerTar() throws Exception {
        ImageReference ref = ImageReference.parse(
                "docker.io/library/busybox:latest"
        );
        byte[] config = "config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "layer".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                config
        );
        String layerDigest = DigestUtil.sha256(
                layer
        );
        String manifest = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + config.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layer.length + "}]}";
        byte[] manifestBytes = manifest.getBytes(
                StandardCharsets.UTF_8
        );
        ImagePackager.BlobReader reader = digest -> {
            if (digest.equals(
                    configDigest
            )) {
                return config;
            }
            if (digest.equals(
                    layerDigest
            )) {
                return layer;
            }
            throw new IOException(
                    "unknown blob " + digest
            );
        };

        Path tarFile = tempDir.resolve(
                "image.tar.gz"
        );
        ImagePackager.packageToDockerTar(
                manifestBytes,
                "application/vnd.oci.image.manifest.v1+json",
                reader,
                ref,
                tarFile
        );

        assertTrue(
                Files.isRegularFile(
                        tarFile
                )
        );
        try (
                TarArchiveInputStream tis = new TarArchiveInputStream(
                        new GzipCompressorInputStream(
                                Files.newInputStream(
                                        tarFile
                                )
                        )
                )) {
            TarArchiveEntry entry = tis.getNextEntry();
            boolean foundManifest = false;
            while (entry != null) {
                if ("manifest.json".equals(
                        entry.getName()
                )) {
                    foundManifest = true;
                    break;
                }
                entry = tis.getNextEntry();
            }
            assertTrue(
                    foundManifest
            );
        }
    }

    @Test
    public void testResolveImageManifestWithPlatform() throws Exception {
        byte[] amd64Config = "amd64-config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] arm64Config = "arm64-config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "layer".getBytes(
                StandardCharsets.UTF_8
        );
        String amd64ConfigDigest = DigestUtil.sha256(
                amd64Config
        );
        String arm64ConfigDigest = DigestUtil.sha256(
                arm64Config
        );
        String layerDigest = DigestUtil.sha256(
                layer
        );

        String amd64Manifest = buildManifest(
                amd64ConfigDigest,
                layerDigest,
                amd64Config.length,
                layer.length
        );
        String arm64Manifest = buildManifest(
                arm64ConfigDigest,
                layerDigest,
                arm64Config.length,
                layer.length
        );
        String amd64Digest = DigestUtil.sha256(
                amd64Manifest.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        String arm64Digest = DigestUtil.sha256(
                arm64Manifest.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String index = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.index.v1+json\",\"manifests\":[{\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"digest\":\""
                + amd64Digest + "\",\"size\":" + amd64Manifest.length()
                + ",\"platform\":{\"os\":\"linux\",\"architecture\":\"amd64\"}},{\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"digest\":\""
                + arm64Digest + "\",\"size\":" + arm64Manifest.length()
                + ",\"platform\":{\"os\":\"linux\",\"architecture\":\"arm64\"}}]}";

        ImagePackager.BlobReader reader = digest -> {
            if (digest.equals(
                    amd64Digest
            )) {
                return amd64Manifest.getBytes(
                        StandardCharsets.UTF_8
                );
            }
            if (digest.equals(
                    arm64Digest
            )) {
                return arm64Manifest.getBytes(
                        StandardCharsets.UTF_8
                );
            }
            throw new IOException(
                    "unknown blob " + digest
            );
        };

        ImagePackager.ResolvedManifest resolved = ImagePackager.resolveImageManifest(
                index.getBytes(
                        StandardCharsets.UTF_8
                ),
                "application/vnd.oci.image.index.v1+json",
                reader,
                "linux/arm64"
        );
        JsonNode manifest = MAPPER.readTree(
                resolved.manifestBytes
        );
        assertEquals(
                arm64ConfigDigest,
                manifest.get(
                        "config"
                )
                        .get(
                                "digest"
                        )
                        .asText()
        );
    }

    private String buildManifest(
            String configDigest,
            String layerDigest,
            int configSize,
            int layerSize
    ) {
        return "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + configSize
                + "},\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layerSize + "}]}";
    }
}
