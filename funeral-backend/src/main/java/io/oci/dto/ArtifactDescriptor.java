package io.oci.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ArtifactDescriptor {

    @JsonProperty("mediaType")
    public String mediaType;

    @JsonProperty("artifactType")
    public String artifactType;

    @JsonProperty("digest")
    public String digest;

    @JsonProperty("size")
    public Long size;

    public ArtifactDescriptor(String mediaType, String artifactType, String digest, Long size) {
        this.mediaType = mediaType;
        this.artifactType = artifactType;
        this.digest = digest;
        this.size = size;
    }
}
