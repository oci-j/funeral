package io.oci.cli.oci;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.oci.docker.DockerSaveTarParser;
import io.oci.docker.ResolvedBlob;
import io.oci.docker.ResolvedManifest;
import io.oci.model.ImageReference;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public class DockerTarConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DOCKER_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    private static final String DOCKER_CONFIG_MEDIA_TYPE = "application/vnd.docker.container.image.v1+json";

    private static final String LAYER_GZIP_MEDIA_TYPE = "application/vnd.docker.image.rootfs.diff.tar.gzip";

    private DockerTarConverter() {
    }

    public static void tarToOciLayout(
            Path tarFile,
            ImageReference imageRef,
            Path layoutDir
    )
            throws IOException {
        Files.createDirectories(
                layoutDir.resolve(
                        "blobs/sha256"
                )
        );

        List<String> candidates = repoTagCandidates(
                imageRef
        );
        ResolvedManifest resolvedManifest = null;
        for (String candidate : candidates) {
            try {
                resolvedManifest = DockerSaveTarParser.parseManifest(
                        tarFile,
                        candidate
                );
                break;
            }
            catch (IOException e) {
                // try next candidate
            }
        }
        if (resolvedManifest == null) {
            throw new IOException(
                    "Could not find image " + imageRef + " in docker tar"
            );
        }

        byte[] manifestBytes = resolvedManifest.bytes;
        String manifestDigest = DigestUtil.sha256(
                manifestBytes
        );
        writeBlob(
                layoutDir,
                manifestDigest,
                manifestBytes
        );

        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        String configDigest = manifest.get(
                "config"
        )
                .get(
                        "digest"
                )
                .asText();
        byte[] configBytes = readBlobFromTar(
                tarFile,
                configDigest
        );
        writeBlob(
                layoutDir,
                configDigest,
                configBytes
        );

        JsonNode layers = manifest.get(
                "layers"
        );
        if (layers != null && layers.isArray()) {
            for (JsonNode layer : layers) {
                String digest = layer.get(
                        "digest"
                ).asText();
                byte[] layerBytes = readBlobFromTar(
                        tarFile,
                        digest
                );
                writeBlob(
                        layoutDir,
                        digest,
                        layerBytes
                );
            }
        }

        writeIndex(
                layoutDir,
                manifestDigest,
                resolvedManifest.mediaType != null ? resolvedManifest.mediaType : DOCKER_MANIFEST_MEDIA_TYPE,
                manifestBytes.length
        );
        writeLayout(
                layoutDir
        );
    }

    public static void ociLayoutToTar(
            Path layoutDir,
            ImageReference imageRef,
            Path tarFile
    )
            throws IOException {
        JsonNode index = MAPPER.readTree(
                layoutDir.resolve(
                        "index.json"
                ).toFile()
        );
        JsonNode descriptors = index.get(
                "manifests"
        );
        if (descriptors == null || !descriptors.isArray() || descriptors.isEmpty()) {
            throw new IOException(
                    "Invalid OCI index: no manifests"
            );
        }
        JsonNode descriptor = descriptors.get(
                0
        );
        String manifestDigest = descriptor.get(
                "digest"
        ).asText();
        byte[] manifestBytes = readLayoutBlob(
                layoutDir,
                manifestDigest
        );

        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        String configDigest = manifest.get(
                "config"
        )
                .get(
                        "digest"
                )
                .asText();
        byte[] configBytes = readLayoutBlob(
                layoutDir,
                configDigest
        );

        String repoTag = imageRef.toString();

        try (
                OutputStream fos = Files.newOutputStream(
                        tarFile
                );
                GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(
                        fos
                );
                TarArchiveOutputStream tos = new TarArchiveOutputStream(
                        gzip
                )) {
            tos.setLongFileMode(
                    TarArchiveOutputStream.LONGFILE_GNU
            );

            // manifest.json
            ObjectNode dockerManifest = MAPPER.createObjectNode();
            dockerManifest.put(
                    "Config",
                    configDigest.replace(
                            "sha256:",
                            ""
                    ) + ".json"
            );
            ArrayNode layersNode = dockerManifest.putArray(
                    "Layers"
            );
            ArrayNode repoTagsNode = MAPPER.createArrayNode();
            repoTagsNode.add(
                    repoTag
            );
            dockerManifest.set(
                    "RepoTags",
                    repoTagsNode
            );

            // config
            addTarEntry(
                    tos,
                    configDigest.replace(
                            "sha256:",
                            ""
                    ) + ".json",
                    configBytes
            );

            // layers
            JsonNode layers = manifest.get(
                    "layers"
            );
            if (layers != null && layers.isArray()) {
                for (JsonNode layer : layers) {
                    String digest = layer.get(
                            "digest"
                    ).asText();
                    byte[] layerBytes = readLayoutBlob(
                            layoutDir,
                            digest
                    );
                    String layerHex = digest.replace(
                            "sha256:",
                            ""
                    );
                    String layerPath = layerHex + "/layer.tar";
                    addTarEntry(
                            tos,
                            layerPath,
                            layerBytes
                    );
                    layersNode.add(
                            layerPath
                    );
                }
            }

            ArrayNode manifestList = MAPPER.createArrayNode();
            manifestList.add(
                    dockerManifest
            );
            byte[] dockerManifestBytes = MAPPER.writeValueAsBytes(
                    manifestList
            );
            addTarEntry(
                    tos,
                    "manifest.json",
                    dockerManifestBytes
            );
        }
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
                "application/vnd.oci.image.index.v1+json"
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

    private static byte[] readLayoutBlob(
            Path layoutDir,
            String digest
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
        return Files.readAllBytes(
                path
        );
    }

    private static byte[] readBlobFromTar(
            Path tarFile,
            String digest
    )
            throws IOException {
        Optional<ResolvedBlob> blob = DockerSaveTarParser.openBlob(
                tarFile,
                digest
        );
        if (blob.isEmpty()) {
            throw new IOException(
                    "Blob " + digest + " not found in docker tar"
            );
        }
        try (InputStream is = blob.get().stream) {
            return is.readAllBytes();
        }
    }

    private static void addTarEntry(
            TarArchiveOutputStream tos,
            String name,
            byte[] bytes
    )
            throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(
                name
        );
        entry.setSize(
                bytes.length
        );
        tos.putArchiveEntry(
                entry
        );
        tos.write(
                bytes
        );
        tos.closeArchiveEntry();
    }

    private static List<String> repoTagCandidates(
            ImageReference imageRef
    ) {
        List<String> candidates = new ArrayList<>();
        candidates.add(
                imageRef.toString()
        );
        candidates.add(
                imageRef.repository + ":" + imageRef.tag
        );
        if (ImageReference.DEFAULT_REGISTRY.equals(
                imageRef.registry
        ) && imageRef.repository.startsWith(
                "library/"
        )) {
            candidates.add(
                    imageRef.repository.substring(
                            "library/".length()
                    ) + ":" + imageRef.tag
            );
        }
        return candidates;
    }
}
