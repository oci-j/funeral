package io.oci.docker;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerSaveTarParser {

    private static final Logger log = LoggerFactory.getLogger(
            DockerSaveTarParser.class
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DOCKER_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    private static final String DOCKER_CONFIG_MEDIA_TYPE = "application/vnd.docker.container.image.v1+json";

    private DockerSaveTarParser() {
    }

    public static ResolvedManifest parseManifest(
            Path tarFile,
            String repoTag
    )
            throws IOException {
        try (
                InputStream fis = Files.newInputStream(
                        tarFile
                );
                InputStream decompressed = wrapDecompressor(
                        fis
                );
                TarArchiveInputStream tis = new TarArchiveInputStream(
                        decompressed
                )) {
            ArchiveEntry entry;
            ImageManifestEntry target = null;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (target == null && (name.equals(
                        "manifest.json"
                ) || name.equals(
                        "./manifest.json"
                ))) {
                    byte[] content = readEntry(
                            tis,
                            entry
                    );
                    target = findManifestEntry(
                            content,
                            repoTag
                    );
                    if (target == null) {
                        throw new IOException(
                                "RepoTag " + repoTag + " not found in manifest.json"
                        );
                    }
                    continue;
                }
                if (target != null) {
                    String base = extractDigest(
                            name
                    );
                    if (base.equals(
                            target.configDigest
                    )) {
                        target.configSize = entry.getSize();
                    }
                    for (LayerEntry layer : target.layers) {
                        if (base.equals(
                                layer.digest
                        )) {
                            layer.size = entry.getSize();
                        }
                    }
                    if (target.isComplete()) {
                        break;
                    }
                }
            }
            if (target == null) {
                throw new IOException(
                        "manifest.json not found in docker save tar"
                );
            }
            return buildManifest(
                    target
            );
        }
    }

    public static Optional<ResolvedBlob> openBlob(
            Path tarFile,
            String digest
    )
            throws IOException {
        String hash = digest.startsWith(
                "sha256:"
        )
                ? digest.substring(
                        7
                )
                : digest;
        InputStream fis = Files.newInputStream(
                tarFile
        );
        try {
            InputStream decompressed = wrapDecompressor(
                    fis
            );
            TarArchiveInputStream tis = new TarArchiveInputStream(
                    decompressed
            );
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String base = extractDigest(
                        name
                );
                if (hash.equals(
                        base
                )) {
                    BoundedInputStream bounded = new BoundedInputStream(
                            tis,
                            entry.getSize()
                    );
                    return Optional.of(
                            new ResolvedBlob(
                                    bounded,
                                    entry.getSize()
                            )
                    );
                }
            }
            tis.close();
            return Optional.empty();
        }
        catch (IOException e) {
            fis.close();
            throw e;
        }
    }

    private static InputStream wrapDecompressor(
            InputStream input
    )
            throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(
                input,
                8192
        );
        buffered.mark(
                4
        );
        byte[] signature = new byte[4];
        int read = buffered.read(
                signature
        );
        buffered.reset();
        if (read >= 2 && (signature[0] & 0xFF) == 0x1f && (signature[1] & 0xFF) == 0x8b) {
            return new GzipCompressorInputStream(
                    buffered
            );
        }
        if (read >= 4 && signature[0] == (byte) 0x28 && signature[1] == (byte) 0xB5 && signature[2] == (byte) 0x2F
                && signature[3] == (byte) 0xFD) {
            return new ZstdCompressorInputStream(
                    buffered
            );
        }
        return buffered;
    }

    private static ImageManifestEntry findManifestEntry(
            byte[] manifestJson,
            String repoTag
    )
            throws IOException {
        JsonNode root = MAPPER.readTree(
                manifestJson
        );
        if (!root.isArray()) {
            return null;
        }
        for (JsonNode node : root) {
            JsonNode repoTagsNode = node.get(
                    "RepoTags"
            );
            if (repoTagsNode != null && repoTagsNode.isArray()) {
                for (JsonNode tagNode : repoTagsNode) {
                    if (repoTag.equals(
                            tagNode.asText()
                    )) {
                        return parseManifestNode(
                                node
                        );
                    }
                }
            }
        }
        return null;
    }

    private static ImageManifestEntry parseManifestNode(
            JsonNode node
    ) {
        ImageManifestEntry entry = new ImageManifestEntry();

        JsonNode configNode = node.get(
                "Config"
        );
        if (configNode != null) {
            entry.configDigest = extractDigest(
                    configNode.asText()
            );
        }

        JsonNode layersNode = node.get(
                "Layers"
        );
        if (layersNode != null && layersNode.isArray()) {
            for (JsonNode layerNode : layersNode) {
                String layerFile = layerNode.asText();
                LayerEntry layer = new LayerEntry();
                layer.digest = extractDigest(
                        layerFile
                );
                layer.mediaType = layerMediaType(
                        layerFile
                );
                entry.layers.add(
                        layer
                );
            }
        }

        return entry;
    }

    private static String layerMediaType(
            String filename
    ) {
        if (filename.endsWith(
                ".tar.gz"
        ) || filename.endsWith(
                ".tgz"
        )) {
            return "application/vnd.docker.image.rootfs.diff.tar.gzip";
        }
        if (filename.endsWith(
                ".tar.zst"
        )) {
            return "application/vnd.docker.image.rootfs.diff.tar.zstd";
        }
        return "application/vnd.docker.image.rootfs.diff.tar";
    }

    private static ResolvedManifest buildManifest(
            ImageManifestEntry entry
    )
            throws IOException {
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put(
                "schemaVersion",
                2
        );
        manifest.put(
                "mediaType",
                DOCKER_MANIFEST_MEDIA_TYPE
        );

        ObjectNode config = manifest.putObject(
                "config"
        );
        config.put(
                "digest",
                "sha256:" + entry.configDigest
        );
        config.put(
                "mediaType",
                DOCKER_CONFIG_MEDIA_TYPE
        );
        config.put(
                "size",
                entry.configSize
        );

        ArrayNode layers = manifest.putArray(
                "layers"
        );
        for (LayerEntry layer : entry.layers) {
            ObjectNode layerNode = layers.addObject();
            layerNode.put(
                    "digest",
                    "sha256:" + layer.digest
            );
            layerNode.put(
                    "mediaType",
                    layer.mediaType
            );
            layerNode.put(
                    "size",
                    layer.size
            );
        }

        byte[] bytes = MAPPER.writeValueAsBytes(
                manifest
        );
        return new ResolvedManifest(
                bytes,
                DOCKER_MANIFEST_MEDIA_TYPE,
                null
        );
    }

    private static String extractDigest(
            String filename
    ) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        String result = filename;
        if (result.contains(
                "sha256/"
        )) {
            int idx = result.indexOf(
                    "sha256/"
            ) + "sha256/".length();
            String after = result.substring(
                    idx
            );
            int slash = after.indexOf(
                    '/'
            );
            if (slash >= 0) {
                after = after.substring(
                        0,
                        slash
                );
            }
            return stripExtensionsFrom(
                    after
            );
        }
        if (result.contains(
                "/"
        )) {
            int lastSlash = result.lastIndexOf(
                    '/'
            );
            String basename = result.substring(
                    lastSlash + 1
            );
            String parent = result.substring(
                    0,
                    lastSlash
            );
            String strippedBasename = stripExtensionsFrom(
                    basename
            );
            if (strippedBasename.isEmpty() || strippedBasename.equals(
                    "layer"
            ) || strippedBasename.equals(
                    "data"
            )) {
                return stripExtensionsFrom(
                        parent
                );
            }
            return strippedBasename;
        }
        return stripExtensionsFrom(
                result
        );
    }

    private static String stripExtensionsFrom(
            String filename
    ) {
        if (filename == null) {
            return null;
        }
        return filename.replace(
                ".tar.gz",
                ""
        )
                .replace(
                        ".tar.zst",
                        ""
                )
                .replace(
                        ".tgz",
                        ""
                )
                .replace(
                        ".tar",
                        ""
                )
                .replace(
                        ".json",
                        ""
                )
                .replace(
                        ".layer",
                        ""
                );
    }

    private static byte[] readEntry(
            TarArchiveInputStream tarInput,
            ArchiveEntry entry
    )
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytes = 0;
        long entrySize = entry.getSize();
        while ((bytesRead = tarInput.read(
                buffer
        )) != -1) {
            baos.write(
                    buffer,
                    0,
                    bytesRead
            );
            totalBytes += bytesRead;
            if (entrySize > 0 && totalBytes >= entrySize) {
                break;
            }
        }
        return baos.toByteArray();
    }

    private static class ImageManifestEntry {

        String configDigest;

        long configSize = -1;

        final List<LayerEntry> layers = new ArrayList<>();

        boolean isComplete() {
            if (configDigest == null || configSize < 0) {
                return false;
            }
            for (LayerEntry layer : layers) {
                if (layer.size < 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class LayerEntry {

        String digest;

        String mediaType;

        long size = -1;
    }

    private static class BoundedInputStream extends InputStream {

        private final InputStream in;

        private long remaining;

        BoundedInputStream(
                InputStream in,
                long size
        ) {
            this.in = in;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(
                byte[] b,
                int off,
                int len
        )
                throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(
                    len,
                    remaining
            );
            int n = in.read(
                    b,
                    off,
                    toRead
            );
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
