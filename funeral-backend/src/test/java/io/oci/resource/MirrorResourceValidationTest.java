package io.oci.resource;

import io.oci.resource.handler.AuthTestHelper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class MirrorResourceValidationTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    @BeforeAll
    public static void setup() {
        port = 8912;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
    }

    @Test
    public void testEmptySourceImage() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/x-www-form-urlencoded"
                )
                .body(
                        "sourceImage="
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors.code",
                        hasItem(
                                "BAD_REQUEST"
                        )
                );
    }

    @Test
    public void testInvalidSourceImageFormat() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/x-www-form-urlencoded"
                )
                .body(
                        "sourceImage=not a valid image reference"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors.code",
                        hasItem(
                                "INVALID_IMAGE_FORMAT"
                        )
                );
    }

    @Test
    public void testInvalidProtocol() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/x-www-form-urlencoded"
                )
                .body(
                        "sourceImage=docker.io/library/nginx:latest&protocol=ftp"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors.code",
                        hasItem(
                                "INVALID_PROTOCOL"
                        )
                );
    }

    @Test
    public void testMissingSourceImageParam() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/x-www-form-urlencoded"
                )
                .body(
                        "targetRepository=nginx"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        400
                );
    }

    @Test
    @Disabled(
        "Requires external network access to Docker Hub"
    )
    public void testDefaultProtocolAndTarget() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/x-www-form-urlencoded"
                )
                .body(
                        "sourceImage=docker.io/library/nginx:latest"
                )
                .when()
                .post(
                        "/funeral_addition/mirror/pull"
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        200
                                ),
                                is(
                                        201
                                ),
                                is(
                                        202
                                ),
                                is(
                                        500
                                ),
                                is(
                                        502
                                ),
                                is(
                                        503
                                )
                        )
                );
    }
}
