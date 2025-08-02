package io.oci.dto;

public record CalculateTempChunkResult(
        int index,
        long bytesWritten
) {
}
