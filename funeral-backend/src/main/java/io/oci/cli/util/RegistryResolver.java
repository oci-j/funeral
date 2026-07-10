package io.oci.cli.util;

import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;

public class RegistryResolver {

    private RegistryResolver() {
    }

    public static String resolve(
            String registry,
            ConfigManager configManager
    ) {
        if (registry != null && !registry.isEmpty()) {
            return registry;
        }
        CliConfig config = configManager.load();
        if (config.defaultRegistry != null && !config.defaultRegistry.isEmpty()) {
            return config.defaultRegistry;
        }
        throw new RuntimeException(
                "Registry not specified and no default registry configured"
        );
    }
}
