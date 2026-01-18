package io.oci.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
import io.oci.service.ManifestStorage;
import io.oci.service.RepositoryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource for mirroring/pulling images from external OCI registries. This allows users to pull images from external
 * registries without using docker CLI.
 */
@Path(
    "/funeral_addition/mirror"
)
@ApplicationScoped
public class MirrorResource {

    private static final Logger log = LoggerFactory.getLogger(
            MirrorResource.class
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    @Named(
        "manifestStorage"
    )
    ManifestStorage manifestStorage;

    @Inject
    @Named(
        "blobStorage"
    )
    BlobStorage blobStorage;

    @Inject
    @Named(
        "repositoryStorage"
    )
    RepositoryStorage repositoryStorage;

    @Inject
    @Named(
        "storage"
    )
    AbstractStorageService storageService;

    // Cache for bearer tokens
    private final Map<String, String> tokenCache = new HashMap<>();

    /**
     * Mirror/Pull image from external registry
     *
     * @param sourceImage Full source image with tag (e.g., docker.io/library/nginx:latest)
     * @param targetRepository Optional target repository name (defaults to source repo name)
     * @param targetTag Optional target tag (defaults to source tag)
     * @param username Optional username for authentication
     * @param password Optional password for authentication
     * @param protocol Optional protocol (http or https, defaults to https)
     * @param insecure Allow insecure HTTPS connections
     * @return Mirror result
     */
    @POST
    @Path(
        "/pull"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    @Consumes(
        MediaType.APPLICATION_FORM_URLENCODED
    )
    public Response mirrorImage(
            @FormParam(
                "sourceImage"
            )
            String sourceImage,
            @FormParam(
                "targetRepository"
            )
            String targetRepository,
            @FormParam(
                "targetTag"
            )
            String targetTag,
            @FormParam(
                "username"
            )
            String username,
            @FormParam(
                "password"
            )
            String password,
            @FormParam(
                "protocol"
            )
            @DefaultValue(
                "https"
            )
            String protocol,
            @FormParam(
                "insecure"
            )
            boolean insecure
    ) {
        log.info(
                "Mirror request for image: {}",
                sourceImage
        );

        if (sourceImage == null || sourceImage.trim().isEmpty()) {
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Source image is required",
                    null
            );
        }

        try {
            // Parse source image
            ImageRef sourceRef = parseImageReference(
                    sourceImage
            );
            String finalTargetRepo = targetRepository != null ? targetRepository : sourceRef.repository;
            String finalTargetTag = targetTag != null ? targetTag : sourceRef.tag;

            log.info(
                    "Mirroring from {} to {}:{}",
                    sourceImage,
                    finalTargetRepo,
                    finalTargetTag
            );

            // Pull manifest from source registry
            ManifestContent manifest = pullManifest(
                    sourceRef,
                    username,
                    password,
                    protocol,
                    insecure
            );

            // Pull and store all blobs (config and layers)
            log.info(
                    "Pulling {} blobs from source registry",
                    manifest.layerDigests.size() + 1
            );
            pullAndStoreBlobs(
                    sourceRef,
                    manifest,
                    username,
                    password,
                    protocol,
                    insecure
            );

            // Store manifest in our registry
            storeManifest(
                    finalTargetRepo,
                    finalTargetTag,
                    manifest
            );

            // Create repository if doesn't exist
            createRepository(
                    finalTargetRepo
            );

            MirrorResult result = new MirrorResult();
            result.success = true;
            result.sourceImage = sourceImage;
            result.targetRepository = finalTargetRepo;
            result.targetTag = finalTargetTag;
            result.manifestDigest = manifest.digest;
            result.blobsCount = manifest.layerDigests.size() + 1; // +1 for config

            return Response.ok(
                    result
            ).build();

        }
        catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains(
                    "HTTP connect timed out"
            )) {
                log.error(
                        "Network timeout while mirroring image: {}. This may be due to network restrictions or proxy settings.",
                        sourceImage,
                        e
                );
                return createErrorResponse(
                        Response.Status.BAD_GATEWAY,
                        "NETWORK_TIMEOUT",
                        "Failed to connect to the external registry. Please check your network settings or try with a different registry.",
                        sourceImage
                );
            }
            else {
                log.error(
                        "Failed to mirror image: {}",
                        sourceImage,
                        e
                );
                return createErrorResponse(
                        Response.Status.INTERNAL_SERVER_ERROR,
                        "MIRROR_FAILED",
                        "Failed to mirror image: " + e.getMessage(),
                        sourceImage
                );
            }
        }
    }

    /**
     * Parse image reference into components
     */
    private ImageRef parseImageReference(
            String image
    ) {
        ImageRef ref = new ImageRef();

        if (image.startsWith(
                "http://"
        ) || image.startsWith(
                "https://"
        )) {
            int slashIndex = image.indexOf(
                    '/',
                    image.indexOf(
                            "//"
                    ) + 2
            );
            if (slashIndex > 0) {
                image = image.substring(
                        slashIndex + 1
                );
            }
        }

        String[] parts = image.split(
                "/"
        );
        String lastPart = parts[parts.length - 1];

        int colonIndex = lastPart.indexOf(
                ':'
        );
        if (colonIndex > 0) {
            ref.tag = lastPart.substring(
                    colonIndex + 1
            );
            ref.repository = lastPart.substring(
                    0,
                    colonIndex
            );
        }
        else {
            ref.tag = "latest";
            ref.repository = lastPart;
        }

        StringBuilder repoPath = new StringBuilder();
        if (parts.length == 1) {
            ref.registry = "docker.io";
            ref.namespace = "library";
            repoPath.append(
                    ref.repository
            );
        }
        else if (parts.length == 2) {
            String firstPart = parts[0];
            if (firstPart.contains(
                    "."
            ) || firstPart.contains(
                    ":"
            )) {
                ref.registry = firstPart;
                ref.namespace = "";
                repoPath.append(
                        ref.repository
                );
            }
            else {
                ref.registry = "docker.io";
                ref.namespace = firstPart;
                repoPath.insert(
                        0,
                        ref.namespace + "/"
                );
                repoPath.append(
                        ref.repository
                );
            }
        }
        else {
            ref.registry = parts[0];
            ref.namespace = parts[1];
            for (int i = 1; i < parts.length - 1; i++) {
                if (i > 1)
                    repoPath.append(
                            "/"
                    );
                repoPath.append(
                        parts[i]
                );
            }
            if (!ref.namespace.isEmpty()) {
                repoPath.append(
                        "/"
                );
            }
            repoPath.append(
                    ref.repository
            );
        }

        ref.fullRepositoryPath = repoPath.toString();
        return ref;
    }

    /**
     * Pull manifest from source registry with authentication support
     */
    private ManifestContent pullManifest(
            ImageRef ref,
            String username,
            String password,
            String protocol,
            boolean insecure
    )
            throws IOException {
        String manifestUrl = buildManifestUrl(
                ref,
                protocol
        );
        log.info(
                "Pulling manifest from: {}",
                manifestUrl
        );

        // First attempt without token
        HttpResponse<String> response = makeManifestRequest(
                ref,
                manifestUrl,
                username,
                password
        );

        if (response.statusCode() == 200) {
            // Check if this is a manifest list or OCI index
            String contentType = response.headers()
                    .firstValue(
                            "Content-Type"
                    )
                    .orElse(
                            ""
                    )
                    .toLowerCase();

            log.info(
                    "Manifest Content-Type: {}",
                    contentType
            );

            boolean isManifestList = contentType.contains(
                    "manifest.list"
            ) || contentType.contains(
                    "image.index"
            );

            if (isManifestList) {
                log.info(
                        "Received manifest list/OCI index, looking up platform-specific manifest"
                );
                return parseAndResolveManifestList(
                        ref,
                        username,
                        password,
                        protocol,
                        insecure,
                        response.body()
                );
            }

            return parseManifest(
                    response.body()
            );
        }
        else if (response.statusCode() == 401) {
            // Try to authenticate and get token
            String wwwAuth = response.headers()
                    .firstValue(
                            "WWW-Authenticate"
                    )
                    .orElse(
                            null
                    );
            log.info(
                    "WWW-Authenticate: {}",
                    wwwAuth
            );

            if (wwwAuth != null && wwwAuth.startsWith(
                    "Bearer "
            )) {
                String token = getBearerToken(
                        wwwAuth,
                        username,
                        password,
                        ref,
                        manifestUrl
                );
                if (token != null) {
                    // Store token in cache
                    String cacheKey = ref.registry + ":" + ref.fullRepositoryPath;
                    tokenCache.put(
                            cacheKey,
                            token
                    );

                    // Retry with token
                    HttpResponse<String> retryResponse = makeManifestRequestWithToken(
                            manifestUrl,
                            token
                    );
                    if (retryResponse.statusCode() == 200) {
                        return parseManifest(
                                retryResponse.body()
                        );
                    }
                }
            }
            throw new IOException(
                    "Authentication failed for registry: " + ref.registry
            );
        }
        else if (response.statusCode() == 404) {
            throw new IOException(
                    "Image not found: " + ref.fullRepositoryPath + ":" + ref.tag
            );
        }
        else {
            throw new IOException(
                    "Failed to pull manifest. Status: " + response.statusCode()
            );
        }
    }

    /**
     * Make manifest request without token
     */
    private HttpResponse<String> makeManifestRequest(
            ImageRef ref,
            String manifestUrl,
            String username,
            String password
    )
            throws IOException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    manifestUrl
                            )
                    )
                    .header(
                            "Accept",
                            "application/vnd.docker.distribution.manifest.v2+json,"
                                    + "application/vnd.docker.distribution.manifest.list.v2+json,"
                                    + "application/vnd.oci.image.manifest.v1+json,"
                                    + "application/vnd.oci.image.index.v1+json"
                    )
                    .timeout(
                            Duration.ofMinutes(
                                    5
                            )
                    );

            // For Docker Hub, try to get anonymous token
            if (ref.registry.equals(
                    "docker.io"
            )) {
                String token = getDockerHubAnonymousToken(
                        ref.fullRepositoryPath
                );
                if (token != null) {
                    requestBuilder.header(
                            "Authorization",
                            "Bearer " + token
                    );
                }
            }
            // For other registries, add basic auth if credentials provided
            else if (username != null && password != null) {
                String auth = Base64.getEncoder()
                        .encodeToString(
                                (username + ":" + password).getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );
                requestBuilder.header(
                        "Authorization",
                        "Basic " + auth
                );
            }

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .build();

            return client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Request interrupted",
                    e
            );
        }
    }

    /**
     * Make manifest request with bearer token
     */
    private HttpResponse<String> makeManifestRequestWithToken(
            String manifestUrl,
            String token
    )
            throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    manifestUrl
                            )
                    )
                    .header(
                            "Accept",
                            "application/vnd.docker.distribution.manifest.v2+json,"
                                    + "application/vnd.docker.distribution.manifest.list.v2+json,"
                                    + "application/vnd.oci.image.manifest.v1+json,"
                                    + "application/vnd.oci.image.index.v1+json"
                    )
                    .header(
                            "Authorization",
                            "Bearer " + token
                    )
                    .timeout(
                            Duration.ofMinutes(
                                    5
                            )
                    )
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .build();

            return client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Request interrupted",
                    e
            );
        }
    }

    /**
     * Parse manifest JSON into ManifestContent
     */
    private ManifestContent parseManifest(
            String manifestJson
    )
            throws IOException {
        ManifestContent content = new ManifestContent();
        content.json = manifestJson;

        JsonNode root = objectMapper.readTree(
                manifestJson
        );

        // Debug log the manifest structure
        log.info(
                "Parsed manifest mediaType: {}, has config: {}, has layers: {}",
                root.path(
                        "mediaType"
                )
                        .asText(
                                "unknown"
                        ),
                root.has(
                        "config"
                ),
                root.has(
                        "layers"
                )
        );

        // Debug: print full manifest
        log.info(
                "Full manifest content: {}",
                manifestJson
        );

        JsonNode configNode = root.path(
                "config"
        );
        if (!configNode.isMissingNode()) {
            content.configDigest = configNode.path(
                    "digest"
            ).asText();
            content.configSize = configNode.path(
                    "size"
            ).asLong();
            log.info(
                    "Found config blob: {} (size: {})",
                    content.configDigest,
                    content.configSize
            );
        }

        JsonNode layersNode = root.path(
                "layers"
        );
        if (layersNode.isArray()) {
            log.info(
                    "Found {} layers in manifest",
                    layersNode.size()
            );
            for (JsonNode layerNode : layersNode) {
                String digest = layerNode.path(
                        "digest"
                ).asText();
                long size = layerNode.path(
                        "size"
                ).asLong();
                content.layerDigests.add(
                        digest
                );
                content.layerSizes.put(
                        digest,
                        size
                );
            }
        }

        content.digest = "sha256:" + calculateSha256(
                manifestJson
        );

        log.info(
                "Successfully pulled manifest with {} layers",
                content.layerDigests.size()
        );

        return content;
    }

    /**
     * Parse manifest list/OCI index and resolve to platform-specific manifest
     */
    private ManifestContent parseAndResolveManifestList(
            ImageRef ref,
            String username,
            String password,
            String protocol,
            boolean insecure,
            String manifestListJson
    )
            throws IOException {
        JsonNode root = objectMapper.readTree(
                manifestListJson
        );

        JsonNode manifestsNode = root.path(
                "manifests"
        );

        if (!manifestsNode.isArray() || manifestsNode.isEmpty()) {
            throw new IOException(
                    "Invalid manifest list: no manifests found"
            );
        }

        // Look for linux/amd64 manifest by default
        String selectedDigest = null;
        String selectedPlatform = null;

        for (JsonNode manifestNode : manifestsNode) {
            JsonNode platformNode = manifestNode.path(
                    "platform"
            );
            String digest = manifestNode.path(
                    "digest"
            ).asText();

            if (platformNode.isMissingNode() || digest.isEmpty()) {
                continue;
            }

            String os = platformNode.path(
                    "os"
            ).asText();
            String architecture = platformNode.path(
                    "architecture"
            ).asText();

            String platform = os + "/" + architecture;
            log.info(
                    "Found manifest for platform: {}",
                    platform
            );

            // Prioritize linux/amd64
            if ("linux/amd64".equals(
                    platform
            )) {
                selectedDigest = digest;
                selectedPlatform = platform;
                break;
            }

            // Fall back to first available
            if (selectedDigest == null) {
                selectedDigest = digest;
                selectedPlatform = platform;
            }
        }

        if (selectedDigest == null) {
            throw new IOException(
                    "No suitable platform manifest found in manifest list"
            );
        }

        log.info(
                "Selected manifest for platform {}: {}",
                selectedPlatform,
                selectedDigest
        );

        // Pull the platform-specific manifest
        ImageRef platformRef = new ImageRef();
        platformRef.registry = ref.registry;
        platformRef.fullRepositoryPath = ref.fullRepositoryPath;
        platformRef.tag = selectedDigest; // Use digest as tag for pulling

        String platformManifestUrl = buildManifestUrl(
                platformRef,
                protocol
        );
        log.info(
                "Pulling platform manifest from: {}",
                platformManifestUrl
        );

        // For platform-specific manifests, we may need to authenticate again
        return pullManifest(
                platformRef,
                username,
                password,
                protocol,
                insecure
        );
    }

    /**
     * Get Bearer token from registry
     */
    private String getBearerToken(
            String wwwAuth,
            String username,
            String password,
            ImageRef ref,
            String manifestUrl
    )
            throws IOException {
        try {
            String challenge = wwwAuth.substring(
                    7
            );
            String realm = null;
            String service = null;
            String scope = null;

            String[] params = challenge.split(
                    ","
            );
            for (String param : params) {
                String[] keyValue = param.trim()
                        .split(
                                "=",
                                2
                        );
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1].replace(
                            "\"",
                            ""
                    );
                    if ("realm".equals(
                            key
                    ))
                        realm = value;
                    else if ("service".equals(
                            key
                    ))
                        service = value;
                    else if ("scope".equals(
                            key
                    ))
                        scope = value;
                }
            }

            if (realm == null) {
                throw new IOException(
                        "Invalid WWW-Authenticate header"
                );
            }

            StringBuilder tokenUrl = new StringBuilder(
                    realm
            ).append(
                    "?service="
            )
                    .append(
                            service != null ? service : ref.registry
                    );

            if (scope != null) {
                tokenUrl.append(
                        "&scope="
                )
                        .append(
                                scope
                        );
            }
            else {
                tokenUrl.append(
                        "&scope=repository:"
                )
                        .append(
                                ref.fullRepositoryPath
                        )
                        .append(
                                ":pull"
                        );
            }

            log.info(
                    "Requesting token from: {}",
                    tokenUrl
            );

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    tokenUrl.toString()
                            )
                    )
                    .timeout(
                            Duration.ofSeconds(
                                    30
                            )
                    );

            if (username != null && password != null) {
                String auth = Base64.getEncoder()
                        .encodeToString(
                                (username + ":" + password).getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );
                builder.header(
                        "Authorization",
                        "Basic " + auth
                );
            }

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .build();

            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IOException(
                        "Token request failed: " + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(
                    response.body()
            );
            String token = root.path(
                    "token"
            ).asText();

            if (token.isEmpty()) {
                throw new IOException(
                        "No token in response"
                );
            }

            log.info(
                    "Successfully obtained token"
            );
            return token;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Token request interrupted",
                    e
            );
        }
    }

    /**
     * Get anonymous access token for Docker Hub
     */
    private String getDockerHubAnonymousToken(
            String repository
    )
            throws IOException {
        try {
            String tokenUrl = "https://auth.docker.io/token?service=registry.docker.io&scope=repository:" + repository
                    + ":pull";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    tokenUrl
                            )
                    )
                    .timeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .GET()
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(
                        response.body()
                );
                return root.path(
                        "token"
                ).asText();
            }
            else {
                log.warn(
                        "Failed to get Docker Hub token. Status: {}",
                        response.statusCode()
                );
                return null;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Failed to get Docker Hub token",
                    e
            );
        }
    }

    /**
     * Pull and store all blobs (config and layers)
     */
    private void pullAndStoreBlobs(
            ImageRef ref,
            ManifestContent manifest,
            String username,
            String password,
            String protocol,
            boolean insecure
    )
            throws IOException {
        if (manifest.configDigest != null && !manifest.configDigest.isEmpty()) {
            log.info(
                    "Pulling config blob: {}",
                    manifest.configDigest
            );
            byte[] configData = pullBlob(
                    ref,
                    manifest.configDigest,
                    username,
                    password,
                    protocol,
                    insecure
            );
            storeBlob(
                    manifest.configDigest,
                    new ByteArrayInputStream(
                            configData
                    ),
                    manifest.configSize
            );
        }

        for (String layerDigest : manifest.layerDigests) {
            log.info(
                    "Pulling layer blob: {}",
                    layerDigest
            );
            Long expectedSize = manifest.layerSizes.get(
                    layerDigest
            );
            byte[] layerData = pullBlob(
                    ref,
                    layerDigest,
                    username,
                    password,
                    protocol,
                    insecure
            );
            storeBlob(
                    layerDigest,
                    new ByteArrayInputStream(
                            layerData
                    ),
                    expectedSize
            );
        }
    }

    /**
     * Pull a single blob from source registry
     */
    private byte[] pullBlob(
            ImageRef ref,
            String digest,
            String username,
            String password,
            String protocol,
            boolean insecure
    )
            throws IOException {
        String blobUrl = buildBlobUrl(
                ref,
                digest,
                protocol
        );
        log.info(
                "Pulling blob from: {}",
                blobUrl
        );

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    blobUrl
                            )
                    )
                    .timeout(
                            Duration.ofMinutes(
                                    10
                            )
                    );

            String cacheKey = ref.registry + ":" + ref.fullRepositoryPath;
            String token = tokenCache.get(
                    cacheKey
            );
            if (token != null) {
                requestBuilder.header(
                        "Authorization",
                        "Bearer " + token
                );
            }
            else if (username != null && password != null) {
                String auth = Base64.getEncoder()
                        .encodeToString(
                                (username + ":" + password).getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );
                requestBuilder.header(
                        "Authorization",
                        "Basic " + auth
                );
            }

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .build();

            HttpResponse<InputStream> response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            int statusCode = response.statusCode();

            if (statusCode == 401 && token != null) {
                // Token expired, try without token
                response.body().close();
                requestBuilder = HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        blobUrl
                                )
                        )
                        .timeout(
                                Duration.ofMinutes(
                                        10
                                )
                        );

                if (username != null && password != null) {
                    String auth = Base64.getEncoder()
                            .encodeToString(
                                    (username + ":" + password).getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );
                    requestBuilder.header(
                            "Authorization",
                            "Basic " + auth
                    );
                }

                response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                statusCode = response.statusCode();
            }

            if (statusCode != 200) {
                throw new IOException(
                        "Failed to pull blob " + digest + ". Status: " + statusCode
                );
            }

            try (
                    InputStream inputStream = response.body();
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                inputStream.transferTo(
                        buffer
                );
                return buffer.toByteArray();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Failed to pull blob: " + digest,
                    e
            );
        }
    }

    /**
     * Store blob in our registry
     */
    private void storeBlob(
            String digest,
            InputStream data,
            Long expectedSize
    )
            throws IOException {
        try {
            if (storageService.blobExists(
                    digest
            )) {
                log.info(
                        "Blob already exists: {}",
                        digest
                );
                return;
            }

            String storedDigest = storageService.storeBlob(
                    data,
                    digest
            );

            Blob blob = blobStorage.findByDigest(
                    digest
            );
            if (blob == null) {
                blob = new Blob();
                blob.digest = digest;
                blob.contentLength = expectedSize;
                blob.mediaType = digest.startsWith(
                        "sha256:"
                ) && digest.equals(
                        storedDigest
                )
                        ? "application/vnd.docker.container.image.v1+json"
                        : "application/vnd.docker.image.rootfs.diff.tar.gzip";
                blobStorage.persist(
                        blob
                );
                log.info(
                        "Stored blob: {}",
                        digest
                );
            }
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob: " + digest,
                    e
            );
        }
    }

    /**
     * Store manifest in our registry
     */
    private void storeManifest(
            String repository,
            String tag,
            ManifestContent manifest
    )
            throws IOException {
        try {
            var existingManifest = manifestStorage.findByRepositoryAndTag(
                    repository,
                    tag
            );
            if (existingManifest != null) {
                log.info(
                        "Tag '{}' already exists in repository '{}'. Overwriting.",
                        tag,
                        repository
                );
                manifestStorage.delete(
                        existingManifest.id
                );
            }

            Manifest newManifest = new Manifest();
            newManifest.repositoryName = repository;
            newManifest.tag = tag;
            newManifest.digest = manifest.digest;
            newManifest.configDigest = manifest.configDigest;
            newManifest.layerDigests = manifest.layerDigests;
            newManifest.mediaType = "application/vnd.docker.distribution.manifest.v2+json";
            newManifest.content = manifest.json;
            newManifest.contentLength = (long) manifest.json.getBytes().length;
            manifestStorage.persist(
                    newManifest
            );

            log.info(
                    "Stored manifest: {}:{} with digest {}",
                    repository,
                    tag,
                    manifest.digest
            );

        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store manifest",
                    e
            );
        }
    }

    /**
     * Create repository if it doesn't exist
     */
    private void createRepository(
            String repositoryName
    ) {
        Repository repository = repositoryStorage.findByName(
                repositoryName
        );
        if (repository == null) {
            repository = new Repository();
            repository.name = repositoryName;
            repositoryStorage.persist(
                    repository
            );
            log.info(
                    "Created repository: {}",
                    repositoryName
            );
        }
    }

    /**
     * Build manifest URL for source registry
     */
    private String buildManifestUrl(
            ImageRef ref,
            String protocol
    ) {
        if (protocol == null || (!protocol.equals(
                "http"
        ) && !protocol.equals(
                "https"
        ))) {
            protocol = "https";
        }

        if (ref.registry.equals(
                "docker.io"
        )) {
            return "https://registry-1.docker.io/v2/" + ref.fullRepositoryPath + "/manifests/" + ref.tag;
        }
        return protocol + "://" + ref.registry + "/v2/" + ref.fullRepositoryPath + "/manifests/" + ref.tag;
    }

    /**
     * Build blob URL for source registry
     */
    private String buildBlobUrl(
            ImageRef ref,
            String digest,
            String protocol
    ) {
        if (protocol == null || (!protocol.equals(
                "http"
        ) && !protocol.equals(
                "https"
        ))) {
            protocol = "https";
        }

        if (ref.registry.equals(
                "docker.io"
        )) {
            return "https://registry-1.docker.io/v2/" + ref.fullRepositoryPath + "/blobs/" + digest;
        }
        return protocol + "://" + ref.registry + "/v2/" + ref.fullRepositoryPath + "/blobs/" + digest;
    }

    /**
     * Calculate SHA256 digest from string
     */
    private String calculateSha256(
            String content
    )
            throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] hash = digest.digest(
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(
                        0xff & b
                );
                if (hex.length() == 1)
                    hexString.append(
                            '0'
                    );
                hexString.append(
                        hex
                );
            }
            return hexString.toString();
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to calculate SHA256",
                    e
            );
        }
    }

    /**
     * Create error response
     */
    private Response createErrorResponse(
            Response.Status status,
            String code,
            String message,
            String detail
    ) {
        return Response.status(
                status
        )
                .entity(
                        new ErrorResponse(
                                List.of(
                                        new ErrorResponse.Error(
                                                code,
                                                message,
                                                detail
                                        )
                                )
                        )
                )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }

    // Inner classes

    static class ImageRef {
        String registry = "docker.io";

        String namespace = "library";

        String repository;

        String tag = "latest";

        String fullRepositoryPath;
    }

    static class ManifestContent {
        String digest;

        String json;

        String configDigest;

        long configSize;

        List<String> layerDigests = new ArrayList<>();

        Map<String, Long> layerSizes = new HashMap<>();
    }

    static class MirrorResult {
        public boolean success;

        public String sourceImage;

        public String targetRepository;

        public String targetTag;

        public String manifestDigest;

        public int blobsCount;
    }
}
