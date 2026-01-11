package io.oci.service;

import io.oci.exception.WithResponseException;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractStorageService {

    public abstract long storeTempChunk(
            InputStream inputStream,
            String uploadUuid,
            int index
    ) throws IOException, WithResponseException;

    public abstract void mergeTempChunks(
            String uploadUuid,
            int maxIndex,
            String digest
    ) throws IOException;

    public record CalculateTempChunkResult(
            int index,
            long bytesWritten
    ) {
    }

    public abstract CalculateTempChunkResult calculateTempChunks(
            String uploadUuid
    ) throws IOException;

    public abstract String storeBlob(InputStream inputStream, String expectedDigest) throws IOException;

    public abstract InputStream getBlobStream(String digest) throws IOException;

    public abstract long getBlobSize(String digest) throws IOException;

    public abstract void deleteBlob(String digest) throws IOException;

    public abstract boolean blobExists(String digest) throws IOException;
}
