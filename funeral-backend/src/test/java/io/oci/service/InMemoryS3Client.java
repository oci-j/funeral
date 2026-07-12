package io.oci.service;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(
    1
)
@ApplicationScoped
public class InMemoryS3Client implements S3Client {

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean bucketExists(
            String bucket
    )
            throws IOException {
        return buckets.containsKey(
                bucket
        );
    }

    @Override
    public void makeBucket(
            String bucket
    )
            throws IOException {
        buckets.putIfAbsent(
                bucket,
                new ConcurrentHashMap<>()
        );
    }

    @Override
    public void setBucketLifecycle(
            String bucket,
            String prefix,
            int expirationDays
    )
            throws IOException {
        // no-op for in-memory tests
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
        makeBucket(
                bucket
        );
        byte[] data = stream.readAllBytes();
        buckets.get(
                bucket
        )
                .put(
                        object,
                        data
                );
    }

    @Override
    public long statObject(
            String bucket,
            String object
    )
            throws IOException {
        if (!bucketExists(
                bucket
        )) {
            throw new FileNotFoundException(
                    "Bucket not found: " + bucket
            );
        }
        byte[] data = buckets.get(
                bucket
        )
                .get(
                        object
                );
        if (data == null) {
            throw new FileNotFoundException(
                    "Object not found: " + bucket + "/" + object
            );
        }
        return data.length;
    }

    @Override
    public void removeObject(
            String bucket,
            String object
    )
            throws IOException {
        if (bucketExists(
                bucket
        )) {
            buckets.get(
                    bucket
            )
                    .remove(
                            object
                    );
        }
    }

    @Override
    public InputStream getObject(
            String bucket,
            String object
    )
            throws IOException {
        if (!bucketExists(
                bucket
        )) {
            return null;
        }
        byte[] data = buckets.get(
                bucket
        )
                .get(
                        object
                );
        if (data == null) {
            return null;
        }
        return new ByteArrayInputStream(
                data
        );
    }

    @Override
    public void composeObject(
            String targetBucket,
            String targetObject,
            String sourceBucket,
            List<String> sourceObjects
    )
            throws IOException {
        makeBucket(
                targetBucket
        );
        List<byte[]> parts = new ArrayList<>();
        for (String source : sourceObjects) {
            byte[] data = buckets.getOrDefault(
                    sourceBucket,
                    Map.of()
            )
                    .get(
                            source
                    );
            if (data == null) {
                throw new FileNotFoundException(
                        "Missing source object: " + sourceBucket + "/" + source
                );
            }
            parts.add(
                    data
            );
        }
        int totalLength = 0;
        for (byte[] part : parts) {
            totalLength += part.length;
        }
        byte[] merged = new byte[totalLength];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(
                    part,
                    0,
                    merged,
                    offset,
                    part.length
            );
            offset += part.length;
        }
        buckets.get(
                targetBucket
        )
                .put(
                        targetObject,
                        merged
                );
    }
}
