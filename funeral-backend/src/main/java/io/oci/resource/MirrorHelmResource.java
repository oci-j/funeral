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
 * Resource for mirroring/pulling Helm charts from external repositories. Supports both OCI format and chartmuseum
 * format.
 */
@Path(
    "/funeral_addition/mirror/helm"
)
@ApplicationScoped
public class MirrorHelmResource {

    private static final Logger log = LoggerFactory.getLogger(
            MirrorHelmResource.class
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

    /**
     * Mirror/Pull Helm chart from external repository
     *
     * @param sourceRepo Full source repository URL or name
     * @param chartName Chart name
     * @param version Chart version
     * @param targetRepository Optional target repository name
     * @param targetVersion Optional target version
     * @param username Optional username for authentication
     * @param password Optional password for authentication
     * @param format Format of the source (oci or chartmuseum)
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
    /**
     * Check if registry is accessible with a quick ping
     */
    private boolean isRegistryAccessible(
            String registry
    ) {
        try {
            String testUrl = registry.contains(
                    "docker.io"
            ) ? "https://registry.hub.docker.com/v2/" : "https://" + registry + "/v2/";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    testUrl
                            )
                    )
                    .timeout(
                            Duration.ofSeconds(
                                    5
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
                                    5
                            )
                    )
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Docker registry returns 401 (unauthorized) for v2/ endpoint, which is expected
            return response.statusCode() == 401 || response.statusCode() == 200;
        }
        catch (Exception e) {
            log.warn(
                    "Registry accessibility check failed for {}: {}",
                    registry,
                    e.getMessage()
            );
            return false;
        }
    }

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
    public Response mirrorHelmChart(
            @FormParam(
                "sourceRepo"
            )
            String sourceRepo,
            @FormParam(
                "chartName"
            )
            String chartName,
            @FormParam(
                "version"
            )
            String version,
            @FormParam(
                "targetRepository"
            )
            String targetRepository,
            @FormParam(
                "targetVersion"
            )
            String targetVersion,
            @FormParam(
                "username"
            )
            String username,
            @FormParam(
                "password"
            )
            String password,
            @FormParam(
                "format"
            )
            @DefaultValue(
                "oci"
            )
            String format
    )
            throws IOException,
            InterruptedException {
        log.info(
                "Mirror Helm request for chart: {} from {} (format: {})",
                chartName,
                sourceRepo,
                format
        );

        if (chartName == null || chartName.trim().isEmpty()) {
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Chart name is required",
                    null
            );
        }

        if (sourceRepo == null || sourceRepo.trim().isEmpty()) {
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Source repository is required",
                    null
            );
        }

        try {
            // Normalize source repository URL
            String normalizedRepo = normalizeRepositoryUrl(
                    sourceRepo,
                    format
            );

            String finalTargetRepo = targetRepository != null ? targetRepository : chartName;
            String finalTargetVersion = targetVersion != null ? targetVersion : version;

            log.info(
                    "Mirroring Helm chart from {} to {}:{}",
                    sourceRepo,
                    finalTargetRepo,
                    finalTargetVersion
            );

            MirrorResult result = new MirrorResult();

            if ("oci".equalsIgnoreCase(
                    format
            )) {
                result = mirrorOCIChart(
                        normalizedRepo,
                        chartName,
                        version,
                        finalTargetRepo,
                        finalTargetVersion,
                        username,
                        password
                );
            }
            else if ("chartmuseum".equalsIgnoreCase(
                    format
            )) {
                result = mirrorChartmuseumChart(
                        normalizedRepo,
                        chartName,
                        version,
                        finalTargetRepo,
                        finalTargetVersion,
                        username,
                        password
                );
            }
            else {
                return createErrorResponse(
                        Response.Status.BAD_REQUEST,
                        "BAD_REQUEST",
                        "Unsupported chart format: " + format,
                        null
                );
            }

            return Response.ok(
                    result
            ).build();

        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(
                    "Failed to mirror Helm chart: {} from {} (interrupted)",
                    chartName,
                    sourceRepo,
                    e
            );
            return createErrorResponse(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "MIRROR_FAILED",
                    "Mirror operation interrupted: " + e.getMessage(),
                    null
            );
        }
        catch (Exception e) {
            log.error(
                    "Failed to mirror Helm chart: {} from {}",
                    chartName,
                    sourceRepo,
                    e
            );

            // Check for network/connectivity issues
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains(
                    "timed out"
            ) || errorMessage.contains(
                    "ConnectException"
            ) || errorMessage.contains(
                    "Unable to connect"
            ))) {

                String details = String.format(
                        "Network error connecting to %s. " + "Please check: 1) Network connectivity, "
                                + "2) Registry URL is correct (%s), " + "3) No firewall/proxy blocking access.%s"
                                + "Original error: %s",
                        sourceRepo,
                        (sourceRepo != null ? "for docker.io use 'registry.hub.docker.com'" : ""),
                        System.lineSeparator(),
                        errorMessage
                );

                return createErrorResponse(
                        Response.Status.BAD_GATEWAY,
                        "NETWORK_ERROR",
                        "Failed to connect to external registry. "
                                + "Please check your network settings and registry URL.",
                        details
                );
            }

            return createErrorResponse(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "MIRROR_FAILED",
                    "Failed to mirror Helm chart: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Mirror Helm chart from OCI registry
     */
    private MirrorResult mirrorOCIChart(
            String sourceRepo,
            String chartName,
            String version,
            String targetRepo,
            String targetVersion,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        log.info(
                "Mirroring OCI chart: {}:{} from {}",
                chartName,
                version,
                sourceRepo
        );

        // OCI format stores charts in OCI registries
        // The format is: <registry>/<chart>:<version>
        String sourceImage = sourceRepo + "/" + chartName + ":" + version;

        // Call the existing mirror logic from MirrorResource
        // For simplicity, we'll use a simplified version here
        return pullOCIChart(
                sourceRepo,
                chartName,
                version,
                targetRepo,
                targetVersion,
                username,
                password
        );
    }

    /**
     * Pull Helm chart from OCI registry
     */
    private MirrorResult pullOCIChart(
            String registry,
            String chartName,
            String version,
            String targetRepo,
            String targetVersion,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        MirrorResult result = new MirrorResult();
        result.format = "oci";

        try {
            // Build OCI image reference for the chart
            // Helm charts in OCI format follow the pattern: <registry>/<chart>:<version>
            String imageRef = registry + "/" + chartName + ":" + version;

            log.info(
                    "Pulling OCI chart from: {}",
                    imageRef
            );

            // Parse the OCI reference
            ImageRef ref = parseOCIReference(
                    registry,
                    chartName,
                    version
            );

            // Get the manifest from OCI registry
            ManifestContent manifestContent = pullOCIManifest(
                    ref,
                    username,
                    password
            );

            // If this is an OCI index (manifest list), fetch the actual manifest
            if (manifestContent.isIndex) {
                log.info(
                        "=== OCI INDEX DETECTED ==="
                );
                log.info(
                        "OCI index detected, fetching actual manifest: {}",
                        manifestContent.indexManifestDigest
                );
                log.info(
                        "Index JSON: {}",
                        manifestContent.json
                );

                // Override the tag with the full digest from the index (including sha256: prefix)
                // This is required for fetching manifests by digest
                ImageRef digestRef = new ImageRef();
                digestRef.registry = ref.registry;
                digestRef.fullRepositoryPath = ref.fullRepositoryPath;
                digestRef.tag = manifestContent.indexManifestDigest;

                log.info(
                        "Fetching manifest with digest reference: {}/{}",
                        digestRef.fullRepositoryPath,
                        digestRef.tag
                );

                manifestContent = pullOCIManifest(
                        digestRef,
                        username,
                        password
                );

                log.info(
                        "=== RESOLVED MANIFEST ==="
                );
                log.info(
                        "Final manifest configDigest: {}",
                        manifestContent.configDigest
                );
                log.info(
                        "Final manifest layers: {}",
                        manifestContent.layerDigests
                );
            }

            // Store all blobs (config and layers)
            log.info(
                    "Storing {} blobs from OCI chart",
                    manifestContent.layerDigests.size() + 1
            );

            // Pull and store config blob
            if (manifestContent.configDigest != null) {
                byte[] configData = pullOCIBlob(
                        ref,
                        manifestContent.configDigest,
                        username,
                        password
                );
                storeBlob(
                        manifestContent.configDigest,
                        new ByteArrayInputStream(
                                configData
                        ),
                        manifestContent.configSize,
                        "application/vnd.cncf.helm.config.v1+json"
                );
            }

            // Pull and store layer blobs
            for (String layerDigest : manifestContent.layerDigests) {
                Long layerSize = manifestContent.layerSizes.get(
                        layerDigest
                );
                log.info(
                        "Pulling layer: {} (size: {})",
                        layerDigest,
                        layerSize
                );
                byte[] layerData = pullOCIBlob(
                        ref,
                        layerDigest,
                        username,
                        password
                );
                storeBlob(
                        layerDigest,
                        new ByteArrayInputStream(
                                layerData
                        ),
                        layerSize,
                        "application/vnd.cncf.helm.chart.content.v1.tar+gzip"
                );
            }

            // Store the manifest
            storeManifest(
                    targetRepo,
                    targetVersion,
                    manifestContent
            );

            // Create repository if doesn't exist
            createRepository(
                    targetRepo
            );

            result.success = true;
            result.chart = chartName;
            result.version = version;
            result.targetChart = targetRepo;
            result.targetVersion = targetVersion;
            result.source = registry;
            result.blobsCount = manifestContent.layerDigests.size() + 1;
            result.digest = manifestContent.digest;

            return result;

        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Failed to pull OCI chart from: " + registry,
                    e
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to pull OCI chart from: " + registry,
                    e
            );
        }
    }

    /**
     * Mirror Helm chart from chartmuseum repository
     */
    private MirrorResult mirrorChartmuseumChart(
            String sourceRepo,
            String chartName,
            String version,
            String targetRepo,
            String targetVersion,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        log.info(
                "Mirroring chartmuseum chart: {}:{} from {}",
                chartName,
                version,
                sourceRepo
        );

        // Chartmuseum format uses HTTP API
        // First, get the chart index
        String chartUrl = buildChartmuseumUrl(
                sourceRepo,
                chartName,
                version
        );

        log.info(
                "=== CHARTMUSEUM MIRROR ==="
        );
        log.info(
                "Chart: {}:{}",
                chartName,
                version
        );
        log.info(
                "Source repo: {}",
                sourceRepo
        );
        log.info(
                "Generated URL: {}",
                chartUrl
        );

        // Download the chart tarball
        byte[] chartData = downloadChart(
                chartUrl,
                username,
                password
        );

        // Calculate digest for chart layer
        String layerDigest = "sha256:" + calculateSha256(
                chartData
        );

        // Store chart as layer blob with correct media type
        storeBlob(
                layerDigest,
                new ByteArrayInputStream(
                        chartData
                ),
                (long) chartData.length,
                "application/vnd.cncf.helm.chart.content.v1.tar+gzip"
        );

        // Create config blob with chart metadata
        String chartMetadata = createChartMetadataJson(
                chartName,
                version,
                sourceRepo
        );
        String configDigest = "sha256:" + calculateSha256(
                chartMetadata
        );
        long configSize = chartMetadata.getBytes(
                StandardCharsets.UTF_8
        ).length;
        storeBlob(
                configDigest,
                new ByteArrayInputStream(
                        chartMetadata.getBytes(
                                StandardCharsets.UTF_8
                        )
                ),
                configSize,
                "application/vnd.cncf.helm.config.v1+json"
        );

        // Create OCI manifest
        String ociManifestJson = buildOCIManifest(
                configDigest,
                configSize,
                layerDigest,
                chartData.length,
                chartName,
                version
        );
        String manifestDigest = "sha256:" + calculateSha256(
                ociManifestJson
        );

        // Create manifest content
        ManifestContent manifest = new ManifestContent();
        manifest.digest = manifestDigest;
        manifest.json = ociManifestJson;
        manifest.configDigest = configDigest;
        manifest.configSize = configSize;
        manifest.layerDigests.add(
                layerDigest
        );
        manifest.layerSizes.put(
                layerDigest,
                (long) chartData.length
        );

        // Store manifest
        storeManifest(
                targetRepo,
                targetVersion,
                manifest
        );

        // Create repository
        createRepository(
                targetRepo
        );

        MirrorResult result = new MirrorResult();
        result.success = true;
        result.format = "chartmuseum";
        result.chart = chartName;
        result.version = version;
        result.targetChart = targetRepo;
        result.targetVersion = targetVersion;
        result.source = sourceRepo;
        result.blobsCount = 2; // config + layer
        result.digest = manifestDigest;

        return result;
    }

    /**
     * Download chart from chartmuseum URL
     */
    private byte[] downloadChart(
            String url,
            String username,
            String password
    )
            throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    url
                            )
                    )
                    .header(
                            "User-Agent",
                            "FUNERAL-Helm-Client/1.0"
                    )
                    .timeout(
                            Duration.ofMinutes(
                                    5
                            )
                    );

            // Add authentication if provided
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

            HttpResponse<InputStream> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() != 200) {
                throw new IOException(
                        "Failed to download chart from " + url + ". Status: " + response.statusCode()
                                + ". The chart may not exist or requires authentication."
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
                    "Chart download interrupted",
                    e
            );
        }
    }

    /**
     * Build chartmuseum URL for chart download
     */
    private String buildChartmuseumUrl(
            String repoBase,
            String chartName,
            String version
    ) {
        // Normalize repository URL
        String baseUrl = repoBase;
        if (baseUrl.endsWith(
                "/"
        )) {
            baseUrl = baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        // Handle different ChartMuseum URL formats
        // Bitnami format: https://charts.bitnami.com/bitnami/<chart>-<version>.tgz
        // Standard ChartMuseum: https://charts.example.com/charts/<chart>-<version>.tgz

        String simpleChartName = chartName;
        String orgPrefix = "";

        if (chartName.contains(
                "/"
        )) {
            // Extract the chart name without organization prefix
            // e.g., "bitnami/mongodb" -> "mongodb"
            simpleChartName = chartName.substring(
                    chartName.indexOf(
                            "/"
                    ) + 1
            );
            // Extract organization prefix for Bitnami style repos
            orgPrefix = chartName.substring(
                    0,
                    chartName.indexOf(
                            "/"
                    )
            );
        }

        if (baseUrl.contains(
                "bitnami.com"
        )) {
            // For Bitnami, check if the base URL already includes the org prefix
            if (!baseUrl.endsWith(
                    "/" + orgPrefix
            ) && !baseUrl.contains(
                    "/" + orgPrefix + "/"
            )) {
                // Base URL doesn't include org path, add it
                return baseUrl + "/" + orgPrefix + "/" + simpleChartName + "-" + version + ".tgz";
            }
            else {
                // Base URL already includes org path
                return baseUrl + "/" + simpleChartName + "-" + version + ".tgz";
            }
        }
        else {
            // Standard ChartMuseum format
            return baseUrl + "/charts/" + chartName + "-" + version + ".tgz";
        }
    }

    /**
     * Normalize repository URL
     */
    private String normalizeRepositoryUrl(
            String repoUrl,
            String format
    ) {
        if ("oci".equalsIgnoreCase(
                format
        )) {
            // For OCI, remove oci:// prefix if present
            if (repoUrl.startsWith(
                    "oci://"
            )) {
                return repoUrl.substring(
                        6
                );
            }
            return repoUrl;
        }
        else {
            // For chartmuseum, ensure proper protocol and no trailing slash
            if (!repoUrl.startsWith(
                    "http://"
            ) && !repoUrl.startsWith(
                    "https://"
            )) {
                // Default to HTTPS
                return "https://" + repoUrl;
            }
            return repoUrl;
        }
    }

    /**
     * Create chart metadata JSON
     */
    private String createChartMetadataJson(
            String chartName,
            String version,
            String source
    ) {
        return "{" + "\"name\":\"" + chartName + "\"," + "\"version\":\"" + version + "\"," + "\"source\":\"" + source
                + "\"," + "\"type\":\"helm\"" + "}";
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
            return bytesToHex(
                    hash
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to calculate SHA256",
                    e
            );
        }
    }

    /**
     * Calculate SHA256 digest from byte array
     */
    private String calculateSha256(
            byte[] content
    )
            throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance(
                    "SHA-256"
            );
            byte[] hash = digest.digest(
                    content
            );
            return bytesToHex(
                    hash
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to calculate SHA256",
                    e
            );
        }
    }

    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(
            byte[] bytes
    ) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
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
                                Collections.singletonList(
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

    /**
     * Store blob in our registry
     */
    private void storeBlob(
            String digest,
            InputStream data,
            Long expectedSize,
            String mediaType
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
                blob.mediaType = mediaType;
                blobStorage.persist(
                        blob
                );
                log.info(
                        "Stored blob: {} with media type: {}",
                        digest,
                        mediaType
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
     * Store blob in our registry (deprecated - use version with mediaType parameter)
     */
    private void storeBlob(
            String digest,
            InputStream data,
            Long expectedSize
    )
            throws IOException {
        storeBlob(
                digest,
                data,
                expectedSize,
                "application/vnd.cncf.helm.config.v1+json"
        );
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
            newManifest.mediaType = "application/vnd.oci.image.manifest.v1+json";
            newManifest.artifactType = "application/vnd.cncf.helm.chart.v1+json";
            newManifest.content = manifest.json;
            newManifest.contentLength = (long) manifest.json.getBytes().length;
            manifestStorage.persist(
                    newManifest
            );

            // Store the manifest JSON as a blob so it can be accessed via blobs endpoint
            storeBlob(
                    manifest.digest,
                    new ByteArrayInputStream(
                            manifest.json.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    ),
                    (long) manifest.json.getBytes().length,
                    "application/vnd.oci.image.manifest.v1+json"
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
     * Parse OCI reference components
     */
    private ImageRef parseOCIReference(
            String registry,
            String chartName,
            String version
    ) {
        ImageRef ref = new ImageRef();
        ref.registry = registry;
        ref.fullRepositoryPath = chartName;
        ref.tag = version;
        return ref;
    }

    /**
     * Pull manifest from OCI registry for Helm chart
     */
    private ManifestContent pullOCIManifest(
            ImageRef ref,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        String manifestUrl = buildManifestUrl(
                ref,
                "https"
        );

        log.info(
                "Pulling OCI manifest from: {}",
                manifestUrl
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(
                        URI.create(
                                manifestUrl
                        )
                )
                .header(
                        "Accept",
                        "application/vnd.oci.image.manifest.v1+json," + "application/vnd.oci.image.index.v1+json,"
                                + "application/vnd.docker.distribution.manifest.v2+json,"
                                + "application/vnd.docker.distribution.manifest.list.v2+json"
                )
                .timeout(
                        Duration.ofMinutes(
                                5
                        )
                );

        // Add authentication for OCI registries
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
        // For Docker Hub, try anonymous token
        else if (ref.registry.contains(
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
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 200) {
            // Parse the manifest
            return parseOCIManifest(
                    response.body()
            );
        }
        else if (response.statusCode() == 401) {
            throw new IOException(
                    "Authentication required for registry: " + ref.registry
            );
        }
        else {
            throw new IOException(
                    "Failed to pull manifest. Status: " + response.statusCode()
            );
        }
    }

    /**
     * Parse OCI manifest for Helm chart
     */
    private ManifestContent parseOCIManifest(
            String manifestJson
    )
            throws IOException {
        ManifestContent content = new ManifestContent();
        content.json = manifestJson;
        content.digest = "sha256:" + calculateSha256(
                manifestJson
        );

        JsonNode root = objectMapper.readTree(
                manifestJson
        );

        // Check if this is an OCI index (manifest list)
        String mediaType = root.path(
                "mediaType"
        ).asText();
        log.info(
                "Manifest mediaType: {}",
                mediaType
        );

        // For OCI index, we need to find the platform-specific manifest
        if ("application/vnd.oci.image.index.v1+json".equals(
                mediaType
        )) {
            log.warn(
                    "=== OCI INDEX DETECTED IN RESPONSE ==="
            );
            log.warn(
                    "MediaType: {}",
                    mediaType
            );
            log.warn(
                    "This is a manifest list, not the actual manifest"
            );
            log.warn(
                    "Full response: {}",
                    manifestJson
            );

            JsonNode manifests = root.path(
                    "manifests"
            );
            if (manifests.isArray() && !manifests.isEmpty()) {
                log.warn(
                        "Index contains {} manifests",
                        manifests.size()
                );

                // For Helm charts, typically all architectures have the same content
                // We'll use the first available manifest
                JsonNode firstManifest = manifests.get(
                        0
                );
                String manifestDigest = firstManifest.path(
                        "digest"
                ).asText();
                String platform = "unknown";

                // Try to get platform info if available
                JsonNode platformNode = firstManifest.path(
                        "platform"
                );
                if (!platformNode.isMissingNode()) {
                    String os = platformNode.path(
                            "os"
                    )
                            .asText(
                                    "unknown"
                            );
                    String arch = platformNode.path(
                            "architecture"
                    )
                            .asText(
                                    "unknown"
                            );
                    platform = os + "/" + arch;
                }

                log.warn(
                        "Using first manifest: {} (platform: {})",
                        manifestDigest,
                        platform
                );

                // Return the index content but mark that we need to fetch the actual manifest
                // The caller will need to handle this by fetching the manifest with the digest
                content.json = manifestJson; // Store the index JSON
                content.digest = "sha256:" + calculateSha256(
                        manifestJson
                );
                content.isIndex = true;
                content.indexManifestDigest = manifestDigest;

                log.warn(
                        "Marked as OCI index, will fetch actual manifest with digest: {}",
                        manifestDigest
                );
                return content;
            }
        }
        else {
            // Regular OCI manifest
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

            // Get layers (the actual chart data)
            JsonNode layersNode = root.path(
                    "layers"
            );
            if (layersNode.isArray()) {
                log.info(
                        "Found {} layers",
                        layersNode.size()
                );
                for (JsonNode layer : layersNode) {
                    String digest = layer.path(
                            "digest"
                    ).asText();
                    Long size = layer.path(
                            "size"
                    ).asLong();
                    content.layerDigests.add(
                            digest
                    );
                    content.layerSizes.put(
                            digest,
                            size
                    );

                    log.info(
                            "Layer: {} (size: {})",
                            digest,
                            size
                    );
                }
            }
        }

        log.info(
                "Successfully parsed OCI manifest with {} layers",
                content.layerDigests.size()
        );

        return content;
    }

    /**
     * Pull blob from OCI registry
     */
    private byte[] pullOCIBlob(
            ImageRef ref,
            String digest,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        String blobUrl = buildBlobUrl(
                ref,
                digest,
                "https"
        );

        log.info(
                "Pulling blob from OCI registry: {}",
                blobUrl
        );

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

        // Add authentication
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
        // For Docker Hub
        else if (ref.registry.contains(
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

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Failed to pull blob " + digest + ". Status: " + response.statusCode()
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

    /**
     * Get anonymous access token for Docker Hub
     */
    private String getDockerHubAnonymousToken(
            String repository
    )
            throws IOException,
            InterruptedException {
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
     * Build manifest URL for OCI registry
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

        String registryHost = ref.registry;

        // Map docker.io to registry.hub.docker.com for OCI artifacts
        if (registryHost.contains(
                "docker.io"
        ) || registryHost.equals(
                "registry-1.docker.io"
        )) {
            registryHost = "registry.hub.docker.com";
        }

        if (registryHost.equals(
                "registry.hub.docker.com"
        )) {
            return "https://registry.hub.docker.com/v2/" + ref.fullRepositoryPath + "/manifests/" + ref.tag;
        }
        return protocol + "://" + registryHost + "/v2/" + ref.fullRepositoryPath + "/manifests/" + ref.tag;
    }

    /**
     * Build OCI manifest JSON with Helm annotations
     */
    private String buildOCIManifest(
            String configDigest,
            Long configSize,
            String layerDigest,
            Integer layerSize,
            String chartName,
            String version
    ) {
        // Build current timestamp
        String timestamp = java.time.ZonedDateTime.now()
                .format(
                        java.time.format.DateTimeFormatter.ISO_INSTANT
                );

        // Extract chart name without organization for annotations
        String simpleChartName = chartName;
        if (chartName.contains(
                "/"
        )) {
            simpleChartName = chartName.substring(
                    chartName.indexOf(
                            "/"
                    ) + 1
            );
        }

        // Build OCI manifest with Helm annotations
        StringBuilder manifest = new StringBuilder();
        manifest.append(
                "{"
        )
                .append(
                        "\"schemaVersion\":2,"
                )
                .append(
                        "\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\","
                )
                .append(
                        "\"artifactType\":\"application/vnd.cncf.helm.chart.v1+json\","
                )
                .append(
                        "\"config\":{"
                )
                .append(
                        "\"mediaType\":\"application/vnd.cncf.helm.config.v1+json\","
                )
                .append(
                        "\"digest\":\""
                )
                .append(
                        configDigest
                )
                .append(
                        "\","
                )
                .append(
                        "\"size\":"
                )
                .append(
                        configSize
                )
                .append(
                        ""
                )
                .append(
                        "},"
                )
                .append(
                        "\"layers\":["
                )
                .append(
                        "{"
                )
                .append(
                        "\"mediaType\":\"application/vnd.cncf.helm.chart.content.v1.tar+gzip\","
                )
                .append(
                        "\"digest\":\""
                )
                .append(
                        layerDigest
                )
                .append(
                        "\","
                )
                .append(
                        "\"size\":"
                )
                .append(
                        layerSize
                )
                .append(
                        ""
                )
                .append(
                        "}"
                )
                .append(
                        "],"
                )
                .append(
                        "\"annotations\":{"
                )
                .append(
                        "\"org.opencontainers.image.created\":\""
                )
                .append(
                        timestamp
                )
                .append(
                        "\","
                )
                .append(
                        "\"org.opencontainers.image.description\":\"Helm chart "
                )
                .append(
                        chartName
                )
                .append(
                        "\","
                )
                .append(
                        "\"org.opencontainers.image.title\":\""
                )
                .append(
                        simpleChartName
                )
                .append(
                        "\","
                )
                .append(
                        "\"org.opencontainers.image.version\":\""
                )
                .append(
                        version
                )
                .append(
                        "\""
                )
                .append(
                        "}"
                )
                .append(
                        "}"
                );
        return manifest.toString();
    }

    /**
     * Build blob URL for OCI registry
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

        String registryHost = ref.registry;

        // Map docker.io to registry.hub.docker.com for OCI artifacts
        if (registryHost.contains(
                "docker.io"
        ) || registryHost.equals(
                "registry-1.docker.io"
        )) {
            registryHost = "registry.hub.docker.com";
        }

        if (registryHost.equals(
                "registry.hub.docker.com"
        )) {
            return "https://registry.hub.docker.com/v2/" + ref.fullRepositoryPath + "/blobs/" + digest;
        }
        return protocol + "://" + registryHost + "/v2/" + ref.fullRepositoryPath + "/blobs/" + digest;
    }

    // Inner classes

    static class ImageRef {
        String registry = "docker.io";

        String fullRepositoryPath;

        String tag = "latest";
    }

    static class ManifestContent {
        String digest;

        String json;

        String configDigest;

        long configSize;

        List<String> layerDigests;

        Map<String, Long> layerSizes;

        // Fields for handling OCI indexes (manifest lists)
        boolean isIndex;

        String indexManifestDigest;

        ManifestContent() {
            this.layerDigests = new ArrayList<>();
            this.layerSizes = new HashMap<>();
            this.isIndex = false;
        }
    }

    static class MirrorResult {
        public boolean success;

        public String chart;

        public String version;

        public String targetChart;

        public String targetVersion;

        public String source;

        public String digest;

        public int blobsCount;

        public String format;
    }
}
