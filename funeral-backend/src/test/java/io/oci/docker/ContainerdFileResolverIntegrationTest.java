package io.oci.docker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.oci.docker.containerd.MetadataDbImageIdFinder;
import io.oci.service.DigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ContainerdFileResolverIntegrationTest {

    private Path metadataDb;

    private Path containerdRoot;

    private ContainerdFileResolver resolver;

    @BeforeEach
    void setUp() {
        String metadataDbProperty = System.getProperty(
                "oci.docker-local.real-metadata-db"
        );
        String containerdRootProperty = System.getProperty(
                "oci.docker-local.real-containerd-root"
        );
        assumeTrue(
                metadataDbProperty != null && !metadataDbProperty.isBlank(),
                "real metadata.db path not set"
        );
        assumeTrue(
                containerdRootProperty != null && !containerdRootProperty.isBlank(),
                "real containerd root not set"
        );
        metadataDb = Paths.get(
                metadataDbProperty
        );
        containerdRoot = Paths.get(
                containerdRootProperty
        );
        assumeTrue(
                Files.isRegularFile(
                        metadataDb
                ),
                "metadata.db not found: " + metadataDb
        );
        assumeTrue(
                Files.isDirectory(
                        containerdRoot
                ),
                "containerd root not found: " + containerdRoot
        );

        resolver = new ContainerdFileResolver();
        resolver.directReadEnabled = true;
        resolver.containerdRoot = containerdRoot;
        resolver.dockerRoot = metadataDb.getParent().getParent().getParent();
        resolver.imageIdFinder = new MetadataDbImageIdFinder();
        resolver.digestService = new DigestService();
    }

    @Test
    void resolveAlpineByTag() {
        Optional<String> digest = resolver.imageIdFinder.findImageId(
                resolver.dockerRoot,
                "alpine",
                "3.20"
        );
        assertTrue(
                digest.isPresent(),
                "metadata.db should contain alpine:3.20"
        );

        Optional<ResolvedManifest> manifest = resolver.resolveManifest(
                "alpine",
                "3.20"
        );
        assertTrue(
                manifest.isPresent(),
                "should resolve manifest for alpine:3.20"
        );
        assertTrue(
                manifest.get().mediaType.startsWith(
                        "application/vnd."
                ),
                "unexpected media type: " + manifest.get().mediaType
        );
    }

    @Test
    void resolveDockerIoLibraryAlpineByTag() {
        Optional<ResolvedManifest> manifest = resolver.resolveManifest(
                "docker.io/library/alpine",
                "3.20"
        );
        assertTrue(
                manifest.isPresent(),
                "should resolve manifest for docker.io/library/alpine:3.20"
        );
        assertTrue(
                manifest.get().mediaType.startsWith(
                        "application/vnd."
                ),
                "unexpected media type: " + manifest.get().mediaType
        );
    }
}
