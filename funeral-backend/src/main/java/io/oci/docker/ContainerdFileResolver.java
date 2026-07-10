package io.oci.docker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.docker.containerd.MetadataDbImageIdFinder;
import io.oci.service.DigestService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ContainerdFileResolver {

    private static final Logger log = LoggerFactory.getLogger(
            ContainerdFileResolver.class
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(
            name = "oci.docker-local.containerd-root",
            defaultValue = "/var/lib/docker/containerd/daemon"
    )
    Path containerdRoot;

    @ConfigProperty(
            name = "oci.docker-local.fallback-to-direct-read",
            defaultValue = "true"
    )
    boolean directReadEnabled;

    @Inject
    DigestService digestService;

    @ConfigProperty(
            name = "oci.docker-local.docker-root",
            defaultValue = "/var/lib/docker"
    )
    Path dockerRoot;

    @Inject
    MetadataDbImageIdFinder imageIdFinder;

    public boolean isAvailable() {
        if (!directReadEnabled) {
            return false;
        }
        Path contentDir = containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs"
        );
        return Files.isDirectory(
                contentDir
        );
    }

    public Optional<ResolvedBlob> resolveBlob(
            String digest
    ) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        Path file = resolveDigestPath(
                digest
        );
        if (file == null || !Files.isRegularFile(
                file
        )) {
            return Optional.empty();
        }
        try {
            long size = Files.size(
                    file
            );
            InputStream fis = Files.newInputStream(
                    file
            );
            return Optional.of(
                    new ResolvedBlob(
                            fis,
                            size
                    )
            );
        }
        catch (IOException e) {
            log.warn(
                    "Failed to read containerd blob {}: {}",
                    digest,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    public Optional<ResolvedManifest> resolveManifest(
            String repositoryName,
            String reference
    ) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        if (reference == null) {
            return Optional.empty();
        }
        String digest = reference;
        if (!reference.startsWith(
                "sha256:"
        )) {
            Optional<String> found = imageIdFinder.findImageId(
                    dockerRoot,
                    containerdRoot,
                    repositoryName,
                    reference
            );
            if (found.isEmpty()) {
                return Optional.empty();
            }
            digest = found.get();
        }
        Path file = resolveDigestPath(
                digest
        );
        if (file == null || !Files.isRegularFile(
                file
        )) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(
                    file
            );
            String mediaType = detectMediaType(
                    bytes
            );
            String calculatedDigest = digestService.calculateDigest(
                    bytes
            );
            return Optional.of(
                    new ResolvedManifest(
                            bytes,
                            mediaType,
                            calculatedDigest
                    )
            );
        }
        catch (IOException e) {
            log.warn(
                    "Failed to read containerd manifest {} for {}: {}",
                    digest,
                    repositoryName,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    private Path resolveDigestPath(
            String digest
    ) {
        if (digest == null) {
            return null;
        }
        int colon = digest.indexOf(
                ':'
        );
        if (colon < 0) {
            return null;
        }
        String algorithm = digest.substring(
                0,
                colon
        );
        String hex = digest.substring(
                colon + 1
        );
        return containerdRoot.resolve(
                "io.containerd.content.v1.content/blobs"
        )
                .resolve(
                        algorithm
                )
                .resolve(
                        hex
                );
    }

    private String detectMediaType(
            byte[] bytes
    ) {
        try {
            JsonNode root = MAPPER.readTree(
                    bytes
            );
            JsonNode mediaTypeNode = root.get(
                    "mediaType"
            );
            if (mediaTypeNode != null && mediaTypeNode.isTextual()) {
                return mediaTypeNode.asText();
            }
            if (root.has(
                    "manifests"
            )) {
                return "application/vnd.oci.image.index.v1+json";
            }
            if (root.has(
                    "layers"
            ) || root.has(
                    "config"
            )) {
                return "application/vnd.oci.image.manifest.v1+json";
            }
        }
        catch (Exception e) {
            // ignore and fall back to default
        }
        return "application/vnd.oci.image.manifest.v1+json";
    }
}
