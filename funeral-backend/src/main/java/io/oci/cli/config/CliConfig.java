package io.oci.cli.config;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CliConfig {

    public String defaultRegistry;

    public Map<String, RegistryAuth> auths = new HashMap<>();

    public Map<String, RegistryAlias> aliases = new HashMap<>();
}
