package io.oci.cli.auth;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.config.RegistryAuth;

public class KeyringCredentialsStore implements CredentialsStore {

    private static final boolean DISABLE_KEYRING = Boolean.getBoolean(
            "funeral.cli.disableKeyring"
    );

    private final ConfigManager configManager;

    public KeyringCredentialsStore(
            ConfigManager configManager
    ) {
        this.configManager = configManager;
    }

    private void checkEnabled() {
        if (DISABLE_KEYRING) {
            throw new RuntimeException(
                    "Keyring disabled by funeral.cli.disableKeyring"
            );
        }
    }

    @Override
    public void save(
            String registry,
            Credentials credentials
    ) {
        checkEnabled();
        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(
                    domain(
                            registry
                    ),
                    credentials.username,
                    credentials.password
            );
            CliConfig config = configManager.load();
            RegistryAuth auth = config.auths.getOrDefault(
                    registry,
                    new RegistryAuth()
            );
            auth.username = credentials.username;
            auth.keyring = true;
            config.auths.put(
                    registry,
                    auth
            );
            configManager.save(
                    config
            );
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "Failed to save credentials to keyring",
                    e
            );
        }
    }

    @Override
    public Credentials load(
            String registry
    ) {
        checkEnabled();
        CliConfig config = configManager.load();
        RegistryAuth auth = config.auths.get(
                registry
        );
        if (auth == null || auth.username == null) {
            return null;
        }
        try (Keyring keyring = Keyring.create()) {
            String password = keyring.getPassword(
                    domain(
                            registry
                    ),
                    auth.username
            );
            Credentials c = new Credentials();
            c.registry = registry;
            c.username = auth.username;
            c.password = password;
            return c;
        }
        catch (PasswordAccessException e) {
            return null;
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load credentials from keyring",
                    e
            );
        }
    }

    @Override
    public void delete(
            String registry
    ) {
        checkEnabled();
        CliConfig config = configManager.load();
        RegistryAuth auth = config.auths.get(
                registry
        );
        if (auth == null || auth.username == null) {
            return;
        }
        try (Keyring keyring = Keyring.create()) {
            keyring.deletePassword(
                    domain(
                            registry
                    ),
                    auth.username
            );
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "Failed to delete credentials from keyring",
                    e
            );
        }
        config.auths.remove(
                registry
        );
        configManager.save(
                config
        );
    }

    private String domain(
            String registry
    ) {
        return "funeral:" + registry;
    }
}
