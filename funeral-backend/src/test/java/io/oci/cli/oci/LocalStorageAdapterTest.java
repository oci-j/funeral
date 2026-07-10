package io.oci.cli.oci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalStorageAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new LocalStorageAdapter(
                tempDir.toString()
        );
    }

    @Test
    public void testStorageAvailable() {
        assertTrue(
                adapter.isAvailable()
        );
    }

    @Test
    public void testWriteAndReadManifest() {
        String repository = "library/busybox";
        String reference = "latest";
        byte[] manifest = "{\"schemaVersion\":2}".getBytes(
                StandardCharsets.UTF_8
        );
        String mediaType = "application/vnd.oci.image.manifest.v1+json";

        adapter.writeManifest(
                repository,
                reference,
                manifest,
                mediaType
        );
        byte[] read = adapter.readManifest(
                repository,
                reference
        );
        assertNotNull(
                read
        );
        assertArrayEquals(
                manifest,
                read
        );
        assertEquals(
                mediaType,
                adapter.readManifestMediaType(
                        repository,
                        reference
                )
        );
    }

    @Test
    public void testWriteAndReadBlob() throws IOException {
        String digest = "sha256:" + "0".repeat(
                64
        );
        byte[] content = "layer-content".getBytes(
                StandardCharsets.UTF_8
        );
        adapter.writeBlob(
                digest,
                content
        );
        assertTrue(
                Files.isRegularFile(
                        Paths.get(
                                tempDir.toString(),
                                "blobs",
                                "sha256",
                                "0".repeat(
                                        64
                                )
                        )
                )
        );
        assertArrayEquals(
                content,
                adapter.readBlob(
                        digest
                )
        );
    }
}
