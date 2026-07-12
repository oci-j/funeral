package io.oci.resource;

import io.oci.resource.handler.AuthTestHelper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AuthenticationFilterTest {

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
    public void getTokens() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
        if (pushToken == null) {
            pushToken = AuthTestHelper.getPushToken();
        }
    }

    private String getToken(
            String username,
            String password,
            String scope
    ) {
        io.restassured.response.Response response = given().queryParam(
                "account",
                username
        )
                .queryParam(
                        "service",
                        "registry"
                )
                .queryParam(
                        "scope",
                        scope
                )
                .auth()
                .preemptive()
                .basic(
                        username,
                        password
                )
                .when()
                .get(
                        "/v2/token"
                );
        if (response.statusCode() == 200) {
            return response.jsonPath()
                    .getString(
                            "access_token"
                    );
        }
        return null;
    }

    private String createPullOnlyUser() {
        String username = "pull-user-" + System.nanoTime();
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"username\":\"" + username
                                + "\",\"password\":\"password\",\"roles\":[\"USER\"],\"enabled\":true}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        201
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"canPull\":true,\"canPush\":false}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/permissions/" + username + "/test-auth-repo"
                )
                .then()
                .statusCode(
                        200
                );

        return username;
    }

    @Test
    public void testMissingAuthReturns401() {
        given().when()
                .get(
                        "/v2/repositories"
                )
                .then()
                .statusCode(
                        401
                )
                .header(
                        "WWW-Authenticate",
                        notNullValue()
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "errors.code",
                        hasItem(
                                "UNAUTHORIZED"
                        )
                );
    }

    @Test
    public void testWwwAuthenticateScope() {
        given().when()
                .get(
                        "/v2/test-repo/manifests/latest"
                )
                .then()
                .statusCode(
                        401
                )
                .header(
                        "WWW-Authenticate",
                        containsString(
                                "scope=\"repository:test-repo:pull,push\""
                        )
                );
    }

    @Test
    public void testAnonymousTokenAllowsPull() {
        String anonymousToken = given().queryParam(
                "service",
                "registry"
        )
                .queryParam(
                        "scope",
                        "repository:test/auth:pull"
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
                )
                .extract()
                .jsonPath()
                .getString(
                        "access_token"
                );

        given().auth()
                .oauth2(
                        anonymousToken
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
    public void testAuthenticatedPullWorks() {
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
    public void testPullOnlyTokenCannotPushV2() {
        String username = createPullOnlyUser();
        String pullOnlyToken = getToken(
                username,
                "password",
                "repository:test-auth-repo:pull"
        );

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
                        pullOnlyToken
                )
                .contentType(
                        "application/vnd.oci.image.manifest.v1+json"
                )
                .body(
                        manifestContent
                )
                .when()
                .put(
                        "/v2/test-auth-repo/manifests/v1.0.0"
                )
                .then()
                .statusCode(
                        403
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "errors.code",
                        hasItem(
                                "DENIED"
                        )
                );
    }

    @Test
    public void testPullOnlyTokenCanPullV2() {
        String username = createPullOnlyUser();
        String pullOnlyToken = getToken(
                username,
                "password",
                "repository:test-auth-repo:pull"
        );

        given().auth()
                .oauth2(
                        pullOnlyToken
                )
                .when()
                .get(
                        "/v2/test-auth-repo/tags/list"
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
    public void testPushTokenCanPushV2() {
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
                        "/v2/test-auth-push-repo/manifests/v1.0.0"
                )
                .then()
                .statusCode(
                        201
                );
    }

    @Test
    public void testWildcardActionAllowsAdminWrite() {
        String username = "wildcard-test-" + System.nanoTime();
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"username\":\"" + username + "\",\"password\":\"password\",\"roles\":[\"USER\"]}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        201
                );
    }

    @Test
    public void testPullOnlyTokenCannotWriteAdmin() {
        String pullOnlyAdminToken = AuthTestHelper.getTokenForScope(
                "registry:catalog:pull"
        );
        String username = "should-fail-" + System.nanoTime();
        given().auth()
                .oauth2(
                        pullOnlyAdminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"username\":\"" + username + "\",\"password\":\"password\",\"roles\":[\"USER\"]}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        403
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "errors.code",
                        hasItem(
                                "DENIED"
                        )
                );
    }

    @Test
    public void testInvalidTokenReturnsUnauthorized() {
        given().auth()
                .oauth2(
                        "invalid-token"
                )
                .when()
                .get(
                        "/v2/repositories"
                )
                .then()
                .statusCode(
                        401
                );
    }
}
