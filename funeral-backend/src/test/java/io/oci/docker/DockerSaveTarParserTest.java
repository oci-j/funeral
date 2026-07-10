package io.oci.docker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSaveTarParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseManifestAndOpenBlob() throws Exception {
        Path tarFile = createDockerSaveTar(
                tempDir
        );

        ResolvedManifest manifest = DockerSaveTarParser.parseManifest(
                tarFile,
                "myrepo:latest"
        );
        assertEquals(
                "application/vnd.docker.distribution.manifest.v2+json",
                manifest.mediaType
        );
        String manifestText = new String(
                manifest.bytes,
                StandardCharsets.UTF_8
        );
        assertTrue(
                manifestText.contains(
                        "sha256:cfg123"
                ),
                "Manifest should contain config digest"
        );
        assertTrue(
                manifestText.contains(
                        "sha256:layer123"
                ),
                "Manifest should contain layer digest"
        );
        assertEquals(
                2,
                manifestText.split(
                        "sha256:"
                ).length - 1
        );

        Optional<ResolvedBlob> blob = DockerSaveTarParser.openBlob(
                tarFile,
                "sha256:layer123"
        );
        assertTrue(
                blob.isPresent()
        );
        try (InputStream is = blob.get().stream) {
            byte[] gzipTar = is.readAllBytes();
            // The returned blob should be the gzip-compressed layer tar.
            try (
                    TarArchiveInputStream layerTar = new TarArchiveInputStream(
                            new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                                    new ByteArrayInputStream(
                                            gzipTar
                                    )
                            )
                    )) {
                org.apache.commons.compress.archivers.ArchiveEntry entry = layerTar.getNextEntry();
                assertEquals(
                        "dummy.txt",
                        entry.getName()
                );
                byte[] data = layerTar.readAllBytes();
                assertEquals(
                        "layer content",
                        new String(
                                data,
                                StandardCharsets.UTF_8
                        )
                );
            }
        }
    }

    private Path createDockerSaveTar(
            Path dir
    )
            throws Exception {
        Path tarFile = dir.resolve(
                "image.tar"
        );
        String configJson = "{\"architecture\":\"amd64\",\"config\":{}}";
        String configDigest = "cfg123";
        byte[] configBytes = configJson.getBytes(
                StandardCharsets.UTF_8
        );

        byte[] layerGzipTar;
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(
                        baos
                );
                TarArchiveOutputStream layerTar = new TarArchiveOutputStream(
                        gzip
                )) {
            TarArchiveEntry entry = new TarArchiveEntry(
                    "dummy.txt"
            );
            byte[] data = "layer content".getBytes(
                    StandardCharsets.UTF_8
            );
            entry.setSize(
                    data.length
            );
            layerTar.putArchiveEntry(
                    entry
            );
            layerTar.write(
                    data
            );
            layerTar.closeArchiveEntry();
            layerTar.finish();
            gzip.finish();
            layerGzipTar = baos.toByteArray();
        }

        try (
                TarArchiveOutputStream outer = new TarArchiveOutputStream(
                        Files.newOutputStream(
                                tarFile
                        )
                )) {
            String manifestJson = "[{\"Config\":\"" + configDigest
                    + ".json\",\"RepoTags\":[\"myrepo:latest\"],\"Layers\":[\"layer123/layer.tar.gz\"]}]";
            byte[] manifestBytes = manifestJson.getBytes(
                    StandardCharsets.UTF_8
            );
            TarArchiveEntry manifestEntry = new TarArchiveEntry(
                    "manifest.json"
            );
            manifestEntry.setSize(
                    manifestBytes.length
            );
            outer.putArchiveEntry(
                    manifestEntry
            );
            outer.write(
                    manifestBytes
            );
            outer.closeArchiveEntry();

            TarArchiveEntry configEntry = new TarArchiveEntry(
                    configDigest + ".json"
            );
            configEntry.setSize(
                    configBytes.length
            );
            outer.putArchiveEntry(
                    configEntry
            );
            outer.write(
                    configBytes
            );
            outer.closeArchiveEntry();

            TarArchiveEntry layerEntry = new TarArchiveEntry(
                    "layer123/layer.tar.gz"
            );
            layerEntry.setSize(
                    layerGzipTar.length
            );
            outer.putArchiveEntry(
                    layerEntry
            );
            outer.write(
                    layerGzipTar
            );
            outer.closeArchiveEntry();

            outer.finish();
        }
        return tarFile;
    }
}
