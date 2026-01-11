package io.oci.service;

import io.oci.exception.WithResponseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("local-storage")
public class StorageService extends AbstractStorageService {

    @ConfigProperty(name = "oci.storage.local-storage-path", defaultValue = "/tmp/funeral-storage")
    String storagePath;

    String tempPath;

    @jakarta.annotation.PostConstruct
    void init() {
        tempPath = storagePath + "/temp";
    }

    @Override
    public String storeBlob(InputStream inputStream, String expectedDigest) throws IOException {
        Path storageDir = Paths.get(storagePath, "blobs");
        Files.createDirectories(storageDir);

        // Create temp file first
        Path tempFile = Files.createTempFile(storageDir, "blob-", ".tmp");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;

            try (InputStream is = inputStream;
                 FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                 DigestInputStream dis = new DigestInputStream(is, digest)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    size += bytesRead;
                }
            }

            String calculatedDigest = "sha256:" + bytesToHex(digest.digest());

            if (expectedDigest != null && !expectedDigest.equals(calculatedDigest)) {
                Files.delete(tempFile);
                throw new IllegalArgumentException("Digest mismatch");
            }

            // Move to final location
            String digestPath = calculatedDigest.replace(":", "/");
            Path finalPath = storageDir.resolve(digestPath);
            Files.createDirectories(finalPath.getParent());
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

            return calculatedDigest;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            try {
                Files.delete(tempFile);
            } catch (IOException ignored) {}
            throw e;
        }
    }

    @Override
    public InputStream getBlobStream(String digest) throws IOException {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(storagePath, "blobs", digestPath);

        if (!Files.exists(blobPath)) {
            return null;
        }

        return Files.newInputStream(blobPath);
    }

    @Override
    public long getBlobSize(String digest) throws IOException {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(storagePath, "blobs", digestPath);
        return Files.size(blobPath);
    }

    public boolean blobExists(String digest) {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(storagePath, "blobs", digestPath);
        return Files.exists(blobPath);
    }

    @Override
    public long storeTempChunk(InputStream inputStream, String uploadUuid, int index) throws IOException, WithResponseException {
        Path tempDir = Paths.get(tempPath, uploadUuid);
        Files.createDirectories(tempDir);

        Path chunkFile = tempDir.resolve("chunk-" + index + ".tmp");

        // Check if chunk already exists
        if (Files.exists(chunkFile) && Files.size(chunkFile) > 0) {
            throw new WithResponseException(jakarta.ws.rs.core.Response.status(416).build());
        }

        // Store chunk
        long bytesWritten = 0;
        try (OutputStream os = Files.newOutputStream(chunkFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }
        }

        return bytesWritten;
    }

    @Override
    public void mergeTempChunks(String uploadUuid, int maxIndex, String digest) throws IOException {
        Path tempDir = Paths.get(tempPath, uploadUuid);
        if (!Files.exists(tempDir)) {
            throw new IOException("Upload UUID not found");
        }

        // Read all chunks and merge them
        Path targetFile = Paths.get(storagePath, "blobs", digest.replace(":", "/"));
        Files.createDirectories(targetFile.getParent());

        try (OutputStream os = Files.newOutputStream(targetFile)) {
            for (int i = 0; i <= maxIndex; i++) {
                Path chunkFile = tempDir.resolve("chunk-" + i + ".tmp");
                if (!Files.exists(chunkFile)) {
                    throw new IOException("Missing chunk: " + i);
                }
                Files.copy(chunkFile, os);
                Files.delete(chunkFile);
            }
        }

        // Clean up temp directory
        Files.deleteIfExists(tempDir);
    }

    @Override
    public void deleteBlob(String digest) throws IOException {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(storagePath, "blobs", digestPath);
        Files.deleteIfExists(blobPath);
    }

    @Override
    public CalculateTempChunkResult calculateTempChunks(String uploadUuid) throws IOException {
        Path tempDir = Paths.get(tempPath, uploadUuid);
        if (!Files.exists(tempDir)) {
            return new CalculateTempChunkResult(0, 0);
        }

        long totalBytes = 0;
        int i = 0;
        while (true) {
            Path chunkFile = tempDir.resolve("chunk-" + i + ".tmp");
            if (!Files.exists(chunkFile)) {
                return new CalculateTempChunkResult(i, totalBytes);
            }
            totalBytes += Files.size(chunkFile);
            i++;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static class DigestInputStream extends InputStream {
        private final InputStream wrapped;
        private final MessageDigest digest;

        public DigestInputStream(InputStream wrapped, MessageDigest digest) {
            this.wrapped = wrapped;
            this.digest = digest;
        }

        @Override
        public int read() throws IOException {
            int b = wrapped.read();
            if (b != -1) {
                digest.update((byte) b);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int bytesRead = wrapped.read(buffer, offset, length);
            if (bytesRead > 0) {
                digest.update(buffer, offset, bytesRead);
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            wrapped.close();
        }
    }
}
