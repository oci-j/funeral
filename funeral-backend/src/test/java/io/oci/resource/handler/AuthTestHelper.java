package io.oci.resource.handler;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class AuthTestHelper {

    private static String authToken = null;

    private static String pushToken = null;

    public static String getAuthToken() {
        if (authToken == null) {
            authToken = getTokenForScope(
                    "registry:catalog:*"
            );
        }
        return authToken;
    }

    public static String getPushToken() {
        if (pushToken == null) {
            pushToken = getTokenForScope(
                    "repository:test/repo:pull,push"
            );
        }
        return pushToken;
    }

    public static String getTokenForScope(
            String scope
    ) {
        // Get token from the token endpoint
        Response response = RestAssured.given()
                .queryParam(
                        "account",
                        "admin"
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
                .basic(
                        "admin",
                        "password"
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
}
