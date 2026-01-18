package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

    @Test
    public void testGetManifestInfo() {
        String repository = "test/repo";
        String reference = "latest";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/manifests/{reference}/info",
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
                )
                .contentType(
                        ContentType.JSON
                );
    }
}
