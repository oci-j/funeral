package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class TagResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

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
    }

    @Test
    public void testListTags() {
        String repository = "test/repo";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        repository
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
                        "application/json"
                )
                .body(
                        "$",
                        anyOf(
                                hasKey(
                                        "name"
                                ),
                                hasKey(
                                        "tags"
                                ),
                                hasKey(
                                        "errors"
                                )
                        )
                );
    }

    @Test
    public void testListTagsWithLimit() {
        String repository = "test/repo";
        int limit = 10;

        given().auth()
                .oauth2(
                        authToken
                )
                .queryParam(
                        "n",
                        limit
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        repository
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
    public void testListTagsWithPagination() {
        String repository = "test/repo";
        String lastTag = "v1.0.0";

        given().auth()
                .oauth2(
                        authToken
                )
                .queryParam(
                        "n",
                        10
                )
                .queryParam(
                        "last",
                        lastTag
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        repository
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
    public void testListTagsInvalidRepository() {
        String invalidRepository = "";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        invalidRepository
                )
                .then()
                .statusCode(
                        404
                );
    }
}
