package io.oci.cli.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Credentials {

    public String registry;

    public String username;

    public String password;
}
