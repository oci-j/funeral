package io.oci.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@ApplicationScoped
public class StorageService {
    private static final String STORAGE_ROOT = System.getProperty("oci.storage.root", "/tmp/oci-registry");

    public String storeBlob(InputStream inputStream, String expectedDigest) throws IOException {
        Path storageDir = Paths.get(STORAGE_ROOT, "blobs");
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

    public InputStream getBlobStream(String digest) throws IOException {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(STORAGE_ROOT, "blobs", digestPath);

        if (!Files.exists(blobPath)) {
            return null;
        }

        return Files.newInputStream(blobPath);
    }

    public boolean blobExists(String digest) {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(STORAGE_ROOT, "blobs", digestPath);
        return Files.exists(blobPath);
    }

    public long getBlobSize(String digest) throws IOException {
        String digestPath = digest.replace(":", "/");
        Path blobPath = Paths.get(STORAGE_ROOT, "blobs", digestPath);
        return Files.size(blobPath);
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
