package io.oci.cli.oci;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.oci.cli.client.FuneralClient;

public class OciPushPull {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String REF_NAME_ANNOTATION = "org.opencontainers.image.ref.name";

    private final FuneralClient client;

    public OciPushPull(
            FuneralClient client
    ) {
        this.client = client;
    }

    public void push(
            String repositoryName,
            String tag,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        Path indexFile = layoutDir.resolve(
                "index.json"
        );
        if (!Files.exists(
                indexFile
        )) {
            throw new RuntimeException(
                    "OCI index not found: " + indexFile
            );
        }
        JsonNode index = MAPPER.readTree(
                indexFile.toFile()
        );
        JsonNode descriptors = index.get(
                "manifests"
        );
        if (descriptors == null || !descriptors.isArray()) {
            throw new RuntimeException(
                    "Invalid OCI index: missing manifests"
            );
        }
        JsonNode selected = null;
        for (JsonNode d : descriptors) {
            JsonNode annotations = d.get(
                    "annotations"
            );
            if (annotations != null) {
                JsonNode ref = annotations.get(
                        REF_NAME_ANNOTATION
                );
                if (ref != null && tag.equals(
                        ref.asText()
                )) {
                    selected = d;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = descriptors.get(
                    0
            );
        }
        if (selected == null) {
            throw new RuntimeException(
                    "No manifest found in OCI layout"
            );
        }
        pushDescriptor(
                repositoryName,
                selected,
                layoutDir,
                tag
        );
        System.out.println(
                "Pushed " + repositoryName + ":" + tag
        );
    }

    private void pushDescriptor(
            String repositoryName,
            JsonNode descriptor,
            Path layoutDir,
            String reference
    )
            throws IOException,
            InterruptedException {
        String mediaType = descriptor.get(
                "mediaType"
        ).asText();
        String digest = descriptor.get(
                "digest"
        ).asText();
        byte[] bytes = Files.readAllBytes(
                blobPath(
                        layoutDir,
                        digest
                )
        );
        if (isIndex(
                mediaType
        )) {
            JsonNode index = MAPPER.readTree(
                    bytes
            );
            JsonNode manifests = index.get(
                    "manifests"
            );
            if (manifests != null) {
                for (JsonNode child : manifests) {
                    pushDescriptor(
                            repositoryName,
                            child,
                            layoutDir,
                            child.get(
                                    "digest"
                            ).asText()
                    );
                }
            }
            client.putManifest(
                    repositoryName,
                    reference,
                    bytes,
                    mediaType
            );
        }
        else if (isManifest(
                mediaType
        )) {
            pushManifestBlobs(
                    repositoryName,
                    bytes,
                    layoutDir
            );
            client.putManifest(
                    repositoryName,
                    reference,
                    bytes,
                    mediaType
            );
        }
        else {
            throw new RuntimeException(
                    "Unsupported descriptor mediaType: " + mediaType
            );
        }
    }

    private void pushManifestBlobs(
            String repositoryName,
            byte[] manifestBytes,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        JsonNode config = manifest.get(
                "config"
        );
        if (config != null) {
            uploadBlobIfNeeded(
                    repositoryName,
                    config,
                    layoutDir
            );
        }
        JsonNode layers = manifest.get(
                "layers"
        );
        if (layers != null) {
            for (JsonNode layer : layers) {
                uploadBlobIfNeeded(
                        repositoryName,
                        layer,
                        layoutDir
                );
            }
        }
    }

    private void uploadBlobIfNeeded(
            String repositoryName,
            JsonNode descriptor,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        String digest = descriptor.get(
                "digest"
        ).asText();
        if (!client.blobExists(
                repositoryName,
                digest
        )) {
            byte[] bytes = Files.readAllBytes(
                    blobPath(
                            layoutDir,
                            digest
                    )
            );
            client.uploadBlob(
                    repositoryName,
                    digest,
                    bytes
            );
        }
    }

    public void pull(
            String repositoryName,
            String tag,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        Files.createDirectories(
                layoutDir.resolve(
                        "blobs/sha256"
                )
        );
        byte[] manifestBytes = client.getManifest(
                repositoryName,
                tag
        );
        String manifestDigest = DigestUtil.sha256(
                manifestBytes
        );
        String mediaType = readMediaType(
                manifestBytes
        );
        Files.write(
                blobPath(
                        layoutDir,
                        manifestDigest
                ),
                manifestBytes
        );
        pullManifest(
                manifestBytes,
                repositoryName,
                layoutDir
        );
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
                manifestBytes.length
        );
        ObjectNode annotations = descriptor.putObject(
                "annotations"
        );
        annotations.put(
                REF_NAME_ANNOTATION,
                tag
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
        System.out.println(
                "Pulled " + repositoryName + ":" + tag + " to " + layoutDir
        );
    }

    private void pullManifest(
            byte[] manifestBytes,
            String repositoryName,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        String mediaType = readMediaType(
                manifestBytes
        );
        if (isIndex(
                mediaType
        )) {
            JsonNode manifests = manifest.get(
                    "manifests"
            );
            if (manifests != null) {
                for (JsonNode child : manifests) {
                    String digest = child.get(
                            "digest"
                    ).asText();
                    byte[] childBytes = client.getManifest(
                            repositoryName,
                            digest
                    );
                    Files.write(
                            blobPath(
                                    layoutDir,
                                    DigestUtil.sha256(
                                            childBytes
                                    )
                            ),
                            childBytes
                    );
                    pullManifest(
                            childBytes,
                            repositoryName,
                            layoutDir
                    );
                }
            }
        }
        else if (isManifest(
                mediaType
        )) {
            JsonNode config = manifest.get(
                    "config"
            );
            if (config != null) {
                downloadBlob(
                        repositoryName,
                        config,
                        layoutDir
                );
            }
            JsonNode layers = manifest.get(
                    "layers"
            );
            if (layers != null) {
                for (JsonNode layer : layers) {
                    downloadBlob(
                            repositoryName,
                            layer,
                            layoutDir
                    );
                }
            }
        }
    }

    private void downloadBlob(
            String repositoryName,
            JsonNode descriptor,
            Path layoutDir
    )
            throws IOException,
            InterruptedException {
        String digest = descriptor.get(
                "digest"
        ).asText();
        Path path = blobPath(
                layoutDir,
                digest
        );
        if (Files.exists(
                path
        )) {
            return;
        }
        byte[] bytes = client.getBlob(
                repositoryName,
                digest
        );
        Files.write(
                path,
                bytes
        );
    }

    private Path blobPath(
            Path layoutDir,
            String digest
    ) {
        String[] parts = digest.split(
                ":"
        );
        return layoutDir.resolve(
                "blobs"
        )
                .resolve(
                        parts[0]
                )
                .resolve(
                        parts[1]
                );
    }

    private String readMediaType(
            byte[] bytes
    )
            throws IOException {
        JsonNode node = MAPPER.readTree(
                bytes
        );
        JsonNode mediaType = node.get(
                "mediaType"
        );
        return mediaType != null ? mediaType.asText() : "application/vnd.oci.image.manifest.v1+json";
    }

    private boolean isIndex(
            String mediaType
    ) {
        return "application/vnd.oci.image.index.v1+json".equals(
                mediaType
        ) || "application/vnd.docker.distribution.manifest.list.v2+json".equals(
                mediaType
        );
    }

    private boolean isManifest(
            String mediaType
    ) {
        return "application/vnd.oci.image.manifest.v1+json".equals(
                mediaType
        ) || "application/vnd.docker.distribution.manifest.v2+json".equals(
                mediaType
        );
    }
}
