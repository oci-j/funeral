package io.oci.docker.overlay2;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarSplitReassemblerTest {

    @TempDir
    Path tempDir;

    @Test
    void reassembleFromJsonlWithInlinePayload() throws Exception {
        String header = "HEADER";
        String content = "content";
        String headerB64 = Base64.getEncoder()
                .encodeToString(
                        header.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String contentB64 = Base64.getEncoder()
                .encodeToString(
                        content.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String jsonl = "{\"type\":2,\"payload\":\"" + headerB64 + "\"}\n" + "{\"type\":1,\"name\":\"a.txt\",\"size\":"
                + content.length() + ",\"payload\":\"" + contentB64 + "\"}\n";

        Path tarSplitFile = tempDir.resolve(
                "tar-split.json.gz"
        );
        try (
                java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(
                        Files.newOutputStream(
                                tarSplitFile
                        )
                )) {
            gzipOut.write(
                    jsonl.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }

        Path diffDir = tempDir.resolve(
                "diff"
        );
        Files.createDirectories(
                diffDir
        );

        TarSplitReassembler.ReassembledLayer result = TarSplitReassembler.reassemble(
                tarSplitFile,
                diffDir
        );
        assertTrue(
                Files.isRegularFile(
                        result.file
                )
        );
        assertEquals(
                "sha256:",
                result.digest.substring(
                        0,
                        7
                )
        );
        assertTrue(
                result.size > 0
        );

        try (
                GZIPInputStream gzipIn = new GZIPInputStream(
                        Files.newInputStream(
                                result.file
                        )
                )) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            gzipIn.transferTo(
                    baos
            );
            String decompressed = baos.toString(
                    StandardCharsets.UTF_8
            );
            assertEquals(
                    header + content,
                    decompressed
            );
        }

        Files.deleteIfExists(
                result.file
        );
    }

    @Test
    void reassembleReadsFromDiffDirectory() throws Exception {
        String header = "DIFFDIR";
        String headerB64 = Base64.getEncoder()
                .encodeToString(
                        header.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String jsonl = "{\"type\":2,\"payload\":\"" + headerB64 + "\"}\n" + "{\"type\":1,\"name\":\"hello.txt\"}\n";

        Path tarSplitFile = tempDir.resolve(
                "tar-split.json.gz"
        );
        try (
                java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(
                        Files.newOutputStream(
                                tarSplitFile
                        )
                )) {
            gzipOut.write(
                    jsonl.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }

        Path diffDir = tempDir.resolve(
                "diff"
        );
        Files.createDirectories(
                diffDir
        );
        String content = "from-diff";
        Files.write(
                diffDir.resolve(
                        "hello.txt"
                ),
                content.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        TarSplitReassembler.ReassembledLayer result = TarSplitReassembler.reassemble(
                tarSplitFile,
                diffDir
        );
        try (
                GZIPInputStream gzipIn = new GZIPInputStream(
                        Files.newInputStream(
                                result.file
                        )
                )) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            gzipIn.transferTo(
                    baos
            );
            String decompressed = baos.toString(
                    StandardCharsets.UTF_8
            );
            assertEquals(
                    header + content,
                    decompressed
            );
        }
        Files.deleteIfExists(
                result.file
        );
    }

    @Test
    void preferDiffFileOverInlinePayload() throws Exception {
        String header = "PREFER";
        String headerB64 = Base64.getEncoder()
                .encodeToString(
                        header.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String payloadContent = "payload";
        String payloadContentB64 = Base64.getEncoder()
                .encodeToString(
                        payloadContent.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String jsonl = "{\"type\":2,\"payload\":\"" + headerB64 + "\"}\n"
                + "{\"type\":1,\"name\":\"hello.txt\",\"size\":" + payloadContent.length() + ",\"payload\":\""
                + payloadContentB64 + "\"}\n";

        Path tarSplitFile = tempDir.resolve(
                "tar-split.json.gz"
        );
        try (
                java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(
                        Files.newOutputStream(
                                tarSplitFile
                        )
                )) {
            gzipOut.write(
                    jsonl.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }

        Path diffDir = tempDir.resolve(
                "diff"
        );
        Files.createDirectories(
                diffDir
        );
        String diffContent = "from-diff";
        Files.write(
                diffDir.resolve(
                        "hello.txt"
                ),
                diffContent.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        TarSplitReassembler.ReassembledLayer result = TarSplitReassembler.reassemble(
                tarSplitFile,
                diffDir
        );
        try (
                GZIPInputStream gzipIn = new GZIPInputStream(
                        Files.newInputStream(
                                result.file
                        )
                )) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            gzipIn.transferTo(
                    baos
            );
            String decompressed = baos.toString(
                    StandardCharsets.UTF_8
            );
            assertEquals(
                    header + diffContent,
                    decompressed
            );
        }
        Files.deleteIfExists(
                result.file
        );
    }
}
