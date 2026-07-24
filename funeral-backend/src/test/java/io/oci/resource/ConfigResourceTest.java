package io.oci.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class ConfigResourceTest {

    @Test
    public void testGetAuthConfig() {
        given().when()
                .get(
                        "/funeral_addition/config/auth"
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "enabled",
                        is(
                                true
                        )
                )
                .body(
                        "allowAnonymousPull",
                        is(
                                true
                        )
                )
                .body(
                        "realm",
                        is(
                                "http://localhost:8912/v2/token"
                        )
                );
    }

    @Test
    public void testGetAllConfig() {
        given().when()
                .get(
                        "/funeral_addition/config/all"
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "auth.enabled",
                        is(
                                true
                        )
                )
                .body(
                        "auth.allowAnonymousPull",
                        is(
                                true
                        )
                )
                .body(
                        "auth.realm",
                        notNullValue()
                );
    }

    @Test
    public void testGetRuntimeInfo() {
        given().when()
                .get(
                        "/funeral_addition/config/runtime"
                )
                .then()
                .statusCode(
                        200
                )
                .contentType(
                        ContentType.JSON
                )
                .body(
                        "isNativeImage",
                        is(
                                false
                        )
                )
                .body(
                        "pid",
                        notNullValue()
                )
                .body(
                        "javaVersion",
                        notNullValue()
                )
                .body(
                        "javaVendor",
                        notNullValue()
                )
                .body(
                        "osName",
                        notNullValue()
                )
                .body(
                        "osArch",
                        notNullValue()
                )
                .body(
                        "canDownload",
                        is(
                                false
                        )
                );
    }

    @Test
    public void testDownloadBinaryNotNativeImage() {
        given().when()
                .get(
                        "/funeral_addition/config/download/binary"
                )
                .then()
                .statusCode(
                        400
                );
    }
}
