package io.oci.cli.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigManager {

    private static final String CONFIG_DIR_PROPERTY = "funeral.config.dir";

    private static final String CONFIG_DIR_ENV = "FUNERAL_CONFIG_DIR";

    private static final Path CONFIG_DIR = resolveConfigDir();

    private static final Path CONFIG_FILE = CONFIG_DIR.resolve(
            "config.json"
    );

    private static Path resolveConfigDir() {
        String dir = System.getProperty(
                CONFIG_DIR_PROPERTY
        );
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv(
                    CONFIG_DIR_ENV
            );
        }
        if (dir != null && !dir.isEmpty()) {
            return Path.of(
                    dir
            );
        }
        return Paths.get(
                System.getProperty(
                        "user.home"
                ),
                ".funeral"
        );
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public CliConfig load() {
        if (!Files.exists(
                CONFIG_FILE
        )) {
            return new CliConfig();
        }
        try {
            return mapper.readValue(
                    CONFIG_FILE.toFile(),
                    CliConfig.class
            );
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load config: " + CONFIG_FILE,
                    e
            );
        }
    }

    public void save(
            CliConfig config
    ) {
        try {
            if (!Files.exists(
                    CONFIG_DIR
            )) {
                Files.createDirectories(
                        CONFIG_DIR
                );
            }
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(
                            CONFIG_FILE.toFile(),
                            config
                    );
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to save config: " + CONFIG_FILE,
                    e
            );
        }
    }

    public Path getConfigDir() {
        return CONFIG_DIR;
    }
}
