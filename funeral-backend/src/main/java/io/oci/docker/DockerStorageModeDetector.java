package io.oci.docker;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DockerStorageModeDetector {

    @ConfigProperty(
            name = "oci.docker-local.containerd-root",
            defaultValue = "/var/lib/docker/containerd/daemon"
    )
    Path containerdRoot;

    @ConfigProperty(
            name = "oci.docker-local.docker-root",
            defaultValue = "/var/lib/docker"
    )
    Path dockerRoot;

    public boolean isContainerdImageStore() {
        Path dockerContainerd = dockerRoot.resolve(
                "containerd/daemon/io.containerd.content.v1.content/blobs"
        );
        if (Files.isDirectory(
                dockerContainerd
        )) {
            return true;
        }
        Path standaloneContainerd = containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs"
        );
        return Files.isDirectory(
                standaloneContainerd
        );
    }

    public boolean isOverlay2StorageDriver() {
        Path overlay2Dir = dockerRoot.resolve(
                "image/overlay2"
        );
        return Files.isDirectory(
                overlay2Dir
        );
    }
}
