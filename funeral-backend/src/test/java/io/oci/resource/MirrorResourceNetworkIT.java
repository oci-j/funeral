package io.oci.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@Tag(
    "network"
)
@EnabledIfSystemProperty(
        named = "oci.test.network",
        matches = "true"
)
public class MirrorResourceNetworkIT {

    @Test
    public void testMirrorDockerHubNginx() {
        given().contentType(
                ContentType.URLENC
        )
                .formParam(
                        "sourceImage",
                        "docker.io/library/nginx:latest"
                )
                .formParam(
                        "targetRepository",
                        "network-nginx"
                )
                .formParam(
                        "targetTag",
                        "latest"
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
                                        202
                                ),
                                is(
                                        502
                                )
                        )
                )
                .contentType(
                        ContentType.JSON
                );
    }
}
