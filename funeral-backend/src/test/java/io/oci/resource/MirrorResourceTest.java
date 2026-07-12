package io.oci.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.oci.cli.oci.DigestUtil;
import io.oci.model.ImageReference;
import io.oci.registry.client.AuthContext;
import io.oci.registry.client.ManifestResponse;
import io.oci.registry.client.RegistryAuthenticationException;
import io.oci.registry.client.RegistryClient;
import io.oci.registry.client.TokenResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(
    MirrorResourceTest.MirrorTestProfile.class
)
public class MirrorResourceTest {

    private static final String SOURCE_IMAGE = "docker.io/library/nginx:latest";

    private static final String TARGET_REPOSITORY = "offline-nginx";

    private static final String TARGET_TAG = "v1";

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

    @BeforeEach
    public void setupMock() {
        MockRegistryClientProducer.delegate = null;
    }

    @AfterAll
    public static void cleanup() throws Exception {
        if (testStoragePath != null) {
            deleteRecursively(
                    testStoragePath
            );
        }
    }

    @Test
    public void testMirrorWithoutSourceImage() {
        given().contentType(
                ContentType.URLENC
        )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "errors",
                        hasSize(
                                1
                        )
                )
                .body(
                        "errors[0].message",
                        containsString(
                                "Source image is required"
                        )
                );
    }

    @Test
    public void testMirrorWithEmptySourceImage() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        ""
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "errors",
                        hasSize(
                                1
                        )
                )
                .body(
                        "errors[0].message",
                        containsString(
                                "Source image is required"
                        )
                );
    }

    @Test
    public void testMirrorInvalidProtocol() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        "nginx:latest"
                )
                .formParam(
                        "protocol",
                        "ftp"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "errors[0].code",
                        containsString(
                                "INVALID_PROTOCOL"
                        )
                );
    }

    @Test
    public void testMirrorSuccess() throws Exception {
        byte[] config = "config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer1 = "layer1".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer2 = "layer2".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                config
        );
        String layer1Digest = DigestUtil.sha256(
                layer1
        );
        String layer2Digest = DigestUtil.sha256(
                layer2
        );

        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\","
                + "\"config\":{\"mediaType\":\"application/vnd.docker.container.image.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + config.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.docker.image.rootfs.diff.tar.gzip\",\"digest\":\""
                + layer1Digest + "\",\"size\":" + layer1.length
                + "},{\"mediaType\":\"application/vnd.docker.image.rootfs.diff.tar.gzip\",\"digest\":\"" + layer2Digest
                + "\",\"size\":" + layer2.length + "}]}";
        String manifestDigest = DigestUtil.sha256(
                manifestJson.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        ManifestResponse manifest = new ManifestResponse(
                manifestJson,
                manifestDigest,
                configDigest,
                config.length,
                List.of(
                        layer1Digest,
                        layer2Digest
                ),
                Map.of(
                        layer1Digest,
                        (long) layer1.length,
                        layer2Digest,
                        (long) layer2.length
                )
        );

        Map<String, byte[]> blobs = Map.of(
                configDigest,
                config,
                layer1Digest,
                layer1,
                layer2Digest,
                layer2
        );
        MockRegistryClientProducer.delegate = new SimpleRegistryClient(
                manifest,
                blobs
        );

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        SOURCE_IMAGE
                )
                .formParam(
                        "targetRepository",
                        TARGET_REPOSITORY
                )
                .formParam(
                        "targetTag",
                        TARGET_TAG
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
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
                        "targetRepository",
                        equalTo(
                                TARGET_REPOSITORY
                        )
                )
                .body(
                        "targetTag",
                        equalTo(
                                TARGET_TAG
                        )
                )
                .body(
                        "manifestDigest",
                        equalTo(
                                manifestDigest
                        )
                )
                .body(
                        "blobsCount",
                        equalTo(
                                3
                        )
                );

        assertNotNull(
                manifestStorage.findByRepositoryAndTag(
                        TARGET_REPOSITORY,
                        TARGET_TAG
                )
        );
        assertTrue(
                storageService.blobExists(
                        configDigest
                )
        );
        assertTrue(
                storageService.blobExists(
                        layer1Digest
                )
        );
        assertTrue(
                storageService.blobExists(
                        layer2Digest
                )
        );
        assertEquals(
                config.length,
                storageService.getBlobSize(
                        configDigest
                )
        );
        assertEquals(
                layer1.length,
                storageService.getBlobSize(
                        layer1Digest
                )
        );
        assertEquals(
                layer2.length,
                storageService.getBlobSize(
                        layer2Digest
                )
        );
    }

    @Test
    public void testMirrorManifestListSelectsLinuxAmd64() throws Exception {
        byte[] config = "amd64-config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "amd64-layer".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                config
        );
        String layerDigest = DigestUtil.sha256(
                layer
        );

        String subManifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\","
                + "\"config\":{\"mediaType\":\"application/vnd.docker.container.image.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + config.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.docker.image.rootfs.diff.tar.gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layer.length + "}]}";
        String subManifestDigest = DigestUtil.sha256(
                subManifestJson.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        ManifestResponse subManifest = new ManifestResponse(
                subManifestJson,
                subManifestDigest,
                configDigest,
                config.length,
                List.of(
                        layerDigest
                ),
                Map.of(
                        layerDigest,
                        (long) layer.length
                )
        );

        Map<String, byte[]> blobs = Map.of(
                configDigest,
                config,
                layerDigest,
                layer
        );
        MockRegistryClientProducer.delegate = new RegistryClient() {
            @Override
            public ManifestResponse pullManifest(
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                // Emulate a RegistryClient that has already resolved the linux/amd64 sub-manifest.
                return subManifest;
            }

            @Override
            public InputStream pullBlob(
                    ImageReference ref,
                    String digest,
                    AuthContext auth
            )
                    throws IOException {
                return new ByteArrayInputStream(
                        blobs.get(
                                digest
                        )
                );
            }

            @Override
            public Optional<TokenResponse> authenticate(
                    String wwwAuthenticate,
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                return Optional.empty();
            }
        };

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        SOURCE_IMAGE
                )
                .formParam(
                        "targetRepository",
                        "manifest-list-nginx"
                )
                .formParam(
                        "targetTag",
                        "linux-amd64"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "success",
                        is(
                                true
                        )
                )
                .body(
                        "manifestDigest",
                        equalTo(
                                subManifestDigest
                        )
                );

        assertEquals(
                subManifestDigest,
                manifestStorage.findByRepositoryAndTag(
                        "manifest-list-nginx",
                        "linux-amd64"
                ).digest
        );
    }

    @Test
    public void testMirrorWithBasicAuth() throws Exception {
        byte[] config = "auth-config".getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layer = "auth-layer".getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                config
        );
        String layerDigest = DigestUtil.sha256(
                layer
        );

        String manifestJson = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\","
                + "\"config\":{\"mediaType\":\"application/vnd.docker.container.image.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + config.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.docker.image.rootfs.diff.tar.gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layer.length + "}]}";
        String manifestDigest = DigestUtil.sha256(
                manifestJson.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        ManifestResponse manifest = new ManifestResponse(
                manifestJson,
                manifestDigest,
                configDigest,
                config.length,
                List.of(
                        layerDigest
                ),
                Map.of(
                        layerDigest,
                        (long) layer.length
                )
        );

        Map<String, byte[]> blobs = Map.of(
                configDigest,
                config,
                layerDigest,
                layer
        );
        MockRegistryClientProducer.delegate = new RegistryClient() {
            @Override
            public ManifestResponse pullManifest(
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                assertEquals(
                        "registry.example.com",
                        ref.registry
                );
                assertEquals(
                        "project/app",
                        ref.repository
                );
                assertEquals(
                        "1.0",
                        ref.tag
                );
                assertEquals(
                        "user",
                        auth.username
                );
                assertEquals(
                        "pass",
                        auth.password
                );
                return manifest;
            }

            @Override
            public InputStream pullBlob(
                    ImageReference ref,
                    String digest,
                    AuthContext auth
            )
                    throws IOException {
                byte[] content = blobs.get(
                        digest
                );
                if (content == null) {
                    throw new IOException(
                            "Unknown blob: " + digest
                    );
                }
                return new ByteArrayInputStream(
                        content
                );
            }

            @Override
            public Optional<TokenResponse> authenticate(
                    String wwwAuthenticate,
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                return Optional.empty();
            }
        };

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        "registry.example.com/project/app:1.0"
                )
                .formParam(
                        "targetRepository",
                        "auth-app"
                )
                .formParam(
                        "targetTag",
                        "v1"
                )
                .formParam(
                        "username",
                        "user"
                )
                .formParam(
                        "password",
                        "pass"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "success",
                        is(
                                true
                        )
                )
                .body(
                        "manifestDigest",
                        equalTo(
                                manifestDigest
                        )
                );
    }

    @Test
    public void testMirrorRegistryUnauthorized() {
        MockRegistryClientProducer.delegate = new RegistryClient() {
            @Override
            public ManifestResponse pullManifest(
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                throw new RegistryAuthenticationException(
                        401,
                        "unauthorized"
                );
            }

            @Override
            public InputStream pullBlob(
                    ImageReference ref,
                    String digest,
                    AuthContext auth
            )
                    throws IOException {
                throw new RegistryAuthenticationException(
                        401,
                        "unauthorized"
                );
            }

            @Override
            public Optional<TokenResponse> authenticate(
                    String wwwAuthenticate,
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                return Optional.empty();
            }
        };

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        "registry.example.com/project/app:1.0"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        401
                )
                .body(
                        "errors[0].code",
                        equalTo(
                                "AUTHENTICATION_FAILED"
                        )
                );
    }

    @Test
    public void testMirrorNetworkTimeout() {
        MockRegistryClientProducer.delegate = new RegistryClient() {
            @Override
            public ManifestResponse pullManifest(
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                throw new HttpTimeoutException(
                        "timeout"
                );
            }

            @Override
            public InputStream pullBlob(
                    ImageReference ref,
                    String digest,
                    AuthContext auth
            )
                    throws IOException {
                throw new HttpTimeoutException(
                        "timeout"
                );
            }

            @Override
            public Optional<TokenResponse> authenticate(
                    String wwwAuthenticate,
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                return Optional.empty();
            }
        };

        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        "registry.example.com/project/app:1.0"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        502
                )
                .body(
                        "errors[0].code",
                        equalTo(
                                "NETWORK_TIMEOUT"
                        )
                );
    }

    private static class SimpleRegistryClient implements RegistryClient {

        private final ManifestResponse manifest;

        private final Map<String, byte[]> blobs;

        SimpleRegistryClient(
                ManifestResponse manifest,
                Map<String, byte[]> blobs
        ) {
            this.manifest = manifest;
            this.blobs = blobs;
        }

        @Override
        public ManifestResponse pullManifest(
                ImageReference ref,
                AuthContext auth
        )
                throws IOException {
            return manifest;
        }

        @Override
        public InputStream pullBlob(
                ImageReference ref,
                String digest,
                AuthContext auth
        )
                throws IOException {
            byte[] content = blobs.get(
                    digest
            );
            if (content == null) {
                throw new IOException(
                        "Unknown blob: " + digest
                );
            }
            return new ByteArrayInputStream(
                    content
            );
        }

        @Override
        public Optional<TokenResponse> authenticate(
                String wwwAuthenticate,
                ImageReference ref,
                AuthContext auth
        )
                throws IOException {
            return Optional.empty();
        }
    }

    public static class MirrorTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                testStoragePath = Files.createTempDirectory(
                        "mirror-resource-test"
                );
                return Map.of(
                        "oci.storage.local-storage-path",
                        testStoragePath.toString()
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
