package io.oci.cli;

import java.util.concurrent.Callable;

import io.oci.cli.auth.CompositeCredentialsStore;
import io.oci.cli.auth.CredentialsStore;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.util.RegistryResolver;
import picocli.CommandLine;

@CommandLine.Command(
        name = "logout",
        description = "Log out from a Funeral registry"
)
public class LogoutCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            arity = "0..1",
            description = "Registry host:port"
    )
    String registry;

    @Override
    public Integer call() throws Exception {
        ConfigManager configManager = new ConfigManager();
        String resolvedRegistry = RegistryResolver.resolve(
                registry,
                configManager
        );
        CredentialsStore store = new CompositeCredentialsStore(
                configManager
        );
        store.delete(
                resolvedRegistry
        );
        System.out.println(
                "Logged out from " + resolvedRegistry
        );
        return 0;
    }
}
