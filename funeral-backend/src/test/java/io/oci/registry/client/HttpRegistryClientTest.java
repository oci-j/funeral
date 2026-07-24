package io.oci.registry.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.oci.model.ImageReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpRegistryClientTest {

    private static final String MANIFEST_JSON = "{" + "\"schemaVersion\":2,"
            + "\"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\","
            + "\"config\":{\"digest\":\"sha256:cfg123\",\"size\":1234}," + "\"layers\":["
            + "{\"digest\":\"sha256:layer1\",\"size\":100}," + "{\"digest\":\"sha256:layer2\",\"size\":200}" + "]}";

    private static final String MANIFEST_LIST_JSON = "{" + "\"schemaVersion\":2,"
            + "\"mediaType\":\"application/vnd.docker.distribution.manifest.list.v2+json\"," + "\"manifests\":["
            + "{\"digest\":\"sha256:arm64manifest\",\"platform\":{\"os\":\"linux\",\"architecture\":\"arm64\"}},"
            + "{\"digest\":\"sha256:amd64manifest\",\"platform\":{\"os\":\"linux\",\"architecture\":\"amd64\"}}" + "]}";

    private HttpServer server;

    private HttpRegistryClient client;

    private ImageReference ref;

    private AuthContext noAuth;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                ),
                0
        );
        server.start();
        client = new HttpRegistryClient();
        String registry = "127.0.0.1:" + server.getAddress().getPort();
        ref = new ImageReference(
                registry,
                "library/test",
                "latest",
                null
        );
        noAuth = new AuthContext(
                null,
                null,
                "http",
                false
        );
    }

    @AfterEach
    public void tearDown() {
        server.stop(
                0
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String contentType,
            String body
    )
            throws IOException {
        byte[] bytes = body.getBytes(
                StandardCharsets.UTF_8
        );
        if (contentType != null) {
            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            contentType
                    );
        }
        exchange.sendResponseHeaders(
                status,
                status == 404 || status == 401 || status == 403 || status == 500 ? bytes.length : bytes.length
        );
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(
                    bytes
            );
        }
    }

    @Test
    public void testPullManifestSuccess() throws Exception {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.docker.distribution.manifest.v2+json",
                        MANIFEST_JSON
                )
        );

        ManifestResponse response = client.pullManifest(
                ref,
                noAuth
        );

        assertEquals(
                MANIFEST_JSON,
                response.json
        );
        assertEquals(
                "sha256:cfg123",
                response.configDigest
        );
        assertEquals(
                1234,
                response.configSize
        );
        assertEquals(
                List.of(
                        "sha256:layer1",
                        "sha256:layer2"
                ),
                response.layerDigests
        );
        assertEquals(
                100L,
                response.layerSizes.get(
                        "sha256:layer1"
                )
        );
        assertTrue(
                response.digest.startsWith(
                        "sha256:"
                )
        );
    }

    @Test
    public void testPullManifestListSelectsAmd64() throws Exception {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.docker.distribution.manifest.list.v2+json",
                        MANIFEST_LIST_JSON
                )
        );
        server.createContext(
                "/v2/library/test/manifests/sha256:amd64manifest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.docker.distribution.manifest.v2+json",
                        MANIFEST_JSON
                )
        );

        ManifestResponse response = client.pullManifest(
                ref,
                noAuth
        );

        assertEquals(
                "sha256:cfg123",
                response.configDigest
        );
        assertEquals(
                2,
                response.layerDigests.size()
        );
    }

    @Test
    public void testPullManifestListFallsBackToFirstPlatform() throws Exception {
        String listOnlyArm = "{" + "\"schemaVersion\":2," + "\"mediaType\":\"application/vnd.oci.image.index.v1+json\","
                + "\"manifests\":["
                + "{\"digest\":\"sha256:arm64manifest\",\"platform\":{\"os\":\"linux\",\"architecture\":\"arm64\"}}"
                + "]}";
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.oci.image.index.v1+json",
                        listOnlyArm
                )
        );
        server.createContext(
                "/v2/library/test/manifests/sha256:arm64manifest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.oci.image.manifest.v1+json",
                        MANIFEST_JSON
                )
        );

        ManifestResponse response = client.pullManifest(
                ref,
                noAuth
        );

        assertNotNull(
                response
        );
        assertEquals(
                "sha256:cfg123",
                response.configDigest
        );
    }

    @Test
    public void testPullManifestListEmpty() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        200,
                        "application/vnd.docker.distribution.manifest.list.v2+json",
                        "{\"manifests\":[]}"
                )
        );

        assertThrows(
                IOException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testPullManifestNotFound() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        404,
                        null,
                        "{\"errors\":[]}"
                )
        );

        assertThrows(
                RegistryImageNotFoundException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testPullManifestServerError() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        500,
                        null,
                        "boom"
                )
        );

        IOException e = assertThrows(
                IOException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
        assertTrue(
                e.getMessage()
                        .contains(
                                "500"
                        )
        );
    }

    @Test
    public void testPullManifestUnauthorizedWithoutChallenge() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> respond(
                        exchange,
                        401,
                        null,
                        "unauthorized"
                )
        );

        assertThrows(
                RegistryAuthenticationException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testPullManifestBearerChallengeFlow() throws Exception {
        AtomicInteger manifestCalls = new AtomicInteger();
        List<String> manifestAuthHeaders = new ArrayList<>();
        List<String> tokenAuthHeaders = new ArrayList<>();

        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> {
                    manifestCalls.incrementAndGet();
                    String authHeader = exchange.getRequestHeaders()
                            .getFirst(
                                    "Authorization"
                            );
                    manifestAuthHeaders.add(
                            authHeader
                    );
                    if (authHeader == null || !authHeader.startsWith(
                            "Bearer "
                    )) {
                        exchange.getResponseHeaders()
                                .set(
                                        "WWW-Authenticate",
                                        "Bearer realm=\"http://" + ref.registry + "/token\","
                                                + "service=\"test-service\"," + "scope=\"repository:library/test:pull\""
                                );
                        respond(
                                exchange,
                                401,
                                null,
                                "unauthorized"
                        );
                    }
                    else {
                        respond(
                                exchange,
                                200,
                                "application/vnd.docker.distribution.manifest.v2+json",
                                MANIFEST_JSON
                        );
                    }
                }
        );
        server.createContext(
                "/token",
                exchange -> {
                    tokenAuthHeaders.add(
                            exchange.getRequestHeaders()
                                    .getFirst(
                                            "Authorization"
                                    )
                    );
                    respond(
                            exchange,
                            200,
                            "application/json",
                            "{\"token\":\"test-token-abc\",\"expires_in\":300}"
                    );
                }
        );

        AuthContext basicAuth = new AuthContext(
                "user",
                "pass",
                "http",
                false
        );
        ManifestResponse response = client.pullManifest(
                ref,
                basicAuth
        );

        assertEquals(
                "sha256:cfg123",
                response.configDigest
        );
        assertEquals(
                2,
                manifestCalls.get()
        );
        assertEquals(
                "Bearer test-token-abc",
                manifestAuthHeaders.get(
                        1
                )
        );
        assertTrue(
                tokenAuthHeaders.get(
                        0
                )
                        .startsWith(
                                "Basic "
                        )
        );
    }

    @Test
    public void testPullManifestBearerRetryForbidden() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> {
                    String authHeader = exchange.getRequestHeaders()
                            .getFirst(
                                    "Authorization"
                            );
                    if (authHeader == null) {
                        exchange.getResponseHeaders()
                                .set(
                                        "WWW-Authenticate",
                                        "Bearer realm=\"http://" + ref.registry + "/token\""
                                );
                        respond(
                                exchange,
                                401,
                                null,
                                "unauthorized"
                        );
                    }
                    else {
                        respond(
                                exchange,
                                403,
                                null,
                                "forbidden"
                        );
                    }
                }
        );
        server.createContext(
                "/token",
                exchange -> respond(
                        exchange,
                        200,
                        "application/json",
                        "{\"token\":\"tok\"}"
                )
        );

        assertThrows(
                RegistryAuthenticationException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testPullManifestBearerRetryNotFound() {
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> {
                    String authHeader = exchange.getRequestHeaders()
                            .getFirst(
                                    "Authorization"
                            );
                    if (authHeader == null) {
                        exchange.getResponseHeaders()
                                .set(
                                        "WWW-Authenticate",
                                        "Bearer realm=\"http://" + ref.registry + "/token\""
                                );
                        respond(
                                exchange,
                                401,
                                null,
                                "unauthorized"
                        );
                    }
                    else {
                        respond(
                                exchange,
                                404,
                                null,
                                "not found"
                        );
                    }
                }
        );
        server.createContext(
                "/token",
                exchange -> respond(
                        exchange,
                        200,
                        "application/json",
                        "{\"token\":\"tok\"}"
                )
        );

        assertThrows(
                RegistryImageNotFoundException.class,
                () -> client.pullManifest(
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testPullBlobSuccess() throws Exception {
        server.createContext(
                "/v2/library/test/blobs/sha256:abc",
                exchange -> respond(
                        exchange,
                        200,
                        "application/octet-stream",
                        "blob-content"
                )
        );

        try (
                InputStream in = client.pullBlob(
                        ref,
                        "sha256:abc",
                        noAuth
                )) {
            assertEquals(
                    "blob-content",
                    new String(
                            in.readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
        }
    }

    @Test
    public void testPullBlobNotFound() {
        server.createContext(
                "/v2/library/test/blobs/sha256:missing",
                exchange -> respond(
                        exchange,
                        404,
                        null,
                        "not found"
                )
        );

        assertThrows(
                RegistryImageNotFoundException.class,
                () -> client.pullBlob(
                        ref,
                        "sha256:missing",
                        noAuth
                )
        );
    }

    @Test
    public void testPullBlobForbidden() {
        server.createContext(
                "/v2/library/test/blobs/sha256:denied",
                exchange -> respond(
                        exchange,
                        403,
                        null,
                        "forbidden"
                )
        );

        assertThrows(
                RegistryAuthenticationException.class,
                () -> client.pullBlob(
                        ref,
                        "sha256:denied",
                        noAuth
                )
        );
    }

    @Test
    public void testPullBlobUnauthorizedNoCachedToken() {
        server.createContext(
                "/v2/library/test/blobs/sha256:unauth",
                exchange -> respond(
                        exchange,
                        401,
                        null,
                        "unauthorized"
                )
        );

        assertThrows(
                RegistryAuthenticationException.class,
                () -> client.pullBlob(
                        ref,
                        "sha256:unauth",
                        noAuth
                )
        );
    }

    @Test
    public void testPullBlobUnauthorizedWithCachedTokenRetries() throws Exception {
        AtomicInteger blobCalls = new AtomicInteger();

        // First authenticate through the manifest flow to populate token cache
        server.createContext(
                "/v2/library/test/manifests/latest",
                exchange -> {
                    String authHeader = exchange.getRequestHeaders()
                            .getFirst(
                                    "Authorization"
                            );
                    if (authHeader == null) {
                        exchange.getResponseHeaders()
                                .set(
                                        "WWW-Authenticate",
                                        "Bearer realm=\"http://" + ref.registry + "/token\""
                                );
                        respond(
                                exchange,
                                401,
                                null,
                                "unauthorized"
                        );
                    }
                    else {
                        respond(
                                exchange,
                                200,
                                "application/vnd.docker.distribution.manifest.v2+json",
                                MANIFEST_JSON
                        );
                    }
                }
        );
        server.createContext(
                "/token",
                exchange -> respond(
                        exchange,
                        200,
                        "application/json",
                        "{\"token\":\"cached-token\"}"
                )
        );
        server.createContext(
                "/v2/library/test/blobs/sha256:retry",
                exchange -> {
                    int call = blobCalls.incrementAndGet();
                    if (call == 1) {
                        // Cached token rejected
                        respond(
                                exchange,
                                401,
                                null,
                                "expired"
                        );
                    }
                    else {
                        // Retry without token succeeds
                        respond(
                                exchange,
                                200,
                                "application/octet-stream",
                                "retried-content"
                        );
                    }
                }
        );

        client.pullManifest(
                ref,
                noAuth
        );

        try (
                InputStream in = client.pullBlob(
                        ref,
                        "sha256:retry",
                        noAuth
                )) {
            assertEquals(
                    "retried-content",
                    new String(
                            in.readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
        }
        assertEquals(
                2,
                blobCalls.get()
        );
    }

    @Test
    public void testAuthenticateMissingRealm() {
        assertThrows(
                IOException.class,
                () -> client.authenticate(
                        "Bearer service=\"svc\"",
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testAuthenticateTokenEndpointError() {
        server.createContext(
                "/token",
                exchange -> respond(
                        exchange,
                        500,
                        null,
                        "boom"
                )
        );

        assertThrows(
                RegistryAuthenticationException.class,
                () -> client.authenticate(
                        "Bearer realm=\"http://" + ref.registry + "/token\"",
                        ref,
                        noAuth
                )
        );
    }

    @Test
    public void testAuthenticateEmptyToken() throws Exception {
        server.createContext(
                "/token",
                exchange -> respond(
                        exchange,
                        200,
                        "application/json",
                        "{\"token\":\"\"}"
                )
        );

        Optional<TokenResponse> result = client.authenticate(
                "Bearer realm=\"http://" + ref.registry + "/token\"",
                ref,
                noAuth
        );

        assertFalse(
                result.isPresent()
        );
    }

    @Test
    public void testAuthenticateRealmWithQueryString() throws Exception {
        List<String> queryStrings = new ArrayList<>();
        server.createContext(
                "/token",
                exchange -> {
                    queryStrings.add(
                            exchange.getRequestURI().getQuery()
                    );
                    respond(
                            exchange,
                            200,
                            "application/json",
                            "{\"token\":\"tok\",\"expires_in\":60}"
                    );
                }
        );

        Optional<TokenResponse> result = client.authenticate(
                "Bearer realm=\"http://" + ref.registry + "/token?foo=bar\",service=\"svc\"",
                ref,
                noAuth
        );

        assertTrue(
                result.isPresent()
        );
        assertEquals(
                "tok",
                result.get().token
        );
        assertEquals(
                60,
                result.get().expiresIn
        );
        String query = queryStrings.get(
                0
        );
        assertTrue(
                query.contains(
                        "foo=bar&service=svc"
                )
        );
        // No scope in challenge -> default repository pull scope is appended
        assertTrue(
                query.contains(
                        "scope=repository:library/test:pull"
                )
        );
    }
}
