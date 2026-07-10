package io.oci.cli.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RegistryAuth {

    public String username;

    public String password;

    public boolean keyring;
}
