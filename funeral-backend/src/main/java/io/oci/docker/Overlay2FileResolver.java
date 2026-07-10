package io.oci.docker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.oci.docker.overlay2.TarSplitReassembler;
import io.oci.docker.util.DeletingOnCloseInputStream;
import io.oci.service.DigestService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class Overlay2FileResolver {

    private static final Logger log = LoggerFactory.getLogger(
            Overlay2FileResolver.class
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DOCKER_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    private static final String DOCKER_CONFIG_MEDIA_TYPE = "application/vnd.docker.container.image.v1+json";

    private static final String LAYER_MEDIA_TYPE = "application/vnd.docker.image.rootfs.diff.tar.gzip";

    @ConfigProperty(
            name = "oci.docker-local.docker-root",
            defaultValue = "/var/lib/docker"
    )
    Path dockerRoot;

    @ConfigProperty(
            name = "oci.docker-local.overlay2-direct-read",
            defaultValue = "true"
    )
    boolean directReadEnabled;

    @Inject
    DigestService digestService;

    LayerReassembler layerReassembler = TarSplitReassembler::reassemble;

    private final Map<String, LayerInfo> digestToLayer = new ConcurrentHashMap<>();

    public boolean isAvailable() {
        if (!directReadEnabled) {
            return false;
        }
        Path imageDbDir = dockerRoot.resolve(
                "image/overlay2/imagedb/content/sha256"
        );
        Path layerDbDir = dockerRoot.resolve(
                "image/overlay2/layerdb/sha256"
        );
        Path overlay2Dir = dockerRoot.resolve(
                "overlay2"
        );
        Path repositoriesJson = dockerRoot.resolve(
                "image/overlay2/repositories.json"
        );
        return Files.isDirectory(
                imageDbDir
        ) && Files.isDirectory(
                layerDbDir
        ) && Files.isDirectory(
                overlay2Dir
        ) && Files.isRegularFile(
                repositoriesJson
        );
    }

    public Optional<ResolvedManifest> resolveManifest(
            String repositoryName,
            String reference
    ) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String imageId = findImageId(
                    repositoryName,
                    reference
            );
            if (imageId == null) {
                return Optional.empty();
            }
            Path imageConfigFile = imageDbDir().resolve(
                    imageId
            );
            if (!Files.isRegularFile(
                    imageConfigFile
            )) {
                return Optional.empty();
            }
            byte[] imageConfig = Files.readAllBytes(
                    imageConfigFile
            );
            String configDigest = digestService.calculateDigest(
                    imageConfig
            );
            List<String> diffIds = parseDiffIds(
                    imageConfig
            );
            if (diffIds == null) {
                return Optional.empty();
            }
            List<LayerRef> layerRefs = new ArrayList<>();
            List<String> chainIds = computeChainIds(
                    diffIds
            );
            for (int i = 0; i < diffIds.size(); i++) {
                String chainId = chainIds.get(
                        i
                );
                LayerInfo layerInfo = resolveLayerInfo(
                        chainId
                );
                if (layerInfo == null) {
                    log.warn(
                            "Layer {} not found for {}:{}",
                            chainId,
                            repositoryName,
                            reference
                    );
                    return Optional.empty();
                }
                TarSplitReassembler.ReassembledLayer reassembled = layerReassembler.reassemble(
                        layerInfo.tarSplitFile,
                        layerInfo.diffDir
                );
                digestToLayer.put(
                        reassembled.digest,
                        layerInfo
                );
                Files.deleteIfExists(
                        reassembled.file
                );
                layerRefs.add(
                        new LayerRef(
                                reassembled.digest,
                                reassembled.size
                        )
                );
            }
            return Optional.of(
                    buildManifest(
                            imageConfig,
                            configDigest,
                            layerRefs
                    )
            );
        }
        catch (IOException e) {
            log.warn(
                    "Failed to resolve manifest for {}:{} via overlay2: {}",
                    repositoryName,
                    reference,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    public Optional<ResolvedBlob> resolveBlob(
            String digest,
            String repositoryName
    ) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            if (digest != null && digest.startsWith(
                    "sha256:"
            )) {
                String hex = digest.substring(
                        7
                );
                Path configFile = imageDbDir().resolve(
                        hex
                );
                if (Files.isRegularFile(
                        configFile
                )) {
                    InputStream is = Files.newInputStream(
                            configFile
                    );
                    long size = Files.size(
                            configFile
                    );
                    return Optional.of(
                            new ResolvedBlob(
                                    is,
                                    size
                            )
                    );
                }
            }
            LayerInfo layerInfo = digestToLayer.get(
                    digest
            );
            if (layerInfo == null) {
                return Optional.empty();
            }
            TarSplitReassembler.ReassembledLayer reassembled = layerReassembler.reassemble(
                    layerInfo.tarSplitFile,
                    layerInfo.diffDir
            );
            InputStream wrapped = new DeletingOnCloseInputStream(
                    Files.newInputStream(
                            reassembled.file
                    ),
                    reassembled.file
            );
            return Optional.of(
                    new ResolvedBlob(
                            wrapped,
                            reassembled.size
                    )
            );
        }
        catch (IOException e) {
            log.warn(
                    "Failed to resolve blob {} for {} via overlay2: {}",
                    digest,
                    repositoryName,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    private Path imageDbDir() {
        return dockerRoot.resolve(
                "image/overlay2/imagedb/content/sha256"
        );
    }

    private Path layerDbDir() {
        return dockerRoot.resolve(
                "image/overlay2/layerdb/sha256"
        );
    }

    private Path overlay2Dir() {
        return dockerRoot.resolve(
                "overlay2"
        );
    }

    private String findImageId(
            String repositoryName,
            String reference
    )
            throws IOException {
        if (reference == null) {
            return null;
        }
        if (reference.startsWith(
                "sha256:"
        )) {
            String hex = reference.substring(
                    7
            );
            Path configFile = imageDbDir().resolve(
                    hex
            );
            if (Files.isRegularFile(
                    configFile
            )) {
                return hex;
            }
            return null;
        }
        Path repositoriesJson = dockerRoot.resolve(
                "image/overlay2/repositories.json"
        );
        if (Files.isRegularFile(
                repositoriesJson
        )) {
            String fromRepos = findImageIdInRepositoriesJson(
                    repositoriesJson,
                    repositoryName,
                    reference
            );
            if (fromRepos != null) {
                return fromRepos;
            }
        }
        return null;
    }

    private String findImageIdInRepositoriesJson(
            Path repositoriesJson,
            String repositoryName,
            String reference
    )
            throws IOException {
        byte[] content = Files.readAllBytes(
                repositoriesJson
        );
        JsonNode root = MAPPER.readTree(
                content
        );
        JsonNode repositories = root.get(
                "Repositories"
        );
        if (repositories == null || !repositories.isObject()) {
            return null;
        }
        String stripped = stripRegistryPrefix(
                repositoryName
        );
        String tag = reference;
        for (Iterator<String> it = repositories.fieldNames(); it.hasNext();) {
            String repoName = it.next();
            if (repoName.equals(
                    repositoryName
            ) || repoName.equals(
                    stripped
            )) {
                JsonNode tags = repositories.get(
                        repoName
                );
                String fullTag = repoName + ":" + tag;
                if (tags.has(
                        fullTag
                )) {
                    return stripSha256Prefix(
                            tags.get(
                                    fullTag
                            ).asText()
                    );
                }
            }
        }
        return null;
    }

    private String stripRegistryPrefix(
            String repositoryName
    ) {
        if (repositoryName == null) {
            return null;
        }
        if (repositoryName.startsWith(
                "docker.io/library/"
        )) {
            return repositoryName.substring(
                    "docker.io/library/".length()
            );
        }
        if (repositoryName.startsWith(
                "docker.io/"
        )) {
            return repositoryName.substring(
                    "docker.io/".length()
            );
        }
        return repositoryName;
    }

    private String stripSha256Prefix(
            String value
    ) {
        if (value != null && value.startsWith(
                "sha256:"
        )) {
            return value.substring(
                    7
            );
        }
        return value;
    }

    private List<String> parseDiffIds(
            byte[] imageConfig
    )
            throws IOException {
        JsonNode root = MAPPER.readTree(
                imageConfig
        );
        JsonNode rootfs = root.get(
                "rootfs"
        );
        if (rootfs == null) {
            return null;
        }
        JsonNode diffIds = rootfs.get(
                "diff_ids"
        );
        if (diffIds == null || !diffIds.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode node : diffIds) {
            if (node.isTextual()) {
                result.add(
                        node.asText()
                );
            }
        }
        return result;
    }

    private List<String> computeChainIds(
            List<String> diffIds
    ) {
        List<String> chainIds = new ArrayList<>();
        String parent = null;
        for (String diffId : diffIds) {
            if (parent == null) {
                parent = diffId;
            }
            else {
                parent = sha256(
                        parent + " " + diffId
                );
            }
            chainIds.add(
                    parent
            );
        }
        return chainIds;
    }

    private String sha256(
            String content
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] hash = digest.digest(
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
            return "sha256:" + bytesToHex(
                    hash
            );
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "SHA-256 not available",
                    e
            );
        }
    }

    private LayerInfo resolveLayerInfo(
            String chainId
    )
            throws IOException {
        String hex = stripSha256Prefix(
                chainId
        );
        Path layerDb = layerDbDir().resolve(
                hex
        );
        if (!Files.isDirectory(
                layerDb
        )) {
            return null;
        }
        Path cacheIdFile = layerDb.resolve(
                "cache-id"
        );
        if (!Files.isRegularFile(
                cacheIdFile
        )) {
            return null;
        }
        String cacheId = Files.readString(
                cacheIdFile
        ).trim();
        Path diffDir = overlay2Dir().resolve(
                cacheId
        )
                .resolve(
                        "diff"
                );
        Path tarSplitFile = layerDb.resolve(
                "tar-split.json.gz"
        );
        if (!Files.isRegularFile(
                tarSplitFile
        )) {
            return null;
        }
        return new LayerInfo(
                tarSplitFile,
                diffDir
        );
    }

    private ResolvedManifest buildManifest(
            byte[] imageConfig,
            String configDigest,
            List<LayerRef> layers
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
                configDigest
        );
        config.put(
                "mediaType",
                DOCKER_CONFIG_MEDIA_TYPE
        );
        config.put(
                "size",
                imageConfig.length
        );

        ArrayNode layersNode = manifest.putArray(
                "layers"
        );
        for (LayerRef layer : layers) {
            ObjectNode layerNode = layersNode.addObject();
            layerNode.put(
                    "digest",
                    layer.digest
            );
            layerNode.put(
                    "mediaType",
                    LAYER_MEDIA_TYPE
            );
            layerNode.put(
                    "size",
                    layer.size
            );
        }

        byte[] bytes = MAPPER.writeValueAsBytes(
                manifest
        );
        String digest = digestService.calculateDigest(
                bytes
        );
        return new ResolvedManifest(
                bytes,
                DOCKER_MANIFEST_MEDIA_TYPE,
                digest
        );
    }

    private String bytesToHex(
            byte[] bytes
    ) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }
        return result.toString();
    }

    @FunctionalInterface
    interface LayerReassembler {

        TarSplitReassembler.ReassembledLayer reassemble(
                Path tarSplitFile,
                Path diffDir
        )
                throws IOException;
    }

    private static class LayerInfo {

        final Path tarSplitFile;

        final Path diffDir;

        LayerInfo(
                Path tarSplitFile,
                Path diffDir
        ) {
            this.tarSplitFile = tarSplitFile;
            this.diffDir = diffDir;
        }
    }

    private static class LayerRef {

        final String digest;

        final long size;

        LayerRef(
                String digest,
                long size
        ) {
            this.digest = digest;
            this.size = size;
        }
    }
}
