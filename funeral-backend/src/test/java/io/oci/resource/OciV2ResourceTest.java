package io.oci.resource;

import java.security.MessageDigest;
import java.util.HexFormat;

import io.oci.resource.handler.AuthTestHelper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class OciV2ResourceTest {

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

    private String sha256(
            String content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return "sha256:" + HexFormat.of()
                .formatHex(
                        md.digest(
                                content.getBytes()
                        )
                );
    }

    @Test
    public void testGetVersion() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/"
                )
                .then()
                .statusCode(
                        200
                );
    }

    @Test
    public void testListRepositories() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/repositories"
                )
                .then()
                .statusCode(
                        200
                );
    }

    @Test
    public void testGetToken() {
        given().auth()
                .preemptive()
                .basic(
                        "admin",
                        "password"
                )
                .queryParam(
                        "service",
                        "registry"
                )
                .queryParam(
                        "scope",
                        "registry:catalog:*"
                )
                .when()
                .get(
                        "/v2/token"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "access_token",
                        notNullValue()
                );
    }

    @Test
    public void testDeleteRepository() throws Exception {
        String repository = "test/repo-delete-" + System.nanoTime();
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
                        "v1.0.0"
                )
                .then()
                .statusCode(
                        201
                );

        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}",
                        repository
                )
                .then()
                .statusCode(
                        202
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        "v1.0.0"
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testDeleteRepositoryNotFound() {
        String repository = "test/not-exist-" + System.nanoTime();
        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}",
                        repository
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathGet() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/unknown-path-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathHead() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/unknown-head-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathPost() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{}"
                )
                .when()
                .post(
                        "/v2/unknown-post-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }
}
