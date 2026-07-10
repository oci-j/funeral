package io.oci.cli.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RegistryAlias {

    public String serverUrl;

    public String authDomain;

    public String protocol;

}
