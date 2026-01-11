package io.oci.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ArtifactDescriptor {

    @JsonProperty(
        "mediaType"
    )
    public String mediaType;

    @JsonProperty(
        "artifactType"
    )
    public String artifactType;

    @JsonProperty(
        "digest"
    )
    public String digest;

    @JsonProperty(
        "size"
    )
    public Long size;

    @JsonProperty(
        "annotations"
    )
    public Map<String, Object> annotations;

    public ArtifactDescriptor(
            String mediaType,
            String artifactType,
            String digest,
            Long size,
            Map<String, Object> annotations
    ) {
        this.mediaType = mediaType;
        this.artifactType = artifactType;
        this.digest = digest;
        this.size = size;
        this.annotations = annotations;
    }

}
