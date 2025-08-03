package io.oci.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class TagsResponse {
    public String name;
    public List<String> tags;

    public TagsResponse(String name, List<String> tags) {
        this.name = name;
        this.tags = tags;
    }
}
