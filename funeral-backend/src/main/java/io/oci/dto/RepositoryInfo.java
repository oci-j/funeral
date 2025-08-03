package io.oci.dto;

import java.time.LocalDateTime;

public class RepositoryInfo {
    public String name;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public long tagCount;

    public RepositoryInfo(String name, LocalDateTime createdAt, LocalDateTime updatedAt, long tagCount) {
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tagCount = tagCount;
    }
}
