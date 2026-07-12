package io.oci.resource;

import java.util.UUID;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AdminResourceTest {

    @TestHTTPResource
    String baseUrl;

    private static String adminToken;

    @BeforeAll
    public static void setup() {
        port = 8912;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getAdminToken() {
        if (adminToken == null) {
            adminToken = getToken(
                    "admin",
                    "password",
                    "registry:catalog:*"
            );
        }
    }

    private static String getToken(
            String username,
            String password,
            String scope
    ) {
        Response response = RestAssured.given()
                .queryParam(
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

    private String uniqueUser() {
        return "user-" + UUID.randomUUID()
                .toString()
                .substring(
                        0,
                        8
                );
    }

    @Test
    public void testListUsers() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "username",
                        hasItem(
                                "admin"
                        )
                );
    }

    @Test
    public void testGetUser() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/users/admin"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "username",
                        equalTo(
                                "admin"
                        )
                );
    }

    @Test
    public void testGetUserNotFound() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/users/not-exist"
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testCreateUser() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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
                )
                .body(
                        "username",
                        equalTo(
                                username
                        )
                );
    }

    @Test
    public void testCreateUserValidation() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"username\":\"\",\"password\":\"password\"}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        400
                );
    }

    @Test
    public void testCreateUserDuplicate() {
        String username = uniqueUser();
        String body = "{\"username\":\"" + username + "\",\"password\":\"password\",\"roles\":[\"USER\"]}";
        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        body
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
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        body
                )
                .when()
                .post(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        409
                );
    }

    @Test
    public void testUpdateUser() {
        String username = uniqueUser();
        String createBody = "{\"username\":\"" + username + "\",\"password\":\"password\",\"roles\":[\"USER\"]}";
        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        createBody
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
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"email\":\"updated@example.com\"}"
                )
                .when()
                .put(
                        "/funeral_addition/admin/users/" + username
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "email",
                        equalTo(
                                "updated@example.com"
                        )
                );
    }

    @Test
    public void testUpdateUserPasswordValidation() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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

        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"password\":\"123\"}"
                )
                .when()
                .put(
                        "/funeral_addition/admin/users/" + username
                )
                .then()
                .statusCode(
                        400
                );
    }

    @Test
    public void testDeleteUser() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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

        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .delete(
                        "/funeral_addition/admin/users/" + username
                )
                .then()
                .statusCode(
                        204
                );

        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/users/" + username
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testDeleteLastAdmin() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .delete(
                        "/funeral_addition/admin/users/admin"
                )
                .then()
                .statusCode(
                        400
                );
    }

    @Test
    public void testListPermissions() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/permissions"
                )
                .then()
                .statusCode(
                        200
                );
    }

    @Test
    public void testGetUserPermissions() {
        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/permissions/admin"
                )
                .then()
                .statusCode(
                        200
                );
    }

    @Test
    public void testSetUserPermission() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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

        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"canPull\":true,\"canPush\":true}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/permissions/" + username + "/test-repo"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "canPull",
                        equalTo(
                                true
                        )
                )
                .body(
                        "canPush",
                        equalTo(
                                true
                        )
                );
    }

    @Test
    public void testDeleteUserPermission() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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

        given().auth()
                .oauth2(
                        adminToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{\"canPull\":true}"
                )
                .when()
                .post(
                        "/funeral_addition/admin/permissions/" + username + "/test-repo"
                )
                .then()
                .statusCode(
                        200
                );

        given().auth()
                .oauth2(
                        adminToken
                )
                .when()
                .delete(
                        "/funeral_addition/admin/permissions/" + username + "/test-repo"
                )
                .then()
                .statusCode(
                        204
                );
    }

    @Test
    public void testNonAdminIsForbidden() {
        String username = uniqueUser();
        given().auth()
                .oauth2(
                        adminToken
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

        String userToken = getToken(
                username,
                "password",
                "repository:test:push,pull"
        );
        given().auth()
                .oauth2(
                        userToken
                )
                .when()
                .get(
                        "/funeral_addition/admin/users"
                )
                .then()
                .statusCode(
                        403
                );
    }
}
