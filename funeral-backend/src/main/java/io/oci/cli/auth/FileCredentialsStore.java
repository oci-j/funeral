package io.oci.cli.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.cli.config.ConfigManager;

public class FileCredentialsStore implements CredentialsStore {

    private final Path file;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Credentials> entries;

    public FileCredentialsStore(
            ConfigManager configManager
    ) {
        this.file = configManager.getConfigDir()
                .resolve(
                        "credentials.json"
                );
        load();
    }

    @Override
    public synchronized void save(
            String registry,
            Credentials credentials
    ) {
        load();
        entries.put(
                registry,
                credentials
        );
        persist();
    }

    @Override
    public synchronized Credentials load(
            String registry
    ) {
        load();
        Credentials c = entries.get(
                registry
        );
        if (c != null) {
            c.registry = registry;
        }
        return c;
    }

    @Override
    public synchronized void delete(
            String registry
    ) {
        load();
        entries.remove(
                registry
        );
        persist();
    }

    private void load() {
        if (!Files.exists(
                file
        )) {
            entries = new ConcurrentHashMap<>();
            return;
        }
        try {
            entries = mapper.readValue(
                    file.toFile(),
                    new TypeReference<Map<String, Credentials>>() {
                    }
            );
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load credentials: " + file,
                    e
            );
        }
    }

    private void persist() {
        try {
            Files.createDirectories(
                    file.getParent()
            );
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            file.toFile(),
                            entries
                    );
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to save credentials: " + file,
                    e
            );
        }
    }
}
