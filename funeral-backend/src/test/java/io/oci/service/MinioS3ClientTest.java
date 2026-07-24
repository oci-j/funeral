package io.oci.service;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MinioS3ClientTest {

    private MinioClient minioClient;

    private MinioS3Client client;

    @BeforeEach
    public void setUp() {
        minioClient = mock(
                MinioClient.class
        );
        client = new MinioS3Client();
        client.minioClient = minioClient;
    }

    private ErrorResponseException errorResponse(
            String code
    ) {
        ErrorResponse errorResponse = mock(
                ErrorResponse.class
        );
        when(
                errorResponse.code()
        ).thenReturn(
                code
        );
        return new ErrorResponseException(
                errorResponse,
                null,
                null
        );
    }

    @Test
    public void testBucketExists() throws Exception {
        when(
                minioClient.bucketExists(
                        any(
                                BucketExistsArgs.class
                        )
                )
        ).thenReturn(
                true
        );
        assertTrue(
                client.bucketExists(
                        "test-bucket"
                )
        );

        when(
                minioClient.bucketExists(
                        any(
                                BucketExistsArgs.class
                        )
                )
        ).thenReturn(
                false
        );
        assertFalse(
                client.bucketExists(
                        "test-bucket"
                )
        );
    }

    @Test
    public void testBucketExistsError() throws Exception {
        when(
                minioClient.bucketExists(
                        any(
                                BucketExistsArgs.class
                        )
                )
        ).thenThrow(
                new RuntimeException(
                        "boom"
                )
        );
        assertThrows(
                IOException.class,
                () -> client.bucketExists(
                        "test-bucket"
                )
        );
    }

    @Test
    public void testMakeBucket() throws Exception {
        client.makeBucket(
                "test-bucket"
        );
        verify(
                minioClient
        ).makeBucket(
                any(
                        MakeBucketArgs.class
                )
        );
    }

    @Test
    public void testMakeBucketError() throws Exception {
        org.mockito.Mockito.doThrow(
                new RuntimeException(
                        "boom"
                )
        )
                .when(
                        minioClient
                )
                .makeBucket(
                        any(
                                MakeBucketArgs.class
                        )
                );
        assertThrows(
                IOException.class,
                () -> client.makeBucket(
                        "test-bucket"
                )
        );
    }

    @Test
    public void testSetBucketLifecycle() throws Exception {
        client.setBucketLifecycle(
                "test-bucket",
                "tmp/",
                7
        );
        verify(
                minioClient
        ).setBucketLifecycle(
                any(
                        SetBucketLifecycleArgs.class
                )
        );
    }

    @Test
    public void testSetBucketLifecycleError() throws Exception {
        org.mockito.Mockito.doThrow(
                new RuntimeException(
                        "boom"
                )
        )
                .when(
                        minioClient
                )
                .setBucketLifecycle(
                        any(
                                SetBucketLifecycleArgs.class
                        )
                );
        assertThrows(
                IOException.class,
                () -> client.setBucketLifecycle(
                        "test-bucket",
                        "tmp/",
                        7
                )
        );
    }

    @Test
    public void testPutObjectWithKnownSize() throws Exception {
        byte[] data = "hello".getBytes(
                StandardCharsets.UTF_8
        );
        client.putObject(
                "test-bucket",
                "o",
                new ByteArrayInputStream(
                        data
                ),
                data.length,
                "text/plain"
        );
        verify(
                minioClient
        ).putObject(
                any(
                        PutObjectArgs.class
                )
        );
    }

    @Test
    public void testPutObjectWithUnknownSize() throws Exception {
        client.putObject(
                "test-bucket",
                "o",
                new ByteArrayInputStream(
                        new byte[0]
                ),
                -1L,
                "application/octet-stream"
        );
        verify(
                minioClient
        ).putObject(
                any(
                        PutObjectArgs.class
                )
        );
    }

    @Test
    public void testPutObjectError() throws Exception {
        when(
                minioClient.putObject(
                        any(
                                PutObjectArgs.class
                        )
                )
        ).thenThrow(
                new RuntimeException(
                        "boom"
                )
        );
        assertThrows(
                IOException.class,
                () -> client.putObject(
                        "test-bucket",
                        "o",
                        new ByteArrayInputStream(
                                new byte[0]
                        ),
                        0,
                        "text/plain"
                )
        );
    }

    @Test
    public void testStatObjectNoSuchKey() throws Exception {
        ErrorResponseException exception = errorResponse(
                "NoSuchKey"
        );
        when(
                minioClient.statObject(
                        any(
                                StatObjectArgs.class
                        )
                )
        ).thenThrow(
                exception
        );
        assertThrows(
                FileNotFoundException.class,
                () -> client.statObject(
                        "test-bucket",
                        "missing"
                )
        );
    }

    @Test
    public void testStatObjectOtherError() throws Exception {
        ErrorResponseException exception = errorResponse(
                "AccessDenied"
        );
        when(
                minioClient.statObject(
                        any(
                                StatObjectArgs.class
                        )
                )
        ).thenThrow(
                exception
        );
        assertThrows(
                IOException.class,
                () -> client.statObject(
                        "test-bucket",
                        "o"
                )
        );

        when(
                minioClient.statObject(
                        any(
                                StatObjectArgs.class
                        )
                )
        ).thenThrow(
                new RuntimeException(
                        "boom"
                )
        );
        assertThrows(
                IOException.class,
                () -> client.statObject(
                        "test-bucket",
                        "o"
                )
        );
    }

    @Test
    public void testRemoveObject() throws Exception {
        client.removeObject(
                "test-bucket",
                "o"
        );
        verify(
                minioClient
        ).removeObject(
                any(
                        RemoveObjectArgs.class
                )
        );
    }

    @Test
    public void testRemoveObjectError() throws Exception {
        org.mockito.Mockito.doThrow(
                new RuntimeException(
                        "boom"
                )
        )
                .when(
                        minioClient
                )
                .removeObject(
                        any(
                                RemoveObjectArgs.class
                        )
                );
        assertThrows(
                IOException.class,
                () -> client.removeObject(
                        "test-bucket",
                        "o"
                )
        );
    }

    @Test
    public void testGetObjectNoSuchKeyReturnsNull() throws Exception {
        ErrorResponseException exception = errorResponse(
                "NoSuchKey"
        );
        when(
                minioClient.getObject(
                        any(
                                GetObjectArgs.class
                        )
                )
        ).thenThrow(
                exception
        );
        assertNull(
                client.getObject(
                        "test-bucket",
                        "missing"
                )
        );
    }

    @Test
    public void testGetObjectError() throws Exception {
        ErrorResponseException exception = errorResponse(
                "AccessDenied"
        );
        when(
                minioClient.getObject(
                        any(
                                GetObjectArgs.class
                        )
                )
        ).thenThrow(
                exception
        );
        assertThrows(
                IOException.class,
                () -> client.getObject(
                        "test-bucket",
                        "o"
                )
        );

        when(
                minioClient.getObject(
                        any(
                                GetObjectArgs.class
                        )
                )
        ).thenThrow(
                new RuntimeException(
                        "boom"
                )
        );
        assertThrows(
                IOException.class,
                () -> client.getObject(
                        "test-bucket",
                        "o"
                )
        );
    }

    @Test
    public void testComposeObject() throws Exception {
        client.composeObject(
                "target-bucket",
                "target-object",
                "source-bucket",
                List.of(
                        "part1",
                        "part2"
                )
        );
        verify(
                minioClient
        ).composeObject(
                any(
                        ComposeObjectArgs.class
                )
        );
    }

    @Test
    public void testComposeObjectError() throws Exception {
        when(
                minioClient.composeObject(
                        any(
                                ComposeObjectArgs.class
                        )
                )
        ).thenThrow(
                new RuntimeException(
                        "boom"
                )
        );
        assertThrows(
                IOException.class,
                () -> client.composeObject(
                        "tb",
                        "to",
                        "sb",
                        List.of(
                                "p1"
                        )
                )
        );
    }

    @Test
    public void testGetObjectSuccess() throws Exception {
        io.minio.GetObjectResponse stream = mock(
                io.minio.GetObjectResponse.class
        );
        when(
                minioClient.getObject(
                        any(
                                GetObjectArgs.class
                        )
                )
        ).thenReturn(
                stream
        );
        assertEquals(
                stream,
                client.getObject(
                        "test-bucket",
                        "o"
                )
        );
    }
}
