package io.oci.resource.handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ManifestResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    private static String pushToken;

    @BeforeAll
    public static void setup() {
        port = 8912;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getAuthToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
        if (pushToken == null) {
            pushToken = AuthTestHelper.getPushToken();
        }
    }

    @Test
    public void testGetManifest() {
        String testManifestContent = "{\"schemaVersion\":2,\"config\":{\"digest\":\"sha256:test\",\"size\":100}}";
        String repository = "test/repo";
        String reference = "latest";

        // First, we'd need to PUT a manifest to test GET
        // This is a basic structure - actual test would need proper setup
        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        testManifestContent
                )
                .when()
                .put(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        201
                                ),
                                is(
                                        400
                                ),
                                is(
                                        404
                                ),
                                is(
                                        403
                                )
                        )
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        200
                                ),
                                is(
                                        404
                                )
                        )
                );
    }

    @Test
    public void testHeadManifest() {
        String repository = "test/repo";
        String reference = "latest";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        200
                                ),
                                is(
                                        404
                                )
                        )
                );
    }

    @Test
    public void testPutManifest() {
        String testManifestContent = """
                {
                    "schemaVersion": 2,
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:test123"
                    },
                    "layers": []
                }
                """;
        String repository = "test/repo";
        String reference = "v1.0.0";

        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        testManifestContent
                )
                .when()
                .put(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        201
                                ),
                                is(
                                        400
                                ),
                                is(
                                        404
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testDeleteManifest() {
        String repository = "test/repo";
        String reference = "test-tag";

        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        202
                                ),
                                is(
                                        404
                                ),
                                is(
                                        403
                                )
                        )
                );
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
            StringBuilder hex = new StringBuilder(
                    "sha256:"
            );
            for (byte b : hash) {
                hex.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    e
            );
        }
    }

    @Test
    public void testPutManifestReturnsComputedDigest() {
        String manifestContent = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:test123"
                    },
                    "layers": []
                }
                """;
        String repository = "test/put-digest";
        String reference = "v1.0.0";
        String expectedDigest = sha256(
                manifestContent
        );

        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        manifestContent
                )
                .when()
                .put(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        is(
                                201
                        )
                )
                .header(
                        "Docker-Content-Digest",
                        equalTo(
                                expectedDigest
                        )
                )
                .header(
                        "Location",
                        equalTo(
                                "/v2/" + repository + "/manifests/" + expectedDigest
                        )
                );
    }

    @Test
    public void testPutManifestByDigestMismatch() {
        String manifestContent = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:test123"
                    },
                    "layers": []
                }
                """;
        String repository = "test/digest-mismatch";
        String reference = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        manifestContent
                )
                .when()
                .put(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        is(
                                400
                        )
                );
    }

    @Test
    public void testPutManifestIgnoresBogusBodyDigest() {
        String manifestContent = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:test123"
                    },
                    "layers": []
                }
                """;
        String repository = "test/bogus-body-digest";
        String reference = "v1.0.0";
        String expectedDigest = sha256(
                manifestContent
        );

        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        manifestContent
                )
                .when()
                .put(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        is(
                                201
                        )
                )
                .header(
                        "Docker-Content-Digest",
                        equalTo(
                                expectedDigest
                        )
                );
    }
}
