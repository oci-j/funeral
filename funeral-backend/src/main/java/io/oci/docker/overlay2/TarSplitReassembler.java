package io.oci.docker.overlay2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TarSplitReassembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TarSplitReassembler() {
    }

    public static ReassembledLayer reassemble(
            Path tarSplitFile,
            Path diffDir
    )
            throws IOException {
        Path tempFile = Files.createTempFile(
                "overlay2-layer-",
                ".tar.gz"
        );
        String digest;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(
                    "SHA-256"
            );
            try (
                    OutputStream fileOut = Files.newOutputStream(
                            tempFile
                    );
                    GZIPOutputStream gzipOut = new GZIPOutputStream(
                            fileOut
                    );
                    OutputStream digestOut = new DigestOutputStreamWrapper(
                            gzipOut,
                            messageDigest
                    )) {
                parseAndWrite(
                        tarSplitFile,
                        diffDir,
                        digestOut
                );
            }
            digest = "sha256:" + bytesToHex(
                    messageDigest.digest()
            );
        }
        catch (NoSuchAlgorithmException e) {
            Files.deleteIfExists(
                    tempFile
            );
            throw new IOException(
                    "SHA-256 not available",
                    e
            );
        }
        catch (IOException e) {
            Files.deleteIfExists(
                    tempFile
            );
            throw e;
        }
        long size = Files.size(
                tempFile
        );
        return new ReassembledLayer(
                tempFile,
                size,
                digest
        );
    }

    private static void parseAndWrite(
            Path tarSplitFile,
            Path diffDir,
            OutputStream out
    )
            throws IOException {
        try (
                InputStream fis = Files.newInputStream(
                        tarSplitFile
                );
                GZIPInputStream gzip = new GZIPInputStream(
                        fis
                );
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                gzip,
                                StandardCharsets.UTF_8
                        )
                )) {
            Set<String> seenInodes = new HashSet<>();
            reader.mark(
                    1
            );
            int first = reader.read();
            reader.reset();
            if (first == '[') {
                JsonNode root = MAPPER.readTree(
                        reader
                );
                if (!root.isArray()) {
                    throw new IOException(
                            "tar-split.json is not an array"
                    );
                }
                for (JsonNode entry : root) {
                    processEntry(
                            entry,
                            diffDir,
                            out,
                            seenInodes
                    );
                }
            }
            else {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    JsonNode entry = MAPPER.readTree(
                            line
                    );
                    processEntry(
                            entry,
                            diffDir,
                            out,
                            seenInodes
                    );
                }
            }
        }
    }

    private static void processEntry(
            JsonNode entry,
            Path diffDir,
            OutputStream out,
            Set<String> seenInodes
    )
            throws IOException {
        int type = entry.has(
                "type"
        )
                ? entry.get(
                        "type"
                ).asInt()
                : 0;
        if (type == 2) {
            byte[] payload = entry.has(
                    "payload"
            ) && !entry.get(
                    "payload"
            ).isNull()
                    ? entry.get(
                            "payload"
                    ).binaryValue()
                    : new byte[0];
            out.write(
                    payload
            );
        }
        else if (type == 1) {
            String name = getName(
                    entry
            );
            if (name == null) {
                throw new IOException(
                        "tar-split FileType entry missing name"
                );
            }
            Path file = diffDir.resolve(
                    name
            );
            if (Files.isRegularFile(
                    file
            )) {
                writeFileFromDiff(
                        file,
                        out,
                        seenInodes
                );
            }
            else {
                byte[] payload = entry.has(
                        "payload"
                ) && !entry.get(
                        "payload"
                ).isNull()
                        ? entry.get(
                                "payload"
                        ).binaryValue()
                        : null;
                if (payload != null && payload.length > 0) {
                    out.write(
                            payload
                    );
                }
            }
        }
    }

    private static void writeFileFromDiff(
            Path file,
            OutputStream out,
            Set<String> seenInodes
    )
            throws IOException {
        Object ino = null;
        try {
            ino = Files.getAttribute(
                    file,
                    "unix:ino"
            );
        }
        catch (Exception e) {
            // ignore and treat as unique
        }
        if (ino != null) {
            if (!seenInodes.add(
                    ino.toString()
            )) {
                return;
            }
        }
        try (
                InputStream fileIn = Files.newInputStream(
                        file
                )) {
            fileIn.transferTo(
                    out
            );
        }
    }

    private static String getName(
            JsonNode entry
    )
            throws IOException {
        if (entry.has(
                "name"
        ) && !entry.get(
                "name"
        ).isNull()) {
            return entry.get(
                    "name"
            ).asText();
        }
        if (entry.has(
                "name_raw"
        )) {
            byte[] raw = entry.get(
                    "name_raw"
            ).binaryValue();
            return new String(
                    raw,
                    StandardCharsets.UTF_8
            );
        }
        return null;
    }

    private static String bytesToHex(
            byte[] bytes
    ) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }
        return result.toString();
    }

    private static class DigestOutputStreamWrapper extends OutputStream {

        private final OutputStream out;

        private final MessageDigest digest;

        DigestOutputStreamWrapper(
                OutputStream out,
                MessageDigest digest
        ) {
            this.out = out;
            this.digest = digest;
        }

        @Override
        public void write(
                int b
        )
                throws IOException {
            out.write(
                    b
            );
            digest.update(
                    (byte) b
            );
        }

        @Override
        public void write(
                byte[] b,
                int off,
                int len
        )
                throws IOException {
            out.write(
                    b,
                    off,
                    len
            );
            digest.update(
                    b,
                    off,
                    len
            );
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    public static class ReassembledLayer {

        public final Path file;

        public final long size;

        public final String digest;

        public ReassembledLayer(
                Path file,
                long size,
                String digest
        ) {
            this.file = file;
            this.size = size;
            this.digest = digest;
        }
    }
}
