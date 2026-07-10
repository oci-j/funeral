package io.oci.cli.oci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
