package io.oci.cli.oci;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ImagePackager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OCI_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

    private static final String OCI_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";

    private static final String DOCKER_INDEX_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";

    private static final String DOCKER_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    private ImagePackager() {
    }

    public interface BlobReader {

        byte[] read(
                String digest
        )
                throws IOException,
                InterruptedException;
    }

    public static void packageToOciLayout(
            byte[] manifestBytes,
            String mediaType,
            BlobReader reader,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        Files.createDirectories(
                layoutDir.resolve(
                        "blobs/sha256"
                )
        );

        ResolvedManifest resolved = resolveImageManifest(
                manifestBytes,
                mediaType,
                reader
        );
        String manifestDigest = DigestUtil.sha256(
                resolved.manifestBytes
        );
        writeBlob(
                layoutDir,
                manifestDigest,
                resolved.manifestBytes
        );
        writeIndex(
                layoutDir,
                manifestDigest,
                resolved.mediaType,
                resolved.manifestBytes.length
        );
        writeLayout(
                layoutDir
        );

        JsonNode manifest = MAPPER.readTree(
                resolved.manifestBytes
        );
        JsonNode config = manifest.get(
                "config"
        );
        if (config != null) {
            String configDigest = config.get(
                    "digest"
            ).asText();
            byte[] configBytes = reader.read(
                    configDigest
            );
            writeBlob(
                    layoutDir,
                    configDigest,
                    configBytes
            );
        }

        JsonNode layers = manifest.get(
                "layers"
        );
        if (layers != null && layers.isArray()) {
            for (JsonNode layer : layers) {
                String digest = layer.get(
                        "digest"
                ).asText();
                byte[] layerBytes = reader.read(
                        digest
                );
                writeBlob(
                        layoutDir,
                        digest,
                        layerBytes
                );
            }
        }
    }

    public static void packageToDockerTar(
            byte[] manifestBytes,
            String mediaType,
            BlobReader reader,
            ImageReference imageRef,
            Path tarFile
    )
            throws IOException,
            InterruptedException {
        Path tempDir = Files.createTempDirectory(
                "funeral-oci-layout"
        );
        try {
            packageToOciLayout(
                    manifestBytes,
                    mediaType,
                    reader,
                    tempDir
            );
            DockerTarConverter.ociLayoutToTar(
                    tempDir,
                    imageRef,
                    tarFile
            );
        }
        finally {
            deleteRecursively(
                    tempDir
            );
        }
    }

    private static ResolvedManifest resolveImageManifest(
            byte[] manifestBytes,
            String mediaType,
            BlobReader reader
    )
            throws IOException,
            InterruptedException {
        if (isIndexMediaType(
                mediaType
        )) {
            JsonNode index = MAPPER.readTree(
                    manifestBytes
            );
            JsonNode manifests = index.get(
                    "manifests"
            );
            if (manifests != null && manifests.isArray()) {
                Iterator<JsonNode> it = manifests.elements();
                while (it.hasNext()) {
                    JsonNode descriptor = it.next();
                    String descMediaType = descriptor.has(
                            "mediaType"
                    )
                            ? descriptor.get(
                                    "mediaType"
                            ).asText()
                            : OCI_MANIFEST_MEDIA_TYPE;
                    if (isImageManifestMediaType(
                            descMediaType
                    )) {
                        String digest = descriptor.get(
                                "digest"
                        ).asText();
                        byte[] childBytes = reader.read(
                                digest
                        );
                        return new ResolvedManifest(
                                childBytes,
                                descMediaType
                        );
                    }
                }
            }
            throw new IOException(
                    "No image manifest found in index"
            );
        }
        return new ResolvedManifest(
                manifestBytes,
                mediaType
        );
    }

    private static boolean isIndexMediaType(
            String mediaType
    ) {
        return OCI_INDEX_MEDIA_TYPE.equals(
                mediaType
        ) || DOCKER_INDEX_MEDIA_TYPE.equals(
                mediaType
        );
    }

    private static boolean isImageManifestMediaType(
            String mediaType
    ) {
        return OCI_MANIFEST_MEDIA_TYPE.equals(
                mediaType
        ) || DOCKER_MANIFEST_MEDIA_TYPE.equals(
                mediaType
        );
    }

    private static void writeBlob(
            Path layoutDir,
            String digest,
            byte[] bytes
    )
            throws IOException {
        String hex = digest.replace(
                "sha256:",
                ""
        );
        Path path = layoutDir.resolve(
                "blobs/sha256"
        )
                .resolve(
                        hex
                );
        Files.write(
                path,
                bytes
        );
    }

    private static void writeIndex(
            Path layoutDir,
            String manifestDigest,
            String mediaType,
            int size
    )
            throws IOException {
        ObjectNode index = MAPPER.createObjectNode();
        index.put(
                "schemaVersion",
                2
        );
        index.put(
                "mediaType",
                OCI_INDEX_MEDIA_TYPE
        );
        ArrayNode manifests = index.putArray(
                "manifests"
        );
        ObjectNode descriptor = manifests.addObject();
        descriptor.put(
                "mediaType",
                mediaType
        );
        descriptor.put(
                "digest",
                manifestDigest
        );
        descriptor.put(
                "size",
                size
        );
        Files.write(
                layoutDir.resolve(
                        "index.json"
                ),
                MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(
                                index
                        )
        );
    }

    private static void writeLayout(
            Path layoutDir
    )
            throws IOException {
        ObjectNode layout = MAPPER.createObjectNode();
        layout.put(
                "imageLayoutVersion",
                "1.0.0"
        );
        Files.write(
                layoutDir.resolve(
                        "oci-layout"
                ),
                MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(
                                layout
                        )
        );
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
        if (Files.isDirectory(
                path
        )) {
            for (java.nio.file.Path child : Files.list(
                    path
            ).toList()) {
                deleteRecursively(
                        child
                );
            }
        }
        Files.delete(
                path
        );
    }

    private static class ResolvedManifest {

        final byte[] manifestBytes;

        final String mediaType;

        ResolvedManifest(
                byte[] manifestBytes,
                String mediaType
        ) {
            this.manifestBytes = manifestBytes;
            this.mediaType = mediaType;
        }
    }
}
