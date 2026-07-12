package io.oci.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface S3Client {

    boolean bucketExists(
            String bucket
    )
            throws IOException;

    void makeBucket(
            String bucket
    )
            throws IOException;

    void setBucketLifecycle(
            String bucket,
            String prefix,
            int expirationDays
    )
            throws IOException;

    void putObject(
            String bucket,
            String object,
            InputStream stream,
            long size,
            String contentType
    )
            throws IOException;

    long statObject(
            String bucket,
            String object
    )
            throws IOException;

    void removeObject(
            String bucket,
            String object
    )
            throws IOException;

    InputStream getObject(
            String bucket,
            String object
    )
            throws IOException;

    void composeObject(
            String targetBucket,
            String targetObject,
            String sourceBucket,
            List<String> sourceObjects
    )
            throws IOException;
}
