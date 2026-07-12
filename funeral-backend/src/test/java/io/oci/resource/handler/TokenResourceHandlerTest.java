package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class TokenResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    @BeforeAll
    public static void setup() {
        port = 8912;
        baseURI = "http://localhost";
    }

    @Test
    public void testPostTokenWithFormBody() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .body(
                        "grant_type=password&username=admin&password=password&scope=repository:test:pull,push&service=funeral-registry"
                )
                .when()
                .post(
                        "/v2/token"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "access_token",
                        notNullValue()
                )
                .body(
                        "token_type",
                        equalTo(
                                "Bearer"
                        )
                )
                .body(
                        "expires_in",
                        greaterThan(
                                0
                        )
                );
    }

    @Test
    public void testPostTokenWithAccountOverride() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .queryParam(
                        "account",
                        "admin"
                )
                .body(
                        "grant_type=password&username=admin&password=password&scope=registry:catalog:*&service=funeral-registry"
                )
                .when()
                .post(
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
    public void testPostTokenWithInvalidCredentials() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .body(
                        "grant_type=password&username=admin&password=wrong&scope=repository:test:pull,push&service=funeral-registry"
                )
                .when()
                .post(
                        "/v2/token"
                )
                .then()
                .statusCode(
                        401
                );
    }

    @Test
    public void testPostTokenWithRepositoryScopeCheck() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .body(
                        "grant_type=password&username=admin&password=password&scope=repository:unknown-repo:pull,push&service=funeral-registry"
                )
                .when()
                .post(
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
    public void testPostTokenWithNoScope() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .body(
                        "grant_type=password&username=admin&password=password&service=funeral-registry"
                )
                .when()
                .post(
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
    public void testPostTokenWithInvalidBodyFallsBackToAnonymous() {
        given().contentType(
                "application/x-www-form-urlencoded"
        )
                .body(
                        "not-a-valid-form"
                )
                .when()
                .post(
                        "/v2/token"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "access_token",
                        notNullValue()
                )
                .body(
                        "token_type",
                        equalTo(
                                "Bearer"
                        )
                );
    }
}
