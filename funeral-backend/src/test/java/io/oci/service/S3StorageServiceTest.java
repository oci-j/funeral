package io.oci.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(
    S3StorageServiceTest.S3TestProfile.class
)
public class S3StorageServiceTest {

    private static Path testStoragePath;

    @Inject
    @Named(
        "s3-storage"
    )
    AbstractStorageService storageService;

    @Inject
    S3StorageService s3StorageService;

    @AfterAll
    public static void cleanup() throws Exception {
        if (testStoragePath != null) {
            deleteRecursively(
                    testStoragePath
            );
        }
    }

    @Test
    public void testStoreBlobWithValidDigest() throws Exception {
        String content = "hello s3 blob";
        byte[] bytes = content.getBytes(
                StandardCharsets.UTF_8
        );
        String digest = sha256(
                bytes
        );

        String returnedDigest = storageService.storeBlob(
                new ByteArrayInputStream(
                        bytes
                ),
                digest
        );

        assertEquals(
                digest,
                returnedDigest
        );
        assertTrue(
                storageService.blobExists(
                        digest
                )
        );
        assertEquals(
                bytes.length,
                storageService.getBlobSize(
                        digest
                )
        );
        try (
                InputStream is = storageService.getBlobStream(
                        digest
                )) {
            assertNotNull(
                    is
            );
            assertArrayEquals(
                    bytes,
                    is.readAllBytes()
            );
        }
    }

    @Test
    public void testStoreBlobWithInvalidDigest() throws Exception {
        String content = "hello s3 blob";
        byte[] bytes = content.getBytes(
                StandardCharsets.UTF_8
        );
        String badDigest = "sha256:" + "0".repeat(
                64
        );

        IOException ex = assertThrows(
                IOException.class,
                () -> storageService.storeBlob(
                        new ByteArrayInputStream(
                                bytes
                        ),
                        badDigest
                )
        );
        assertEquals(
                "Digest mismatch",
                ex.getCause().getMessage()
        );
        assertFalse(
                storageService.blobExists(
                        badDigest
                )
        );
    }

    @Test
    public void testStoreBlobWithoutDigest() throws Exception {
        String content = "compute my digest";
        byte[] bytes = content.getBytes(
                StandardCharsets.UTF_8
        );
        String expectedDigest = sha256(
                bytes
        );

        String returnedDigest = storageService.storeBlob(
                new ByteArrayInputStream(
                        bytes
                ),
                null
        );

        assertEquals(
                expectedDigest,
                returnedDigest
        );
        assertTrue(
                storageService.blobExists(
                        expectedDigest
                )
        );
    }

    @Test
    public void testTempChunkMergeAndCleanup() throws Exception {
        String uploadUuid = "merge-test-" + System.nanoTime();
        byte[] chunk0 = "hello ".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] chunk1 = "world".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] merged = "hello world".getBytes(
                StandardCharsets.UTF_8
        );
        String digest = sha256(
                merged
        );

        long written0 = s3StorageService.storeTempChunk(
                new ByteArrayInputStream(
                        chunk0
                ),
                uploadUuid,
                0
        );
        long written1 = s3StorageService.storeTempChunk(
                new ByteArrayInputStream(
                        chunk1
                ),
                uploadUuid,
                1
        );

        assertEquals(
                chunk0.length,
                written0
        );
        assertEquals(
                chunk1.length,
                written1
        );

        AbstractStorageService.CalculateTempChunkResult before = s3StorageService.calculateTempChunks(
                uploadUuid
        );
        assertEquals(
                2,
                before.index()
        );
        assertEquals(
                merged.length,
                before.bytesWritten()
        );

        s3StorageService.mergeTempChunks(
                uploadUuid,
                1,
                digest
        );

        assertTrue(
                storageService.blobExists(
                        digest
                )
        );
        assertEquals(
                merged.length,
                storageService.getBlobSize(
                        digest
                )
        );
        try (
                InputStream is = storageService.getBlobStream(
                        digest
                )) {
            assertArrayEquals(
                    merged,
                    is.readAllBytes()
            );
        }

        AbstractStorageService.CalculateTempChunkResult after = s3StorageService.calculateTempChunks(
                uploadUuid
        );
        assertEquals(
                0,
                after.index()
        );
        assertEquals(
                0,
                after.bytesWritten()
        );
    }

    @Test
    public void testTempChunkDuplicateRejected() throws Exception {
        String uploadUuid = "duplicate-test-" + System.nanoTime();
        byte[] chunk = "chunk".getBytes(
                StandardCharsets.UTF_8
        );

        s3StorageService.storeTempChunk(
                new ByteArrayInputStream(
                        chunk
                ),
                uploadUuid,
                0
        );

        assertThrows(
                io.oci.exception.WithResponseException.class,
                () -> s3StorageService.storeTempChunk(
                        new ByteArrayInputStream(
                                chunk
                        ),
                        uploadUuid,
                        0
                )
        );
    }

    @Test
    public void testDeleteBlob() throws Exception {
        String content = "to be deleted";
        byte[] bytes = content.getBytes(
                StandardCharsets.UTF_8
        );
        String digest = sha256(
                bytes
        );
        storageService.storeBlob(
                new ByteArrayInputStream(
                        bytes
                ),
                digest
        );
        assertTrue(
                storageService.blobExists(
                        digest
                )
        );

        storageService.deleteBlob(
                digest
        );

        assertFalse(
                storageService.blobExists(
                        digest
                )
        );
        assertNull(
                storageService.getBlobStream(
                        digest
                )
        );
    }

    @Test
    public void testMissingBlob() throws Exception {
        String digest = "sha256:" + "0".repeat(
                64
        );

        assertFalse(
                storageService.blobExists(
                        digest
                )
        );
        assertNull(
                storageService.getBlobStream(
                        digest
                )
        );
        assertThrows(
                IOException.class,
                () -> storageService.getBlobSize(
                        digest
                )
        );
    }

    private static String sha256(
            byte[] content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return "sha256:" + HexFormat.of()
                .formatHex(
                        md.digest(
                                content
                        )
                );
    }

    public static class S3TestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                testStoragePath = Files.createTempDirectory(
                        "s3-storage-service-test"
                );
                return Map.of(
                        "oci.storage.bucket",
                        "test-bucket",
                        "oci.storage.tempBucket",
                        "test-temp-bucket",
                        "oci.storage.local-storage-path",
                        testStoragePath.toString(),
                        "oci.storage.no-minio",
                        "true",
                        "quarkus.devservices.enabled",
                        "false"
                );
            }
            catch (IOException e) {
                throw new RuntimeException(
                        e
                );
            }
        }
    }

    private static void deleteRecursively(
            Path path
    )
            throws IOException {
        if (!Files.exists(
                path
        )) {
            return;
        }
        try (
                var stream = Files.walk(
                        path
                )) {
            stream.sorted(
                    (
                            a,
                            b
                    ) -> -a.compareTo(
                            b
                    )
            )
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(
                                            p
                                    );
                                }
                                catch (IOException e) {
                                    throw new RuntimeException(
                                            e
                                    );
                                }
                            }
                    );
        }
    }
}
