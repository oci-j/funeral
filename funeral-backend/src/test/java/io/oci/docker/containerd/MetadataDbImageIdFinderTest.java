package io.oci.docker.containerd;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void findImageIdByContainerdRoot() throws Exception {
        Path containerdRoot = copyFixture();

        Optional<String> result = finder.findImageId(
                null,
                containerdRoot,
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
    void findImageIdByDockerRoot() throws Exception {
        Path dockerRoot = copyFixtureToDockerRoot();

        Optional<String> result = finder.findImageId(
                dockerRoot,
                null,
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
    void findDockerIoLibraryImage() throws Exception {
        Path containerdRoot = copyFixture();

        Optional<String> result = finder.findImageId(
                null,
                containerdRoot,
                "docker.io/library/alpine",
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
    void missingDbReturnsEmpty() {
        Optional<String> result = finder.findImageId(
                tempDir.resolve(
                        "docker"
                ),
                null,
                "alpine",
                "3.20"
        );

        assertFalse(
                result.isPresent()
        );
    }

    @Test
    void unknownImageReturnsEmpty() throws Exception {
        Path containerdRoot = copyFixture();

        Optional<String> result = finder.findImageId(
                null,
                containerdRoot,
                "unknown",
                "latest"
        );

        assertFalse(
                result.isPresent()
        );
    }

    private Path copyFixture() throws Exception {
        Path containerdRoot = tempDir.resolve(
                "containerd"
        );
        Path dbDir = containerdRoot.resolve(
                "io.containerd.metadata.v1.bolt"
        );
        Files.createDirectories(
                dbDir
        );
        Path dbPath = dbDir.resolve(
                "meta.db"
        );
        Files.copy(
                fixtureResource(),
                dbPath
        );
        return containerdRoot;
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
        Files.copy(
                fixtureResource(),
                dbPath
        );
        return dockerRoot;
    }

    private Path fixtureResource() throws Exception {
        Path path = Path.of(
                MetadataDbImageIdFinderTest.class.getResource(
                        "/io/oci/docker/containerd/meta.db"
                ).toURI()
        );
        assertTrue(
                Files.isRegularFile(
                        path
                ),
                "fixture meta.db not found"
        );
        return path;
    }
}
