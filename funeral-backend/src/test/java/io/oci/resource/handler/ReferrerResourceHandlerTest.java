package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ReferrerResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    @BeforeAll
    public static void setup() {
        port = 8081;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getAuthToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
    }

    @Test
    public void testGetReferrers() {
        String repository = "test/repo";
        String digest = "sha256:test123456789abcdef";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/referrers/{digest}",
                        repository,
                        digest
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        anyOf(
                                is(
                                        "application/json"
                                ),
                                is(
                                        "application/vnd.oci.image.index.v1+json"
                                )
                        )
                )
                .body(
                        "$",
                        anyOf(
                                hasKey(
                                        "schemaVersion"
                                ),
                                hasKey(
                                        "manifests"
                                )
                        )
                );
    }

    @Test
    public void testGetReferrersWithArtifactType() {
        String repository = "test/repo";
        String digest = "sha256:test123456789abcdef";
        String artifactType = "application/vnd.oci.image.manifest.v1+json";

        given().auth()
                .oauth2(
                        authToken
                )
                .queryParam(
                        "artifactType",
                        artifactType
                )
                .when()
                .get(
                        "/v2/{name}/referrers/{digest}",
                        repository,
                        digest
                )
                .then()
                .statusCode(
                        200
                );
    }

    @Test
    public void testGetReferrersInvalidDigest() {
        String repository = "test/repo";
        String invalidDigest = "invalid-digest-format";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/referrers/{digest}",
                        repository,
                        invalidDigest
                )
                .then()
                .statusCode(
                        200
                ); // Should still return 200 with empty manifests
    }
}
