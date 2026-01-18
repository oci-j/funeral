package io.oci.resource;

import io.oci.dto.HealthCheckResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class HealthCheckResourceTest {

    @Test
    public void testHealthEndpoint() {
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", is("UP"))
            .body("services", notNullValue())
            .body("services.mongodb.status", is("UP"))
            .body("services.storage.status", is("UP"))
            .body("version", notNullValue())
            .body("uptime", notNullValue());
    }

    @Test
    public void testLivenessEndpoint() {
        given()
            .when()
            .get("/health/live")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", is("UP"));
    }

    @Test
    public void testReadinessEndpoint() {
        given()
            .when()
            .get("/health/ready")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", is("UP"))
            .body("services", notNullValue());
    }
}