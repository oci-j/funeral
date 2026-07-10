package io.oci.cli.auth;

import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.config.RegistryAuth;

public class CompositeCredentialsStore implements CredentialsStore {

    private final KeyringCredentialsStore keyringStore;

    private final FileCredentialsStore fileStore;

    private final ConfigManager configManager;

    public CompositeCredentialsStore(
            ConfigManager configManager
    ) {
        this.configManager = configManager;
        this.keyringStore = new KeyringCredentialsStore(
                configManager
        );
        this.fileStore = new FileCredentialsStore(
                configManager
        );
    }

    @Override
    public void save(
            String registry,
            Credentials credentials
    ) {
        boolean keyringOk = false;
        try {
            keyringStore.save(
                    registry,
                    credentials
            );
            keyringOk = true;
        }
        catch (Exception e) {
            keyringOk = false;
        }

        if (!keyringOk) {
            fileStore.save(
                    registry,
                    credentials
            );
            CliConfig config = configManager.load();
            RegistryAuth auth = config.auths.getOrDefault(
                    registry,
                    new RegistryAuth()
            );
            auth.username = credentials.username;
            auth.keyring = false;
            config.auths.put(
                    registry,
                    auth
            );
            configManager.save(
                    config
            );
        }
    }

    @Override
    public Credentials load(
            String registry
    ) {
        Credentials c = null;
        try {
            c = keyringStore.load(
                    registry
            );
        }
        catch (Exception e) {
            c = null;
        }
        if (c == null) {
            c = fileStore.load(
                    registry
            );
        }
        return c;
    }

    @Override
    public void delete(
            String registry
    ) {
        try {
            keyringStore.delete(
                    registry
            );
        }
        catch (Exception e) {
        }
        fileStore.delete(
                registry
        );
        CliConfig config = configManager.load();
        config.auths.remove(
                registry
        );
        configManager.save(
                config
        );
    }
}
