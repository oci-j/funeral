package io.oci.cli.util;

import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.config.RegistryAlias;

public class RegistryResolver {

    private RegistryResolver() {
    }

    public static String resolve(
            String registry,
            ConfigManager configManager
    ) {
        if (registry != null && !registry.isEmpty()) {
            return resolveAlias(
                    registry,
                    configManager
            );
        }
        CliConfig config = configManager.load();
        if (config.defaultRegistry != null && !config.defaultRegistry.isEmpty()) {
            return resolveAlias(
                    config.defaultRegistry,
                    configManager
            );
        }
        throw new RuntimeException(
                "Registry not specified and no default registry configured"
        );
    }

    private static String resolveAlias(
            String registry,
            ConfigManager configManager
    ) {
        CliConfig config = configManager.load();
        RegistryAlias alias = config.aliases.get(
                registry
        );
        if (alias != null && alias.serverUrl != null && !alias.serverUrl.isBlank()) {
            return alias.serverUrl;
        }
        return registry;
    }

    public static String resolveAuthDomain(
            String registry,
            ConfigManager configManager
    ) {
        CliConfig config = configManager.load();
        RegistryAlias alias = config.aliases.get(
                registry
        );
        if (alias != null && alias.authDomain != null && !alias.authDomain.isBlank()) {
            return alias.authDomain;
        }
        return registry;
    }
}
