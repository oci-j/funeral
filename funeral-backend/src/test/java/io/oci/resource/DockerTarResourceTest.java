package io.oci.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import io.oci.resource.handler.AuthTestHelper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class DockerTarResourceTest {

    @TestHTTPResource
    String baseUrl;

    private static String authToken;

    @BeforeAll
    public static void setup() {
        port = 8912;
        baseURI = "http://localhost";
    }

    @BeforeEach
    public void getToken() {
        if (authToken == null) {
            authToken = AuthTestHelper.getAuthToken();
        }
    }

    private String sha256Hex(
            byte[] content
    )
            throws Exception {
        MessageDigest md = MessageDigest.getInstance(
                "SHA-256"
        );
        return HexFormat.of()
                .formatHex(
                        md.digest(
                                content
                        )
                );
    }

    private byte[] createMinimalDockerTar(
            String repositoryName,
            String tag
    )
            throws Exception {
        byte[] configContent = """
                {
                    "architecture": "amd64",
                    "os": "linux",
                    "config": {}
                }
                """.getBytes();
        String configDigest = sha256Hex(
                configContent
        );

        byte[] layerContent = "minimal layer content".getBytes();
        String layerDigest = sha256Hex(
                layerContent
        );

        String manifestJson = """
                [{
                    "Config": "%s.json",
                    "RepoTags": ["%s:%s"],
                    "Layers": ["%s.tar.gz"]
                }]
                """.formatted(
                configDigest,
                repositoryName,
                tag,
                layerDigest
        );

        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(
                        baos
                )) {
            tarOut.setLongFileMode(
                    TarArchiveOutputStream.LONGFILE_GNU
            );

            addTarEntry(
                    tarOut,
                    "manifest.json",
                    manifestJson.getBytes()
            );
            addTarEntry(
                    tarOut,
                    configDigest + ".json",
                    configContent
            );

            // Store layer as gzip to match .tar.gz extension semantics
            byte[] gzippedLayer;
            try (
                    ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
                    GZIPOutputStream gzipOut = new GZIPOutputStream(
                            gzipBaos
                    )) {
                gzipOut.write(
                        layerContent
                );
                gzipOut.finish();
                gzippedLayer = gzipBaos.toByteArray();
            }
            addTarEntry(
                    tarOut,
                    layerDigest + ".tar.gz",
                    gzippedLayer
            );

            tarOut.finish();
            return baos.toByteArray();
        }
    }

    private void addTarEntry(
            TarArchiveOutputStream tarOut,
            String name,
            byte[] content
    )
            throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(
                name
        );
        entry.setSize(
                content.length
        );
        tarOut.putArchiveEntry(
                entry
        );
        tarOut.write(
                content
        );
        tarOut.closeArchiveEntry();
    }

    @Test
    public void testUploadSingleDockerTar() throws Exception {
        String repository = "test/docker-tar-" + System.nanoTime();
        byte[] tarBytes = createMinimalDockerTar(
                repository,
                "latest"
        );

        given().auth()
                .oauth2(
                        authToken
                )
                .multiPart(
                        "file",
                        "image.tar",
                        tarBytes,
                        "application/x-tar"
                )
                .when()
                .post(
                        "/funeral_addition/write/upload/dockertar"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "repositories",
                        hasItem(
                                repository
                        )
                )
                .body(
                        "manifests.size()",
                        greaterThanOrEqualTo(
                                1
                        )
                )
                .body(
                        "blobs.size()",
                        greaterThanOrEqualTo(
                                1
                        )
                );
    }

    @Test
    public void testUploadBatchDockerTar() throws Exception {
        String repository = "test/docker-tar-batch-" + System.nanoTime();
        byte[] tarBytes = createMinimalDockerTar(
                repository,
                "v1.0"
        );

        given().auth()
                .oauth2(
                        authToken
                )
                .multiPart(
                        "files",
                        "image.tar",
                        tarBytes,
                        "application/x-tar"
                )
                .when()
                .post(
                        "/funeral_addition/write/upload/dockertar/batch"
                )
                .then()
                .statusCode(
                        200
                )
                .body(
                        "totalFiles",
                        equalTo(
                                1
                        )
                )
                .body(
                        "successfulUploads",
                        equalTo(
                                1
                        )
                )
                .body(
                        "failedUploads",
                        equalTo(
                                0
                        )
                );
    }

    @Test
    public void testUploadEmptyTar() {
        byte[] emptyTar = new byte[0];
        given().auth()
                .oauth2(
                        authToken
                )
                .multiPart(
                        "file",
                        "empty.tar",
                        emptyTar,
                        "application/x-tar"
                )
                .when()
                .post(
                        "/funeral_addition/write/upload/dockertar"
                )
                .then()
                .statusCode(
                        400
                );
    }

    @Test
    public void testUploadTarWithoutManifest() throws Exception {
        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(
                        baos
                )) {
            addTarEntry(
                    tarOut,
                    "somefile.txt",
                    "not a docker tar".getBytes()
            );
            tarOut.finish();
            byte[] tarBytes = baos.toByteArray();

            given().auth()
                    .oauth2(
                            authToken
                    )
                    .multiPart(
                            "file",
                            "bad.tar",
                            tarBytes,
                            "application/x-tar"
                    )
                    .when()
                    .post(
                            "/funeral_addition/write/upload/dockertar"
                    )
                    .then()
                    .statusCode(
                            400
                    );
        }
    }

    @Test
    public void testUploadBatchEmptyFiles() {
        given().auth()
                .oauth2(
                        authToken
                )
                .contentType(
                        "multipart/form-data"
                )
                .when()
                .post(
                        "/funeral_addition/write/upload/dockertar/batch"
                )
                .then()
                .statusCode(
                        400
                )
                .body(
                        "errors.code",
                        hasItem(
                                "NO_FILES"
                        )
                );
    }
}
