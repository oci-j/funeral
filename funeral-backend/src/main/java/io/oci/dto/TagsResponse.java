package io.oci.dto;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TagsResponse {
    public String name;

    public List<String> tags;

    public TagsResponse(
            String name,
            List<String> tags
    ) {
        this.name = name;
        this.tags = tags;
    }
}
