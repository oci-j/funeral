package io.oci.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.cli.oci.DigestUtil;
import io.oci.cli.oci.LocalStorageAdapter;
import io.oci.cli.oci.MockRegistryServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImportExportIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String REPOSITORY = "test/repo";

    private static final String TAG = "1.0";

    private static final String IMAGE_REF = "docker.xenoamess.com/" + REPOSITORY + ":" + TAG;

    private static final String CONFIG = "{\"architecture\":\"amd64\",\"os\":\"linux\",\"config\":{},\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[]}}";

    private static final String LAYER = "layer-content";

    @TempDir
    Path tempDir;

    private MockRegistryServer server;

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockRegistryServer();
        server.start();
        System.setProperty(
                "funeral.config.dir",
                tempDir.resolve(
                        "config"
                ).toString()
        );
        registerSampleImage();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
        System.clearProperty(
                "funeral.config.dir"
        );
    }

    private void registerSampleImage() throws IOException {
        byte[] configBytes = CONFIG.getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layerBytes = LAYER.getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                configBytes
        );
        String layerDigest = DigestUtil.sha256(
                layerBytes
        );
        String manifest = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + configBytes.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layerBytes.length + "}]}";
        byte[] manifestBytes = manifest.getBytes(
                StandardCharsets.UTF_8
        );
        server.registerManifest(
                REPOSITORY,
                TAG,
                manifestBytes,
                "application/vnd.oci.image.manifest.v1+json"
        );
        server.registerBlob(
                configDigest,
                configBytes
        );
        server.registerBlob(
                layerDigest,
                layerBytes
        );
    }

    @Test
    public void testImportToLocalStorage() throws Exception {
        Path storageDir = tempDir.resolve(
                "local-storage"
        );
        ImportCommand cmd = new ImportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                "local",
                "--storage",
                storageDir.toString(),
                "--server",
                server.baseUrl()
        );
        Integer exitCode = cmd.call();

        assertEquals(
                0,
                exitCode
        );
        LocalStorageAdapter local = new LocalStorageAdapter(
                storageDir.toString()
        );
        assertTrue(
                local.isAvailable()
        );
        byte[] manifestBytes = local.readManifest(
                REPOSITORY,
                TAG
        );
        assertNotNull(
                manifestBytes
        );
        assertNotNull(
                local.readManifestMediaType(
                        REPOSITORY,
                        TAG
                )
        );

        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        String configDigest = manifest.get(
                "config"
        )
                .get(
                        "digest"
                )
                .asText();
        String layerDigest = manifest.get(
                "layers"
        )
                .get(
                        0
                )
                .get(
                        "digest"
                )
                .asText();
        assertArrayEquals(
                CONFIG.getBytes(
                        StandardCharsets.UTF_8
                ),
                local.readBlob(
                        configDigest
                )
        );
        assertArrayEquals(
                LAYER.getBytes(
                        StandardCharsets.UTF_8
                ),
                local.readBlob(
                        layerDigest
                )
        );
    }

    @Test
    public void testImportToOciLayout() throws Exception {
        Path ociDir = tempDir.resolve(
                "oci-layout"
        );
        ImportCommand cmd = new ImportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                "oci",
                "--oci-dir",
                ociDir.toString(),
                "--server",
                server.baseUrl()
        );
        Integer exitCode = cmd.call();

        assertEquals(
                0,
                exitCode
        );
        assertTrue(
                Files.isRegularFile(
                        ociDir.resolve(
                                "index.json"
                        )
                )
        );
        JsonNode index = MAPPER.readTree(
                ociDir.resolve(
                        "index.json"
                ).toFile()
        );
        assertEquals(
                1,
                index.get(
                        "manifests"
                ).size()
        );
        String manifestDigest = index.get(
                "manifests"
        )
                .get(
                        0
                )
                .get(
                        "digest"
                )
                .asText();
        assertTrue(
                Files.isRegularFile(
                        ociDir.resolve(
                                "blobs/sha256/" + manifestDigest.replace(
                                        "sha256:",
                                        ""
                                )
                        )
                )
        );
    }

    @Test
    public void testExportFromLocalToFuneralServer() throws Exception {
        Path storageDir = tempDir.resolve(
                "local-storage"
        );
        prepareLocalStorage(
                storageDir
        );

        ExportCommand cmd = new ExportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                IMAGE_REF,
                "--from",
                "local",
                "--storage",
                storageDir.toString(),
                "--server",
                server.baseUrl()
        );
        Integer exitCode = cmd.call();

        assertEquals(
                0,
                exitCode
        );
        assertEquals(
                1,
                server.uploadCount()
        );
        byte[] tar = server.lastUploadedTarBytes();
        assertNotNull(
                tar
        );
        String manifestJson = extractManifestJson(
                tar
        );
        assertTrue(
                manifestJson.contains(
                        IMAGE_REF
                )
        );
    }

    @Test
    public void testExportHostHeaderOverride() throws Exception {
        Path storageDir = tempDir.resolve(
                "local-storage"
        );
        prepareLocalStorage(
                storageDir
        );

        ExportCommand cmd = new ExportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                IMAGE_REF,
                "--from",
                "local",
                "--storage",
                storageDir.toString(),
                "--server",
                server.baseUrl()
        );
        cmd.call();

        assertTrue(
                server.recordedHostHeaders()
                        .contains(
                                "docker.xenoamess.com"
                        )
        );
    }

    @Test
    public void testExportToMultipleTargets() throws Exception {
        Path storageDir = tempDir.resolve(
                "local-storage"
        );
        prepareLocalStorage(
                storageDir
        );

        ExportCommand cmd = new ExportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                "docker.xenoamess.com/test/repo:1.0",
                "--to",
                "docker.xenoamess.com/test/repo:2.0",
                "--from",
                "local",
                "--storage",
                storageDir.toString(),
                "--server",
                server.baseUrl()
        );
        Integer exitCode = cmd.call();

        assertEquals(
                0,
                exitCode
        );
        assertEquals(
                2,
                server.uploadCount()
        );
    }

    private void prepareLocalStorage(
            Path storageDir
    )
            throws IOException,
            InterruptedException {
        byte[] configBytes = CONFIG.getBytes(
                StandardCharsets.UTF_8
        );
        byte[] layerBytes = LAYER.getBytes(
                StandardCharsets.UTF_8
        );
        String configDigest = DigestUtil.sha256(
                configBytes
        );
        String layerDigest = DigestUtil.sha256(
                layerBytes
        );
        String manifest = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
                + configDigest + "\",\"size\":" + configBytes.length
                + "},\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
                + layerDigest + "\",\"size\":" + layerBytes.length + "}]}";
        byte[] manifestBytes = manifest.getBytes(
                StandardCharsets.UTF_8
        );

        LocalStorageAdapter local = new LocalStorageAdapter(
                storageDir.toString()
        );
        local.ensureRepository(
                REPOSITORY
        );
        local.writeManifest(
                REPOSITORY,
                TAG,
                manifestBytes,
                "application/vnd.oci.image.manifest.v1+json"
        );
        local.writeBlob(
                configDigest,
                configBytes
        );
        local.writeBlob(
                layerDigest,
                layerBytes
        );
    }

    @Test
    public void testExportContinueOnError() throws Exception {
        Path storageDir = tempDir.resolve(
                "local-storage"
        );
        prepareLocalStorage(
                storageDir
        );
        server.failUpload(
                true
        );

        ExportCommand cmd = new ExportCommand();
        new CommandLine(
                cmd
        ).parseArgs(
                IMAGE_REF,
                "--to",
                "docker.xenoamess.com/test/repo:1.0",
                "--to",
                "docker.xenoamess.com/test/repo:2.0",
                "--from",
                "local",
                "--storage",
                storageDir.toString(),
                "--server",
                server.baseUrl(),
                "--continue-on-error"
        );
        Integer exitCode = cmd.call();

        assertEquals(
                1,
                exitCode
        );
        assertEquals(
                2,
                server.uploadCount()
        );
    }

    private String extractManifestJson(
            byte[] tarBytes
    )
            throws IOException {
        try (
                TarArchiveInputStream tis = new TarArchiveInputStream(
                        new GzipCompressorInputStream(
                                new java.io.ByteArrayInputStream(
                                        tarBytes
                                )
                        )
                )) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if ("manifest.json".equals(
                        entry.getName()
                )) {
                    return new String(
                            tis.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                }
            }
        }
        throw new IllegalStateException(
                "manifest.json not found in tar"
        );
    }
}
