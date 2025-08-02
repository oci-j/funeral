package io.oci.service;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.oci.dto.CalculateTempChunkResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class S3StorageService {

    @Inject
    MinioClient minioClient;

    @ConfigProperty(name = "oci.storage.bucket", defaultValue = "oci-registry")
    String bucketName;

    @ConfigProperty(name = "oci.storage.tempBucket", defaultValue = "oci-registry-temp")
    String tempBucketName;

    public long storeTempChunk(
            InputStream inputStream,
            String uploadUuid,
            int index
    ) throws IOException {
        try {
            // Ensure bucket exists
            ensureTempBucketExists();
            // Store in S3
            String objectKey = "chunk/" + uploadUuid + "/" + index;
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(tempBucketName)
                            .object(objectKey)
                            .stream(
                                    inputStream,
                                    -1,
                                    1024 * 1024 * 8
                            )
                            .contentType("application/octet-stream")
                            .build()
            );
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(tempBucketName)
                            .object(objectKey)
                            .build()
            );
            long bytesWritten = stat.size();
            return bytesWritten;
        } catch (Exception e) {
            throw new IOException("Failed to store blob", e);
        }
    }

    public void mergeTempChunks(
            String uploadUuid,
            int maxIndex,
            String digest
    ) throws IOException {
        try {
            // Ensure bucket exists
            ensureBucketExists();

            // Store in S3
            String finalObjectKey = "blobs/" + digest.replace(":", "/");

            List<ComposeSource> sources = new ArrayList<>();
            for (int i = 0; i < maxIndex; i++) {
                String objectKey = "chunk/" + uploadUuid + "/" + i;
                sources.add(ComposeSource.builder()
                        .bucket(tempBucketName)
                        .object(objectKey)
                        .build());
            }
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(finalObjectKey)
                            .sources(sources)
                            .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to store blob", e);
        }

    }

    public CalculateTempChunkResult calculateTempChunks(
            String uploadUuid
    ) throws IOException {
        try {
            // Ensure bucket exists
            ensureTempBucketExists();
        } catch (Exception e) {
            throw new IOException("Failed to store blob", e);
        }
        long bytesWritten = 0;
        int i = 0;
        while (true) {
            String objectKey = "chunk/" + uploadUuid + "/" + i;
            try {
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(tempBucketName)
                                .object(objectKey)
                                .build()
                );
                bytesWritten += stat.size();
            } catch (Exception e) {
                return new CalculateTempChunkResult(i, bytesWritten);
            }
            i++;
        }
    }


    public String storeBlob(InputStream inputStream, String expectedDigest) throws IOException {
        try {
            // Ensure bucket exists
            ensureBucketExists();

            // Create temp file to calculate digest and size
            File tempFile = File.createTempFile("blob-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;

            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 DigestInputStream dis = new DigestInputStream(inputStream, digest)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    size += bytesRead;
                }
            }

            String calculatedDigest = "sha256:" + bytesToHex(digest.digest());

            if (expectedDigest != null && !expectedDigest.equals(calculatedDigest)) {
                tempFile.delete();
                throw new IllegalArgumentException("Digest mismatch");
            }

            // Store in S3
            String objectKey = "blobs/" + calculatedDigest.replace(":", "/");

            try (FileInputStream fis = new FileInputStream(tempFile)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(fis, size, -1)
                                .contentType("application/octet-stream")
                                .build()
                );
            }

            tempFile.delete();
            return calculatedDigest;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new IOException("Failed to store blob", e);
        }
    }

    public InputStream getBlobStream(String digest) throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(":", "/");
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            if (e instanceof ErrorResponseException && ((ErrorResponseException) e).errorResponse().code().equals("NoSuchKey")) {
                return null;
            }
            throw new IOException("Failed to get blob", e);
        }
    }

    public boolean blobExists(String digest) {
        try {
            String objectKey = "blobs/" + digest.replace(":", "/");
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getBlobSize(String digest) throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(":", "/");
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return stat.size();
        } catch (Exception e) {
            throw new IOException("Failed to get blob size", e);
        }
    }

    public void deleteBlob(String digest) throws IOException {
        try {
            String objectKey = "blobs/" + digest.replace(":", "/");
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to delete blob", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        }
    }

    private void ensureTempBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(tempBucketName)
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(tempBucketName)
                            .build()
            );
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
