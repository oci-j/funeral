package io.oci.docker;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerStorageModeDetectorTest {

    @TempDir
    Path tempDir;

    private final DockerStorageModeDetector detector = new DockerStorageModeDetector();

    @Test
    void containerdImageStoreDetected() throws Exception {
        Path containerdRoot = tempDir.resolve(
                "containerd"
        );
        Files.createDirectories(
                containerdRoot.resolve(
                        "io.containerd.content.v1.content/blobs"
                )
        );
        detector.containerdRoot = containerdRoot;
        detector.dockerRoot = tempDir.resolve(
                "docker"
        );

        assertTrue(
                detector.isContainerdImageStore()
        );
        assertFalse(
                detector.isOverlay2StorageDriver()
        );
    }

    @Test
    void overlay2StorageDriverDetected() throws Exception {
        Path dockerRoot = tempDir.resolve(
                "docker"
        );
        Files.createDirectories(
                dockerRoot.resolve(
                        "image/overlay2"
                )
        );
        detector.containerdRoot = tempDir.resolve(
                "containerd"
        );
        detector.dockerRoot = dockerRoot;

        assertFalse(
                detector.isContainerdImageStore()
        );
        assertTrue(
                detector.isOverlay2StorageDriver()
        );
    }
}
