package io.oci.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class UserResponse {

    public String username;

    public String email;

    public Boolean enabled;

    public List<String> roles;

    public List<String> allowedRepositories;

    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;
}
