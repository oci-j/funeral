package io.oci.resource.handler;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class BlobResourceHandlerTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    private static String pushToken;

    @BeforeAll
    public static void setup() {
        port = 8081;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getAuthToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
        if (pushToken == null) {
            pushToken = AuthTestHelper.getPushToken();
        }
    }

    @Test
    public void testGetBlob() {
        String repository = "test/repo";
        String digest = "sha256:test123456789abcdef";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/blobs/{digest}",
                        repository,
                        digest
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
    public void testHeadBlob() {
        String repository = "test/repo";
        String digest = "sha256:test123456789abcdef";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/{name}/blobs/{digest}",
                        repository,
                        digest
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
    public void testDeleteBlob() {
        String repository = "test/repo";
        String digest = "sha256:test123456789abcdef";

        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}/blobs/{digest}",
                        repository,
                        digest
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        202
                                ),
                                is(
                                        404
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testStartBlobUpload() {
        String repository = "test/repo";

        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .post(
                        "/v2/{name}/blobs/uploads/",
                        repository
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        202
                                ),
                                is(
                                        201
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testCompleteBlobUploadWithDigestParam() {
        String repository = "test/repo";
        String digest = "sha256:abcdef123456";
        String testBlobContent = "test blob content";

        given().config(
                RestAssured.config()
                        .encoderConfig(
                                EncoderConfig.encoderConfig()
                                        .encodeContentTypeAs(
                                                "application/octet-stream",
                                                ContentType.TEXT
                                        )
                        )
        )
                .auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/octet-stream"
                )
                .body(
                        testBlobContent
                )
                .when()
                .post(
                        "/v2/{name}/blobs/uploads/?digest={digest}",
                        repository,
                        digest
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        201
                                ),
                                is(
                                        202
                                ),
                                is(
                                        400
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testGetUploadStatus() {
        String repository = "test/repo";
        String uuid = "test-uuid-12345";

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/blobs/uploads/{uuid}",
                        repository,
                        uuid
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        204
                                ),
                                is(
                                        404
                                )
                        )
                );
    }

    @Test
    public void testCompleteUploadWithData() {
        String repository = "test/repo";
        String uuid = "test-session-uuid";
        String digest = "sha256:testcomplete123";
        String blobData = "test blob data for complete upload";

        given().config(
                RestAssured.config()
                        .encoderConfig(
                                EncoderConfig.encoderConfig()
                                        .encodeContentTypeAs(
                                                "application/octet-stream",
                                                ContentType.TEXT
                                        )
                        )
        )
                .auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/octet-stream"
                )
                .queryParam(
                        "digest",
                        digest
                )
                .body(
                        blobData
                )
                .when()
                .post(
                        "/v2/{name}/blobs/uploads/{uuid}",
                        repository,
                        uuid
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        201
                                ),
                                is(
                                        400
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testCompleteUploadMonolithic() {
        String repository = "test/repo";
        String uuid = "test-session-uuid";
        String digest = "sha256:testmonolithic";
        String blobData = "monolithic blob upload";

        given().config(
                RestAssured.config()
                        .encoderConfig(
                                EncoderConfig.encoderConfig()
                                        .encodeContentTypeAs(
                                                "application/octet-stream",
                                                ContentType.TEXT
                                        )
                        )
        )
                .auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/octet-stream"
                )
                .queryParam(
                        "digest",
                        digest
                )
                .body(
                        blobData
                )
                .when()
                .put(
                        "/v2/{name}/blobs/uploads/{uuid}",
                        repository,
                        uuid
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        201
                                ),
                                is(
                                        400
                                ),
                                is(
                                        403
                                )
                        )
                );
    }

    @Test
    public void testUploadChunk() {
        String repository = "test/repo";
        String uuid = "test-session-uuid";
        String chunkData = "chunk of data";

        given().config(
                RestAssured.config()
                        .encoderConfig(
                                EncoderConfig.encoderConfig()
                                        .encodeContentTypeAs(
                                                "application/octet-stream",
                                                ContentType.TEXT
                                        )
                        )
        )
                .auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/octet-stream"
                )
                .header(
                        "Content-Range",
                        "bytes 0-100/1000"
                )
                .body(
                        chunkData
                )
                .when()
                .patch(
                        "/v2/{name}/blobs/uploads/{uuid}",
                        repository,
                        uuid
                )
                .then()
                .statusCode(
                        anyOf(
                                is(
                                        202
                                ),
                                is(
                                        400
                                ),
                                is(
                                        403
                                )
                        )
                );
    }
}
