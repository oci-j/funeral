package io.oci.resource;

import java.security.MessageDigest;
import java.util.HexFormat;

import io.oci.resource.handler.AuthTestHelper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class OciV2ResourceTest {

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
    public void getAuthToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
        if (pushToken == null) {
            pushToken = AuthTestHelper.getPushToken();
        }
    }

    private String sha256(
            String content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return "sha256:" + HexFormat.of()
                .formatHex(
                        md.digest(
                                content.getBytes()
                        )
                );
    }

    private String sha256(
            byte[] content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return "sha256:" + HexFormat.of()
                .formatHex(
                        md.digest(
                                content
                        )
                );
    }

    private String uploadBlob(
            String repository,
            byte[] content
    )
            throws Exception {
        String digest = sha256(
                content
        );
        given().auth()
                .oauth2(
                        pushToken
                )
                .contentType(
                        "application/octet-stream"
                )
                .body(
                        content
                )
                .queryParam(
                        "digest",
                        digest
                )
                .when()
                .post(
                        "/v2/{name}/blobs/uploads/",
                        repository
                )
                .then()
                .statusCode(
                        201
                );
        return digest;
    }

    private String uploadManifest(
            String repository,
            String reference,
            String manifestContent
    )
            throws Exception {
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
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        201
                );
        return sha256(
                manifestContent
        );
    }

    @Test
    public void testGetVersion() {
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
                );
    }

    @Test
    public void testGetToken() {
        given().auth()
                .preemptive()
                .basic(
                        "admin",
                        "password"
                )
                .queryParam(
                        "service",
                        "registry"
                )
                .queryParam(
                        "scope",
                        "registry:catalog:*"
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
                );
    }

    @Test
    public void testDeleteRepository() throws Exception {
        String repository = "test/repo-delete-" + System.nanoTime();
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
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        "v1.0.0"
                )
                .then()
                .statusCode(
                        201
                );

        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}",
                        repository
                )
                .then()
                .statusCode(
                        202
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        "v1.0.0"
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testDeleteRepositoryNotFound() {
        String repository = "test/not-exist-" + System.nanoTime();
        given().auth()
                .oauth2(
                        pushToken
                )
                .when()
                .delete(
                        "/v2/{name}",
                        repository
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathGet() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/unknown-path-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathHead() {
        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/unknown-head-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testUnknownPathPost() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "application/json"
                )
                .body(
                        "{}"
                )
                .when()
                .post(
                        "/v2/unknown-post-" + System.nanoTime()
                )
                .then()
                .statusCode(
                        404
                );
    }

    @Test
    public void testPushAndPullManifest() throws Exception {
        String repository = "test/push-pull-" + System.nanoTime();
        String reference = "v1.0.0";
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

        uploadManifest(
                repository,
                reference,
                manifestContent
        );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "schemaVersion",
                        equalTo(
                                2
                        )
                );
    }

    @Test
    public void testHeadManifestAfterPush() throws Exception {
        String repository = "test/head-" + System.nanoTime();
        String reference = "latest";
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

        uploadManifest(
                repository,
                reference,
                manifestContent
        );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .head(
                        "/v2/{name}/manifests/{reference}",
                        repository,
                        reference
                )
                .then()
                .statusCode(
                        200
                )
                .header(
                        "Docker-Content-Digest",
                        notNullValue()
                );
    }

    @Test
    public void testUploadBlobAndGet() throws Exception {
        String repository = "test/blob-" + System.nanoTime();
        byte[] content = "Hello, blob!".getBytes();
        String digest = uploadBlob(
                repository,
                content
        );

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
                        200
                )
                .body(
                        equalTo(
                                "Hello, blob!"
                        )
                );
    }

    @Test
    public void testBlobMount() throws Exception {
        String sourceRepo = "test/mount-source-" + System.nanoTime();
        String targetRepo = "test/mount-target-" + System.nanoTime();
        byte[] content = "Mountable blob content".getBytes();
        String digest = uploadBlob(
                sourceRepo,
                content
        );

        given().auth()
                .oauth2(
                        pushToken
                )
                .queryParam(
                        "mount",
                        digest
                )
                .queryParam(
                        "from",
                        sourceRepo
                )
                .when()
                .post(
                        "/v2/{name}/blobs/uploads/",
                        targetRepo
                )
                .then()
                .statusCode(
                        201
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/blobs/{digest}",
                        targetRepo,
                        digest
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        equalTo(
                                "Mountable blob content"
                        )
                );
    }

    @Test
    public void testListTagsWithPagination() throws Exception {
        String repository = "test/tags-" + System.nanoTime();

        for (int i = 0; i < 3; i++) {
            String manifestContent = """
                    {
                        "schemaVersion": 2,
                        "mediaType": "application/vnd.oci.image.manifest.v1+json",
                        "config": {
                            "mediaType": "application/vnd.oci.image.config.v1+json",
                            "size": 100,
                            "digest": "sha256:test123"
                        },
                        "layers": [],
                        "annotations": {"tag": "tag-%d"}
                    }
                    """.formatted(
                    i
            );
            uploadManifest(
                    repository,
                    "tag-" + i,
                    manifestContent
            );
        }

        given().auth()
                .oauth2(
                        authToken
                )
                .queryParam(
                        "n",
                        2
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        repository
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "tags.size()",
                        equalTo(
                                2
                        )
                );

        given().auth()
                .oauth2(
                        authToken
                )
                .queryParam(
                        "n",
                        2
                )
                .queryParam(
                        "last",
                        "tag-1"
                )
                .when()
                .get(
                        "/v2/{name}/tags/list",
                        repository
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "tags.size()",
                        equalTo(
                                1
                        )
                );
    }

    @Test
    public void testReferrers() throws Exception {
        String repository = "test/referrers-" + System.nanoTime();
        String subjectManifest = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:subjectconfig"
                    },
                    "layers": []
                }
                """;
        String subjectDigest = uploadManifest(
                repository,
                "subject",
                subjectManifest
        );

        String referrerManifest = """
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "config": {
                        "mediaType": "application/vnd.oci.image.config.v1+json",
                        "size": 100,
                        "digest": "sha256:referrerconfig"
                    },
                    "layers": [],
                    "subject": {
                        "digest": "%s",
                        "mediaType": "application/vnd.oci.image.manifest.v1+json"
                    }
                }
                """.formatted(
                subjectDigest
        );
        uploadManifest(
                repository,
                "referrer",
                referrerManifest
        );

        given().auth()
                .oauth2(
                        authToken
                )
                .when()
                .get(
                        "/v2/{name}/referrers/{digest}",
                        repository,
                        subjectDigest
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "manifests.size()",
                        greaterThanOrEqualTo(
                                1
                        )
                );
    }
}
