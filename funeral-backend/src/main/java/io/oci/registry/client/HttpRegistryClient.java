package io.oci.registry.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.model.ImageReference;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HttpRegistryClient implements RegistryClient {

    private static final Logger log = LoggerFactory.getLogger(
            HttpRegistryClient.class
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MANIFEST_ACCEPT_HEADER = "application/vnd.docker.distribution.manifest.v2+json,"
            + "application/vnd.docker.distribution.manifest.list.v2+json,"
            + "application/vnd.oci.image.manifest.v1+json," + "application/vnd.oci.image.index.v1+json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(
            30
    );

    private static final Duration MANIFEST_TIMEOUT = Duration.ofMinutes(
            5
    );

    private static final Duration BLOB_TIMEOUT = Duration.ofMinutes(
            10
    );

    private static final Pattern WWW_AUTHENTICATE_PARAM_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*\"([^\"]+)\""
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(
                    HttpClient.Redirect.NORMAL
            )
            .connectTimeout(
                    CONNECT_TIMEOUT
            )
            .build();

    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    @Override
    public ManifestResponse pullManifest(
            ImageReference ref,
            AuthContext auth
    )
            throws IOException {
        String manifestUrl = buildManifestUrl(
                ref,
                auth.protocol
        );
        log.info(
                "Pulling manifest from: {}",
                manifestUrl
        );

        HttpResponse<String> response = sendManifestRequest(
                ref,
                auth,
                manifestUrl,
                null
        );

        if (response.statusCode() == 200) {
            return processManifestResponse(
                    ref,
                    auth,
                    response
            );
        }
        else if (response.statusCode() == 401) {
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
                Optional<TokenResponse> token = authenticate(
                        wwwAuth,
                        ref,
                        auth
                );
                if (token.isPresent() && token.get().token != null) {
                    cacheToken(
                            ref,
                            token.get().token
                    );
                    HttpResponse<String> retryResponse = sendManifestRequest(
                            ref,
                            auth,
                            manifestUrl,
                            token.get().token
                    );
                    if (retryResponse.statusCode() == 200) {
                        return processManifestResponse(
                                ref,
                                auth,
                                retryResponse
                        );
                    }
                    else if (retryResponse.statusCode() == 401 || retryResponse.statusCode() == 403) {
                        throw new RegistryAuthenticationException(
                                retryResponse.statusCode(),
                                "Authentication failed for registry: " + ref.registry
                        );
                    }
                    else if (retryResponse.statusCode() == 404) {
                        throw new RegistryImageNotFoundException(
                                "Image not found: " + ref
                        );
                    }
                }
            }
            throw new RegistryAuthenticationException(
                    401,
                    "Authentication failed for registry: " + ref.registry
            );
        }
        else if (response.statusCode() == 404) {
            throw new RegistryImageNotFoundException(
                    "Image not found: " + ref
            );
        }
        else {
            throw new IOException(
                    "Failed to pull manifest. Status: " + response.statusCode()
            );
        }
    }

    @Override
    public InputStream pullBlob(
            ImageReference ref,
            String digest,
            AuthContext auth
    )
            throws IOException {
        String blobUrl = buildBlobUrl(
                ref,
                digest,
                auth.protocol
        );
        log.info(
                "Pulling blob from: {}",
                blobUrl
        );

        HttpResponse<InputStream> response = sendBlobRequest(
                ref,
                digest,
                auth,
                blobUrl
        );

        int statusCode = response.statusCode();
        if (statusCode == 200) {
            return response.body();
        }
        else if (statusCode == 401) {
            // Token may have expired; clear cache and retry without token
            String cacheKey = cacheKey(
                    ref
            );
            if (tokenCache.containsKey(
                    cacheKey
            )) {
                tokenCache.remove(
                        cacheKey
                );
                response.body().close();
                response = sendBlobRequest(
                        ref,
                        digest,
                        auth,
                        blobUrl
                );
                statusCode = response.statusCode();
                if (statusCode == 200) {
                    return response.body();
                }
            }
            throw new RegistryAuthenticationException(
                    statusCode,
                    "Authentication failed for blob: " + digest
            );
        }
        else if (statusCode == 403) {
            throw new RegistryAuthenticationException(
                    statusCode,
                    "Authentication failed for blob: " + digest
            );
        }
        else if (statusCode == 404) {
            throw new RegistryImageNotFoundException(
                    "Blob not found: " + digest
            );
        }
        else {
            throw new IOException(
                    "Failed to pull blob " + digest + ". Status: " + statusCode
            );
        }
    }

    @Override
    public Optional<TokenResponse> authenticate(
            String wwwAuthenticate,
            ImageReference ref,
            AuthContext auth
    )
            throws IOException {
        String challenge = wwwAuthenticate.substring(
                7
        );
        String realm = null;
        String service = null;
        String scope = null;

        Matcher matcher = WWW_AUTHENTICATE_PARAM_PATTERN.matcher(
                challenge
        );
        while (matcher.find()) {
            String key = matcher.group(
                    1
            );
            String value = matcher.group(
                    2
            );
            if ("realm".equals(
                    key
            )) {
                realm = value;
            }
            else if ("service".equals(
                    key
            )) {
                service = value;
            }
            else if ("scope".equals(
                    key
            )) {
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
        );
        if (realm.contains(
                "?"
        )) {
            tokenUrl.append(
                    "&service="
            );
        }
        else {
            tokenUrl.append(
                    "?service="
            );
        }
        tokenUrl.append(
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
                            ref.repository
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

        if (auth.username != null && auth.password != null) {
            String basicAuth = Base64.getEncoder()
                    .encodeToString(
                            (auth.username + ":" + auth.password).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
            builder.header(
                    "Authorization",
                    "Basic " + basicAuth
            );
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RegistryAuthenticationException(
                        response.statusCode(),
                        "Token request failed: " + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(
                    response.body()
            );
            String token = root.path(
                    "token"
            ).asText();

            if (token == null || token.isEmpty()) {
                return Optional.empty();
            }

            long expiresIn = root.path(
                    "expires_in"
            )
                    .asLong(
                            0
                    );
            log.info(
                    "Successfully obtained token"
            );
            return Optional.of(
                    new TokenResponse(
                            token,
                            expiresIn
                    )
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Token request interrupted",
                    e
            );
        }
    }

    private ManifestResponse processManifestResponse(
            ImageReference ref,
            AuthContext auth,
            HttpResponse<String> response
    )
            throws IOException {
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

        String body = response.body();
        if (isManifestList) {
            log.info(
                    "Received manifest list/OCI index, looking up platform-specific manifest"
            );
            String selectedDigest = selectPlatformDigest(
                    body
            );
            ImageReference subRef = new ImageReference(
                    ref.registry,
                    ref.repository,
                    null,
                    selectedDigest
            );
            return pullManifest(
                    subRef,
                    auth
            );
        }

        return parseManifest(
                body
        );
    }

    private String selectPlatformDigest(
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

            if ("linux/amd64".equals(
                    platform
            )) {
                selectedDigest = digest;
                selectedPlatform = platform;
                break;
            }

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
        return selectedDigest;
    }

    private ManifestResponse parseManifest(
            String manifestJson
    )
            throws IOException {
        JsonNode root = objectMapper.readTree(
                manifestJson
        );

        String configDigest = null;
        long configSize = 0;
        JsonNode configNode = root.path(
                "config"
        );
        if (!configNode.isMissingNode()) {
            configDigest = configNode.path(
                    "digest"
            ).asText();
            configSize = configNode.path(
                    "size"
            ).asLong();
        }

        List<String> layerDigests = new ArrayList<>();
        Map<String, Long> layerSizes = new HashMap<>();
        JsonNode layersNode = root.path(
                "layers"
        );
        if (layersNode.isArray()) {
            for (JsonNode layerNode : layersNode) {
                String digest = layerNode.path(
                        "digest"
                ).asText();
                long size = layerNode.path(
                        "size"
                ).asLong();
                layerDigests.add(
                        digest
                );
                layerSizes.put(
                        digest,
                        size
                );
            }
        }

        String digest = sha256(
                manifestJson.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        log.info(
                "Successfully pulled manifest with {} layers",
                layerDigests.size()
        );

        return new ManifestResponse(
                manifestJson,
                digest,
                configDigest,
                configSize,
                layerDigests,
                layerSizes
        );
    }

    private HttpResponse<String> sendManifestRequest(
            ImageReference ref,
            AuthContext auth,
            String manifestUrl,
            String token
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
                            MANIFEST_ACCEPT_HEADER
                    )
                    .timeout(
                            MANIFEST_TIMEOUT
                    );

            if (token != null) {
                requestBuilder.header(
                        "Authorization",
                        "Bearer " + token
                );
            }
            else {
                addRegistryAuth(
                        ref,
                        auth,
                        requestBuilder
                );
            }

            return httpClient.send(
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

    private HttpResponse<InputStream> sendBlobRequest(
            ImageReference ref,
            String digest,
            AuthContext auth,
            String blobUrl
    )
            throws IOException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    blobUrl
                            )
                    )
                    .timeout(
                            BLOB_TIMEOUT
                    );

            String cacheKey = cacheKey(
                    ref
            );
            String token = tokenCache.get(
                    cacheKey
            );
            if (token != null) {
                requestBuilder.header(
                        "Authorization",
                        "Bearer " + token
                );
            }
            else {
                addRegistryAuth(
                        ref,
                        auth,
                        requestBuilder
                );
            }

            return httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Failed to pull blob: " + digest,
                    e
            );
        }
    }

    private void addRegistryAuth(
            ImageReference ref,
            AuthContext auth,
            HttpRequest.Builder requestBuilder
    )
            throws IOException {
        if (auth.username != null && auth.password != null) {
            String basicAuth = Base64.getEncoder()
                    .encodeToString(
                            (auth.username + ":" + auth.password).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
            requestBuilder.header(
                    "Authorization",
                    "Basic " + basicAuth
            );
        }
        else if ("docker.io".equals(
                ref.registry
        )) {
            String token = getDockerHubAnonymousToken(
                    ref.repository
            );
            if (token != null) {
                cacheToken(
                        ref,
                        token
                );
                requestBuilder.header(
                        "Authorization",
                        "Bearer " + token
                );
            }
        }
    }

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

            HttpResponse<String> response = httpClient.send(
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

    private void cacheToken(
            ImageReference ref,
            String token
    ) {
        tokenCache.put(
                cacheKey(
                        ref
                ),
                token
        );
    }

    private String cacheKey(
            ImageReference ref
    ) {
        return ref.registry + ":" + ref.repository;
    }

    private String buildManifestUrl(
            ImageReference ref,
            String protocol
    ) {
        if (protocol == null || (!"http".equals(
                protocol
        ) && !"https".equals(
                protocol
        ))) {
            protocol = "https";
        }

        if ("docker.io".equals(
                ref.registry
        )) {
            return "https://registry-1.docker.io/v2/" + ref.repository + "/manifests/" + ref.reference();
        }
        return protocol + "://" + ref.registry + "/v2/" + ref.repository + "/manifests/" + ref.reference();
    }

    private String buildBlobUrl(
            ImageReference ref,
            String digest,
            String protocol
    ) {
        if (protocol == null || (!"http".equals(
                protocol
        ) && !"https".equals(
                protocol
        ))) {
            protocol = "https";
        }

        if ("docker.io".equals(
                ref.registry
        )) {
            return "https://registry-1.docker.io/v2/" + ref.repository + "/blobs/" + digest;
        }
        return protocol + "://" + ref.registry + "/v2/" + ref.repository + "/blobs/" + digest;
    }

    private String sha256(
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
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(
                        0xff & b
                );
                if (hex.length() == 1) {
                    hexString.append(
                            '0'
                    );
                }
                hexString.append(
                        hex
                );
            }
            return "sha256:" + hexString;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to calculate SHA256",
                    e
            );
        }
    }
}
