package io.oci.cli;

import io.oci.cli.auth.CompositeCredentialsStore;
import io.oci.cli.auth.Credentials;
import io.oci.cli.auth.CredentialsStore;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.util.RegistryResolver;

public class CliHelper {

    private CliHelper() {
    }

    public static FuneralClient createClient(
            String registry
    ) {
        ConfigManager configManager = new ConfigManager();
        String resolvedRegistry = RegistryResolver.resolve(
                registry,
                configManager
        );
        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        Credentials credentials = store.load(
                resolvedRegistry
        );
        return new FuneralClient(
                resolvedRegistry,
                credentials
        );
    }
}
