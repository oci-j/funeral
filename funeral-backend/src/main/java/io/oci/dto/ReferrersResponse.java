package io.oci.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class ReferrersResponse {

    @JsonProperty("referrers")
    public List<ArtifactDescriptor> referrers;

    public ReferrersResponse(List<ArtifactDescriptor> referrers) {
        this.referrers = referrers;
    }
}
