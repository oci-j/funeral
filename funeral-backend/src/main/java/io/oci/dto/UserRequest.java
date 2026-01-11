package io.oci.dto;

import java.util.List;

public class UserRequest {

    public String username;

    public String password;

    public String email;

    public Boolean enabled;

    public List<String> roles;

    public List<String> allowedRepositories;
}
