package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class RegistryResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    @BeforeAll
    public static void setup() {
        // Configure RestAssured to use the correct port
        port = 8912;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getAuthToken() {
        if (authToken == null) {
            // Get token from the token endpoint
            var response = given().queryParam(
                    "account",
                    "admin"
            )
                    .queryParam(
                            "service",
                            "registry"
                    )
                    .queryParam(
                            "scope",
                            "registry:catalog:*"
                    )
                    .auth()
                    .basic(
                            "admin",
                            "password"
                    )
                    .when()
                    .get(
                            "/v2/token"
                    );

            if (response.statusCode() == 200) {
                authToken = response.jsonPath()
                        .getString(
                                "access_token"
                        );
            }
        }
    }

    @Test
    public void testCheckVersion() {
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
                )
                .header(
                        "Docker-Distribution-API-Version",
                        "registry/2.0"
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
                )
                .contentType(
                        "application/json"
                );
    }

    @Test
    public void testCheckVersionWithDifferentMethods() {
        // The OCI spec allows various methods on this endpoint
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/"
                )
                .then()
                .statusCode(
                        200
                )
                .header(
                        "Docker-Distribution-API-Version",
                        "registry/2.0"
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .options(
                        "/v2/"
                )
                .then()
                .statusCode(
                        200
                ); // OPTIONS is allowed and returns 200 with CORS headers
    }
}
