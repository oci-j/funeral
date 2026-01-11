package io.oci.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.LocalDateTime;

@RegisterForReflection
public class ManifestInfo {
    public String digest;
    public String mediaType;
    public Long contentLength;
    public String tag;
    public String artifactType;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public ManifestInfo() {
    }

    public ManifestInfo(String digest, String mediaType, Long contentLength, String tag, String artifactType, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.digest = digest;
        this.mediaType = mediaType;
        this.contentLength = contentLength;
        this.tag = tag;
        this.artifactType = artifactType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
