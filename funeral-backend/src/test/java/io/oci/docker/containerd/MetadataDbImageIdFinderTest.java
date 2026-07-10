package io.oci.docker.containerd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataDbImageIdFinderTest {

    @TempDir
    Path tempDir;

    private final MetadataDbImageIdFinder finder = new MetadataDbImageIdFinder();

    @Test
    void findImageIdFromImagesTableWithTarget() throws Exception {
        Path dockerRoot = tempDir.resolve(
                "docker"
        );
        Path dbPath = dockerRoot.resolve(
                "image/overlay2/metadata.db"
        );
        Files.createDirectories(
                dbPath.getParent()
        );
        createDb(
                dbPath,
                "images",
                "name",
                "target",
                "docker.io/library/alpine:3.20",
                "{\"digest\":\"sha256:abc123\"}"
        );

        Optional<String> result = finder.findImageId(
                dockerRoot,
                "alpine",
                "3.20"
        );

        assertTrue(
                result.isPresent()
        );
        assertEquals(
                "sha256:abc123",
                result.get()
        );
    }

    @Test
    void findImageIdFromKvTable() throws Exception {
        Path dockerRoot = tempDir.resolve(
                "docker"
        );
        Path dbPath = dockerRoot.resolve(
                "image/overlayfs/metadata.db"
        );
        Files.createDirectories(
                dbPath.getParent()
        );
        createDb(
                dbPath,
                "kv",
                "key",
                "value",
                "docker.io/library/alpine:3.20",
                "{\"target\":{\"digest\":\"sha256:def456\"}}"
        );

        Optional<String> result = finder.findImageId(
                dockerRoot,
                "alpine",
                "3.20"
        );

        assertTrue(
                result.isPresent()
        );
        assertEquals(
                "sha256:def456",
                result.get()
        );
    }

    @Test
    void missingDbReturnsEmpty() {
        Optional<String> result = finder.findImageId(
                tempDir.resolve(
                        "docker"
                ),
                "alpine",
                "3.20"
        );

        assertFalse(
                result.isPresent()
        );
    }

    @Test
    void unknownSchemaReturnsEmpty() throws Exception {
        Path dockerRoot = tempDir.resolve(
                "docker"
        );
        Path dbPath = dockerRoot.resolve(
                "image/overlay2/metadata.db"
        );
        Files.createDirectories(
                dbPath.getParent()
        );
        try (
                Connection conn = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbPath
                );
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE unknown (foo TEXT, bar TEXT)"
            );
            stmt.execute(
                    "INSERT INTO unknown VALUES ('x', 'y')"
            );
        }

        Optional<String> result = finder.findImageId(
                dockerRoot,
                "alpine",
                "3.20"
        );

        assertFalse(
                result.isPresent()
        );
    }

    private void createDb(
            Path dbPath,
            String tableName,
            String keyColumn,
            String valueColumn,
            String name,
            String value
    )
            throws Exception {
        try (
                Connection conn = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbPath
                );
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE " + tableName + " (" + keyColumn + " TEXT, " + valueColumn + " TEXT)"
            );
            stmt.execute(
                    "INSERT INTO " + tableName + " VALUES ('" + name + "', '" + value + "')"
            );
        }
    }
}
