package io.oci.resource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpServer;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
import io.oci.service.ManifestStorage;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(
    MirrorHelmResourceTest.MirrorHelmTestProfile.class
)
public class MirrorHelmResourceTest {

    private static final String CHART_NAME = "mychart";

    private static final String VERSION = "1.0.0";

    private static final String TARGET_REPOSITORY = "offline-helm";

    private static final String TARGET_VERSION = "v1";

    private static HttpServer server;

    private static int serverPort;

    private static final Map<String, String> manifests = new HashMap<>();

    private static final Map<String, byte[]> blobs = new HashMap<>();

    private static final Map<String, byte[]> charts = new HashMap<>();

    private static Path testStoragePath;

    @Inject
    @Named(
        "storage"
    )
    AbstractStorageService storageService;

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

    @BeforeAll
    public static void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        "localhost",
                        0
                ),
                0
        );
        server.createContext(
                "/",
                exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    String method = exchange.getRequestMethod();
                    if (!"GET".equals(
                            method
                    ) && !"HEAD".equals(
                            method
                    )) {
                        exchange.sendResponseHeaders(
                                405,
                                -1
                        );
                        return;
                    }

                    Matcher manifestMatcher = Pattern.compile(
                            "/v2/([^/]+)/manifests/([^/]+)"
                    )
                            .matcher(
                                    path
                            );
                    if (manifestMatcher.matches()) {
                        String manifest = manifests.get(
                                path
                        );
                        if (manifest != null) {
                            byte[] body = manifest.getBytes(
                                    StandardCharsets.UTF_8
                            );
                            exchange.getResponseHeaders()
                                    .add(
                                            "Content-Type",
                                            "application/vnd.oci.image.manifest.v1+json"
                                    );
                            exchange.sendResponseHeaders(
                                    200,
                                    body.length
                            );
                            exchange.getResponseBody()
                                    .write(
                                            body
                                    );
                            exchange.close();
                            return;
                        }
                    }

                    Matcher blobMatcher = Pattern.compile(
                            "/v2/([^/]+)/blobs/([^/]+)"
                    )
                            .matcher(
                                    path
                            );
                    if (blobMatcher.matches()) {
                        String digest = blobMatcher.group(
                                2
                        );
                        byte[] data = blobs.get(
                                digest
                        );
                        if (data != null) {
                            exchange.sendResponseHeaders(
                                    200,
                                    data.length
                            );
                            exchange.getResponseBody()
                                    .write(
                                            data
                                    );
                            exchange.close();
                            return;
                        }
                    }

                    Matcher chartMatcher = Pattern.compile(
                            "/charts/([^/]+)-([^/]+)\\.tgz"
                    )
                            .matcher(
                                    path
                            );
                    if (chartMatcher.matches()) {
                        byte[] data = charts.get(
                                path
                        );
                        if (data != null) {
                            exchange.getResponseHeaders()
                                    .add(
                                            "Content-Type",
                                            "application/x-gzip"
                                    );
                            exchange.sendResponseHeaders(
                                    200,
                                    data.length
                            );
                            exchange.getResponseBody()
                                    .write(
                                            data
                                    );
                            exchange.close();
                            return;
                        }
                    }

                    exchange.sendResponseHeaders(
                            404,
                            -1
                    );
                }
        );
        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (server != null) {
            server.stop(
                    0
            );
        }
        if (testStoragePath != null) {
            deleteRecursively(
                    testStoragePath
            );
        }
    }

    @BeforeEach
    public void resetServer() {
        manifests.clear();
        blobs.clear();
        charts.clear();
    }

    @Test
    public void testMirrorOCIHelmChart() throws Exception {
        byte[] config = "helm config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "helm chart tarball".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = sha256(
                config
        );
        String layerDigest = sha256(
                layer
        );
        blobs.put(
                configDigest,
                config
        );
        blobs.put(
                layerDigest,
                layer
        );

        String manifestJson = "{\"schemaVersion\":2," + "\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\","
                + "\"artifactType\":\"application/vnd.cncf.helm.chart.v1+json\","
                + "\"config\":{\"mediaType\":\"application/vnd.cncf.helm.config.v1+json\",\"digest\":\"" + configDigest
                + "\",\"size\":" + config.length + "},"
                + "\"layers\":[{\"mediaType\":\"application/vnd.cncf.helm.chart.content.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layer.length + "}]}";
        String manifestDigest = sha256(
                manifestJson.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        manifests.put(
                "/v2/" + CHART_NAME + "/manifests/" + VERSION,
                manifestJson
        );

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceRepo",
                        "localhost:" + serverPort
                )
                .formParam(
                        "chartName",
                        CHART_NAME
                )
                .formParam(
                        "version",
                        VERSION
                )
                .formParam(
                        "targetRepository",
                        TARGET_REPOSITORY
                )
                .formParam(
                        "targetVersion",
                        TARGET_VERSION
                )
                .formParam(
                        "format",
                        "oci"
                )
                .formParam(
                        "protocol",
                        "http"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/helm/pull"
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "success",
                        is(
                                true
                        )
                )
                .body(
                        "format",
                        equalTo(
                                "oci"
                        )
                )
                .body(
                        "chart",
                        equalTo(
                                CHART_NAME
                        )
                )
                .body(
                        "version",
                        equalTo(
                                VERSION
                        )
                )
                .body(
                        "targetChart",
                        equalTo(
                                TARGET_REPOSITORY
                        )
                )
                .body(
                        "targetVersion",
                        equalTo(
                                TARGET_VERSION
                        )
                )
                .body(
                        "digest",
                        equalTo(
                                manifestDigest
                        )
                )
                .body(
                        "blobsCount",
                        equalTo(
                                2
                        )
                );

        assertNotNull(
                manifestStorage.findByRepositoryAndTag(
                        TARGET_REPOSITORY,
                        TARGET_VERSION
                )
        );
        assertEquals(
                manifestDigest,
                manifestStorage.findByRepositoryAndTag(
                        TARGET_REPOSITORY,
                        TARGET_VERSION
                ).digest
        );
        assertNotNull(
                blobStorage.findByDigest(
                        configDigest
                )
        );
        assertNotNull(
                blobStorage.findByDigest(
                        layerDigest
                )
        );
        assertTrue(
                storageService.blobExists(
                        configDigest
                )
        );
        assertTrue(
                storageService.blobExists(
                        layerDigest
                )
        );
    }

    @Test
    public void testMirrorChartmuseumHelmChart() throws Exception {
        byte[] chartData = gzip(
                "chart payload"
        );
        String layerDigest = sha256(
                chartData
        );
        charts.put(
                "/charts/" + CHART_NAME + "-" + VERSION + ".tgz",
                chartData
        );

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceRepo",
                        "http://localhost:" + serverPort
                )
                .formParam(
                        "chartName",
                        CHART_NAME
                )
                .formParam(
                        "version",
                        VERSION
                )
                .formParam(
                        "targetRepository",
                        TARGET_REPOSITORY
                )
                .formParam(
                        "targetVersion",
                        TARGET_VERSION
                )
                .formParam(
                        "format",
                        "chartmuseum"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/helm/pull"
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "success",
                        is(
                                true
                        )
                )
                .body(
                        "format",
                        equalTo(
                                "chartmuseum"
                        )
                )
                .body(
                        "targetChart",
                        equalTo(
                                TARGET_REPOSITORY
                        )
                )
                .body(
                        "targetVersion",
                        equalTo(
                                TARGET_VERSION
                        )
                )
                .body(
                        "blobsCount",
                        equalTo(
                                2
                        )
                );

        assertNotNull(
                manifestStorage.findByRepositoryAndTag(
                        TARGET_REPOSITORY,
                        TARGET_VERSION
                )
        );
        assertTrue(
                storageService.blobExists(
                        layerDigest
                )
        );
    }

    @Test
    public void testMirrorMissingChartName() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceRepo",
                        "localhost:" + serverPort
                )
                .when()
                .post(
                        "/funeral_addition/mirror/helm/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors[0].message",
                        containsString(
                                "Chart name is required"
                        )
                );
    }

    @Test
    public void testMirrorMissingSourceRepo() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "chartName",
                        CHART_NAME
                )
                .when()
                .post(
                        "/funeral_addition/mirror/helm/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors[0].message",
                        containsString(
                                "Source repository is required"
                        )
                );
    }

    @Test
    public void testMirrorUnsupportedFormat() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceRepo",
                        "localhost:" + serverPort
                )
                .formParam(
                        "chartName",
                        CHART_NAME
                )
                .formParam(
                        "version",
                        VERSION
                )
                .formParam(
                        "format",
                        "unknown"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/helm/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors[0].message",
                        containsString(
                                "Unsupported chart format"
                        )
                );
    }

    private static String sha256(
            byte[] content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return "sha256:" + HexFormat.of()
                .formatHex(
                        md.digest(
                                content
                        )
                );
    }

    private static String sha256(
            String content
    )
            throws Exception {
        return sha256(
                content.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private static byte[] gzip(
            String content
    )
            throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (
                GZIPOutputStream gzos = new GZIPOutputStream(
                        baos
                )) {
            gzos.write(
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }
        return baos.toByteArray();
    }

    public static class MirrorHelmTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                testStoragePath = Files.createTempDirectory(
                        "mirror-helm-resource-test"
                );
                return Map.of(
                        "oci.storage.local-storage-path",
                        testStoragePath.toString(),
                        "quarkus.devservices.enabled",
                        "false"
                );
            }
            catch (IOException e) {
                throw new RuntimeException(
                        e
                );
            }
        }
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
        try (
                var stream = Files.walk(
                        path
                )) {
            stream.sorted(
                    (
                            a,
                            b
                    ) -> -a.compareTo(
                            b
                    )
            )
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(
                                            p
                                    );
                                }
                                catch (IOException e) {
                                    throw new RuntimeException(
                                            e
                                    );
                                }
                            }
                    );
        }
    }
}
