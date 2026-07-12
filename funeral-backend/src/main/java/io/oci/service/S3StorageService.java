package io.oci.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import io.oci.exception.WithResponseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named(
    "s3-storage"
)
public class S3StorageService extends AbstractStorageService {

    @Inject
    S3Client s3Client;

    @ConfigProperty(
            name = "oci.storage.bucket",
            defaultValue = "oci-registry"
    )
    String bucketName;

    @ConfigProperty(
            name = "oci.storage.tempBucket",
            defaultValue = "oci-registry-temp"
    )
    String tempBucketName;

    @Override
    public long storeTempChunk(
            InputStream inputStream,
            String uploadUuid,
            int index
    )
            throws IOException,
            WithResponseException {
        try {
            ensureTempBucketExists();
            String objectKey = "chunk/" + uploadUuid + "/" + index;
            long existingSize = 0;
            try {
                existingSize = s3Client.statObject(
                        tempBucketName,
                        objectKey
                );
            }
            catch (IOException e) {
                // Object does not exist yet
            }
            if (existingSize > 0) {
                throw new WithResponseException(
                        Response.status(
                                416
                        ).build()
                );
            }
            s3Client.putObject(
                    tempBucketName,
                    objectKey,
                    inputStream,
                    -1L,
                    "application/octet-stream"
            );
            return s3Client.statObject(
                    tempBucketName,
                    objectKey
            );
        }
        catch (WithResponseException | IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob",
                    e
            );
        }
    }

    @Override
    public void mergeTempChunks(
            String uploadUuid,
            int maxIndex,
            String digest
    )
            throws IOException {
        try {
            ensureBucketExists();

            String finalObjectKey = "blobs/" + digest.replace(
                    ":",
                    "/"
            );

            List<String> sourceKeys = new ArrayList<>();
            for (int i = 0; i <= maxIndex; i++) {
                sourceKeys.add(
                        "chunk/" + uploadUuid + "/" + i
                );
            }
            s3Client.composeObject(
                    bucketName,
                    finalObjectKey,
                    tempBucketName,
                    sourceKeys
            );

            for (int i = 0; i <= maxIndex; i++) {
                String objectKey = "chunk/" + uploadUuid + "/" + i;
                try {
                    s3Client.removeObject(
                            tempBucketName,
                            objectKey
                    );
                }
                catch (Exception ignored) {
                }
            }
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob",
                    e
            );
        }

    }

    @Override
    public CalculateTempChunkResult calculateTempChunks(
            String uploadUuid
    )
            throws IOException {
        try {
            ensureTempBucketExists();
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob",
                    e
            );
        }
        long bytesWritten = 0;
        int i = 0;
        while (true) {
            String objectKey = "chunk/" + uploadUuid + "/" + i;
            try {
                long size = s3Client.statObject(
                        tempBucketName,
                        objectKey
                );
                bytesWritten += size;
            }
            catch (Exception e) {
                return new CalculateTempChunkResult(
                        i,
                        bytesWritten
                );
            }
            i++;
        }
    }

    @Override
    public String storeBlob(
            InputStream inputStream,
            String expectedDigest
    )
            throws IOException {
        try {
            ensureBucketExists();

            File tempFile = File.createTempFile(
                    "blob-",
                    ".tmp"
            );
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );
            long size = 0;

            try (
                    FileOutputStream fos = new FileOutputStream(
                            tempFile
                    );
                    DigestInputStream dis = new DigestInputStream(
                            inputStream,
                            digest
                    )) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dis.read(
                        buffer
                )) != -1) {
                    fos.write(
                            buffer,
                            0,
                            bytesRead
                    );
                    size += bytesRead;
                }
            }

            String calculatedDigest = "sha256:" + bytesToHex(
                    digest.digest()
            );

            if (expectedDigest != null && !expectedDigest.equals(
                    calculatedDigest
            )) {
                tempFile.delete();
                throw new IllegalArgumentException(
                        "Digest mismatch"
                );
            }

            String objectKey = "blobs/" + calculatedDigest.replace(
                    ":",
                    "/"
            );

            try (
                    FileInputStream fis = new FileInputStream(
                            tempFile
                    )) {
                s3Client.putObject(
                        bucketName,
                        objectKey,
                        fis,
                        size,
                        "application/octet-stream"
                );
            }

            tempFile.delete();
            return calculatedDigest;

        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "SHA-256 not available",
                    e
            );
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob",
                    e
            );
        }
    }

    @Override
    public InputStream getBlobStream(
            String digest
    )
            throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(
                    ":",
                    "/"
            );
            return s3Client.getObject(
                    bucketName,
                    objectKey
            );
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to get blob",
                    e
            );
        }
    }

    @Override
    public long getBlobSize(
            String digest
    )
            throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(
                    ":",
                    "/"
            );
            return s3Client.statObject(
                    bucketName,
                    objectKey
            );
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to get blob size",
                    e
            );
        }
    }

    @Override
    public void deleteBlob(
            String digest
    )
            throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(
                    ":",
                    "/"
            );
            s3Client.removeObject(
                    bucketName,
                    objectKey
            );
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to delete blob",
                    e
            );
        }
    }

    @Override
    public boolean blobExists(
            String digest
    )
            throws IOException {
        try {
            getBlobSize(
                    digest
            );
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    private void ensureBucketExists() throws IOException {
        try {
            if (!s3Client.bucketExists(
                    bucketName
            )) {
                s3Client.makeBucket(
                        bucketName
                );
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to ensure bucket exists",
                    e
            );
        }
    }

    private void ensureTempBucketExists() throws IOException {
        try {
            if (!s3Client.bucketExists(
                    tempBucketName
            )) {
                s3Client.makeBucket(
                        tempBucketName
                );
                s3Client.setBucketLifecycle(
                        tempBucketName,
                        "chunk/",
                        1
                );
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to ensure temp bucket exists",
                    e
            );
        }
    }

    private String bytesToHex(
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

    private static class DigestInputStream extends InputStream {
        private final InputStream wrapped;

        private final MessageDigest digest;

        public DigestInputStream(
                InputStream wrapped,
                MessageDigest digest
        ) {
            this.wrapped = wrapped;
            this.digest = digest;
        }

        @Override
        public int read() throws IOException {
            int b = wrapped.read();
            if (b != -1) {
                digest.update(
                        (byte) b
                );
            }
            return b;
        }

        @Override
        public int read(
                byte[] buffer,
                int offset,
                int length
        )
                throws IOException {
            int bytesRead = wrapped.read(
                    buffer,
                    offset,
                    length
            );
            if (bytesRead > 0) {
                digest.update(
                        buffer,
                        offset,
                        bytesRead
                );
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            wrapped.close();
        }
    }
}
