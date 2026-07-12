package io.oci.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.SourceObject;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Filter;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.Status;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MinioS3Client implements S3Client {

    @Inject
    MinioClient minioClient;

    @Override
    public boolean bucketExists(
            String bucket
    )
            throws IOException {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to check bucket existence: " + bucket,
                    e
            );
        }
    }

    @Override
    public void makeBucket(
            String bucket
    )
            throws IOException {
        try {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to create bucket: " + bucket,
                    e
            );
        }
    }

    @Override
    public void setBucketLifecycle(
            String bucket,
            String prefix,
            int expirationDays
    )
            throws IOException {
        try {
            LifecycleConfiguration config = new LifecycleConfiguration(
                    List.of(
                            new LifecycleConfiguration.Rule(
                                    Status.ENABLED,
                                    null,
                                    new LifecycleConfiguration.Expiration(
                                            (java.time.ZonedDateTime) null,
                                            expirationDays,
                                            null,
                                            null
                                    ),
                                    new Filter(
                                            prefix
                                    ),
                                    null,
                                    null,
                                    null,
                                    null
                            )
                    )
            );
            minioClient.setBucketLifecycle(
                    SetBucketLifecycleArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .config(
                                    config
                            )
                            .build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to set bucket lifecycle: " + bucket,
                    e
            );
        }
    }

    @Override
    public void putObject(
            String bucket,
            String object,
            InputStream stream,
            long size,
            String contentType
    )
            throws IOException {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(
                            bucket
                    )
                    .object(
                            object
                    )
                    .contentType(
                            contentType
                    );
            if (size >= 0) {
                builder.stream(
                        stream,
                        size,
                        -1L
                );
            }
            else {
                builder.stream(
                        stream,
                        -1L,
                        8L * 1024 * 1024
                );
            }
            minioClient.putObject(
                    builder.build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to put object: " + bucket + "/" + object,
                    e
            );
        }
    }

    @Override
    public long statObject(
            String bucket,
            String object
    )
            throws IOException {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .object(
                                    object
                            )
                            .build()
            ).size();
        }
        catch (ErrorResponseException e) {
            if (e.errorResponse()
                    .code()
                    .equals(
                            "NoSuchKey"
                    )) {
                throw new FileNotFoundException(
                        "Object not found: " + bucket + "/" + object
                );
            }
            throw new IOException(
                    "Failed to stat object: " + bucket + "/" + object,
                    e
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to stat object: " + bucket + "/" + object,
                    e
            );
        }
    }

    @Override
    public void removeObject(
            String bucket,
            String object
    )
            throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .object(
                                    object
                            )
                            .build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to remove object: " + bucket + "/" + object,
                    e
            );
        }
    }

    @Override
    public InputStream getObject(
            String bucket,
            String object
    )
            throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(
                                    bucket
                            )
                            .object(
                                    object
                            )
                            .build()
            );
        }
        catch (ErrorResponseException e) {
            if (e.errorResponse()
                    .code()
                    .equals(
                            "NoSuchKey"
                    )) {
                return null;
            }
            throw new IOException(
                    "Failed to get object: " + bucket + "/" + object,
                    e
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to get object: " + bucket + "/" + object,
                    e
            );
        }
    }

    @Override
    public void composeObject(
            String targetBucket,
            String targetObject,
            String sourceBucket,
            List<String> sourceObjects
    )
            throws IOException {
        try {
            List<SourceObject> sources = new ArrayList<>();
            for (String source : sourceObjects) {
                sources.add(
                        SourceObject.builder()
                                .bucket(
                                        sourceBucket
                                )
                                .object(
                                        source
                                )
                                .build()
                );
            }
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(
                                    targetBucket
                            )
                            .object(
                                    targetObject
                            )
                            .sources(
                                    sources
                            )
                            .build()
            );
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to compose object: " + targetBucket + "/" + targetObject,
                    e
            );
        }
    }
}
