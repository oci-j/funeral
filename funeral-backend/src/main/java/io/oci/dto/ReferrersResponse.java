package io.oci.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ReferrersResponse {

    @JsonProperty(
        "schemaVersion"
    )
    public Integer schemaVersion = 2;

    @JsonProperty(
        "mediaType"
    )
    public String mediaType = "application/vnd.oci.image.index.v1+json";

    @JsonProperty(
        "manifests"
    )
    public List<ArtifactDescriptor> manifests;

    public ReferrersResponse(
            List<ArtifactDescriptor> referrers
    ) {
        this.manifests = referrers;
    }
}
