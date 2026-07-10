package io.oci.cli;

import java.util.concurrent.Callable;

import io.oci.cli.auth.CompositeCredentialsStore;
import io.oci.cli.auth.Credentials;
import io.oci.cli.auth.CredentialsStore;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.util.RegistryResolver;
import picocli.CommandLine;

@CommandLine.Command(
        name = "login",
        description = "Log in to a Funeral registry"
)
public class LoginCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            arity = "0..1",
            description = "Registry host:port"
    )
    String registry;

    @CommandLine.Option(
            names = {
                    "-u", "--username"
            },
            description = "Username",
            interactive = true
    )
    String username;

    @CommandLine.Option(
            names = {
                    "-p", "--password"
            },
            description = "Password",
            arity = "0..1",
            interactive = true
    )
    String password;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        String resolvedRegistry = RegistryResolver.resolve(
                registry,
                configManager
        );

        if (username == null) {
            java.io.Console console = System.console();
            if (console == null) {
                throw new RuntimeException(
                        "Username required"
                );
            }
            username = console.readLine(
                    "Username: "
            );
        }
        if (password == null) {
            java.io.Console console = System.console();
            if (console == null) {
                throw new RuntimeException(
                        "Password required"
                );
            }
            char[] pass = console.readPassword(
                    "Password: "
            );
            password = pass != null
                    ? new String(
                            pass
                    )
                    : null;
        }
        if (username == null || username.isEmpty()) {
            throw new RuntimeException(
                    "Username is required"
            );
        }
        if (password == null || password.isEmpty()) {
            throw new RuntimeException(
                    "Password is required"
            );
        }

        Credentials credentials = new Credentials();
        credentials.registry = resolvedRegistry;
        credentials.username = username;
        credentials.password = password;

        FuneralClient client = new FuneralClient(
                resolvedRegistry,
                credentials
        );
        String token = client.getToken();
        if (token == null) {
            throw new RuntimeException(
                    "Login failed: no token returned"
            );
        }

        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        store.save(
                resolvedRegistry,
                credentials
        );

        CliConfig config = configManager.load();
        if (config.defaultRegistry == null) {
            config.defaultRegistry = resolvedRegistry;
            configManager.save(
                    config
            );
        }

        System.out.println(
                "Login succeeded for " + resolvedRegistry
        );
        return 0;
    }
}
