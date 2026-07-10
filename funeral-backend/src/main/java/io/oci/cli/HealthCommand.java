package io.oci.cli;

import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

import io.oci.cli.auth.CompositeCredentialsStore;
import io.oci.cli.auth.Credentials;
import io.oci.cli.auth.CredentialsStore;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.util.RegistryResolver;
import picocli.CommandLine;

@CommandLine.Command(
        name = "health",
        description = "Check the health of a Funeral registry"
)
public class HealthCommand implements Callable<Integer> {

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
        Credentials credentials = store.load(
                resolvedRegistry
        );
        FuneralClient client = new FuneralClient(
                resolvedRegistry,
                credentials
        );
        try {
            HttpResponse<String> response = client.get(
                    "/funeral_addition/health"
            );
            if (response.statusCode() == 200) {
                System.out.println(
                        "OK: " + response.body()
                );
                return 0;
            }
            System.out.println(
                    "UNHEALTHY (" + response.statusCode() + "): " + response.body()
            );
            return 1;
        }
        catch (Exception e) {
            System.out.println(
                    "Health check failed: " + e.getMessage()
            );
            return 1;
        }
    }
}
