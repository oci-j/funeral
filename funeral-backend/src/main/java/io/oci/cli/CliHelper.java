package io.oci.cli;

import java.time.Duration;

import io.oci.cli.auth.CompositeCredentialsStore;
import io.oci.cli.auth.Credentials;
import io.oci.cli.auth.CredentialsStore;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.util.RegistryResolver;
import io.oci.model.ImageReference;

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
        String authDomain = RegistryResolver.resolveAuthDomain(
                registry,
                configManager
        );
        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        Credentials credentials = store.load(
                authDomain
        );
        return new FuneralClient(
                resolvedRegistry,
                authDomain,
                credentials
        );
    }

    public static FuneralClient createClient(
            String registry,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        ConfigManager configManager = new ConfigManager();
        String resolvedRegistry = RegistryResolver.resolve(
                registry,
                configManager
        );
        String authDomain = RegistryResolver.resolveAuthDomain(
                registry,
                configManager
        );
        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        Credentials credentials = store.load(
                authDomain
        );
        return new FuneralClient(
                resolvedRegistry,
                authDomain,
                credentials,
                connectTimeout,
                requestTimeout
        );
    }

    public static FuneralClient createClient(
            ImageReference ref
    ) {
        return createClient(
                ref,
                null
        );
    }

    public static FuneralClient createClient(
            ImageReference ref,
            String serverUrl
    ) {
        ConfigManager configManager = new ConfigManager();
        String domain = ref.registry;
        String resolvedUrl = serverUrl != null && !serverUrl.isBlank()
                ? serverUrl
                : RegistryResolver.resolve(
                        domain,
                        configManager
                );
        String authDomain = RegistryResolver.resolveAuthDomain(
                domain,
                configManager
        );
        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        Credentials credentials = store.load(
                authDomain
        );
        return new FuneralClient(
                resolvedUrl,
                domain,
                credentials
        );
    }
}
