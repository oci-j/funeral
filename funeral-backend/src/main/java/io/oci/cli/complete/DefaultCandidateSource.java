package io.oci.cli.complete;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.oci.cli.CliHelper;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.dto.RepositoryInfo;
import io.oci.dto.UserResponse;

public class DefaultCandidateSource implements CompletionEngine.CandidateSource {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(
            500
    );

    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(
            800
    );

    @Override
    public List<String> hosts() {
        Set<String> hosts = new LinkedHashSet<>();
        CliConfig config = new ConfigManager().load();
        if (config.defaultRegistry != null && !config.defaultRegistry.isBlank()) {
            hosts.add(
                    config.defaultRegistry
            );
        }
        if (config.aliases != null) {
            hosts.addAll(
                    config.aliases.keySet()
            );
        }
        if (config.auths != null) {
            hosts.addAll(
                    config.auths.keySet()
            );
        }
        return List.copyOf(
                hosts
        );
    }

    @Override
    public List<String> repositories(
            String host
    ) {
        try {
            List<String> names = new ArrayList<>();
            for (RepositoryInfo repo : createClient(
                    host
            ).listRepositories()) {
                names.add(
                        repo.name
                );
            }
            return names;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<String> tags(
            String host,
            String repository
    ) {
        if (repository == null || repository.isBlank()) {
            return List.of();
        }
        try {
            return createClient(
                    host
            ).listTags(
                    repository
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<String> users(
            String host
    ) {
        try {
            List<String> names = new ArrayList<>();
            for (UserResponse user : createClient(
                    host
            ).listUsers()) {
                names.add(
                        user.username
                );
            }
            return names;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        catch (Exception e) {
            return List.of();
        }
    }

    private FuneralClient createClient(
            String host
    ) {
        return CliHelper.createClient(
                host,
                CONNECT_TIMEOUT,
                REQUEST_TIMEOUT
        );
    }
}
