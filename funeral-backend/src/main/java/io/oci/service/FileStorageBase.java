package io.oci.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.oci.config.ObjectIdJacksonSerializer;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ApplicationScoped
public class FileStorageBase {

    @ConfigProperty(name = "oci.storage.local-storage-path", defaultValue = "/tmp/funeral-storage")
    String storagePath;

    protected final ObjectMapper objectMapper;

    protected FileStorageBase() {
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle ObjectId properly
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Create a custom module for ObjectId serialization
        SimpleModule module = new SimpleModule();
        module.addSerializer(ObjectId.class, new ObjectIdJacksonSerializer.ObjectIdSerializer());
        module.addDeserializer(ObjectId.class, new ObjectIdJacksonSerializer.ObjectIdDeserializer());
        this.objectMapper.registerModule(module);
    }

    protected <T> T readFromFile(Class<T> clazz, String collection, String id) {
        try {
            Path filePath = Paths.get(storagePath, collection, id + ".json");
            if (!Files.exists(filePath)) {
                return null;
            }
            String content = Files.readString(filePath);
            return objectMapper.readValue(content, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from file", e);
        }
    }

    protected <T> List<T> readAllFromFiles(Class<T> clazz, String collection) {
        try {
            Path dirPath = Paths.get(storagePath, collection);
            if (!Files.exists(dirPath)) {
                return List.of();
            }
            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            String content = Files.readString(p);
                            return objectMapper.readValue(content, clazz);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read file", e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to list files", e);
        }
    }

    protected <T> void writeToFile(T entity, String collection, String id) {
        try {
            Path dirPath = Paths.get(storagePath, collection);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(id + ".json");
            String content = objectMapper.writeValueAsString(entity);
            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file", e);
        }
    }

    protected void deleteFile(String collection, String id) {
        try {
            Path filePath = Paths.get(storagePath, collection, id + ".json");
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    protected long countFiles(String collection) {
        try {
            Path dirPath = Paths.get(storagePath, collection);
            if (!Files.exists(dirPath)) {
                return 0;
            }
            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count files", e);
        }
    }

    protected <T> Optional<T> findFirst(Class<T> clazz, String collection, java.util.function.Predicate<T> predicate) {
        try {
            Path dirPath = Paths.get(storagePath, collection);
            if (!Files.exists(dirPath)) {
                return Optional.empty();
            }
            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            String content = Files.readString(p);
                            return objectMapper.readValue(content, clazz);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read file", e);
                        }
                    })
                    .filter(predicate)
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find file", e);
        }
    }

    protected <T> long countWithFilter(String collection, java.util.function.Predicate<T> predicate, Class<T> clazz) {
        try {
            Path dirPath = Paths.get(storagePath, collection);
            if (!Files.exists(dirPath)) {
                return 0;
            }
            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            String content = Files.readString(p);
                            return objectMapper.readValue(content, clazz);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read file", e);
                        }
                    })
                    .filter(predicate)
                    .count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count files", e);
        }
    }
}
