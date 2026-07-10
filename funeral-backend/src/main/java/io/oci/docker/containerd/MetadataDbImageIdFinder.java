package io.oci.docker.containerd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MetadataDbImageIdFinder {

    private static final Logger log = LoggerFactory.getLogger(
            MetadataDbImageIdFinder.class
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Optional<String> findImageId(
            Path dockerRoot,
            String repositoryName,
            String reference
    ) {
        Optional<Path> dbPath = findMetadataDb(
                dockerRoot
        );
        if (dbPath.isEmpty()) {
            return Optional.empty();
        }
        try (
                Connection conn = DriverManager.getConnection(
                        "jdbc:sqlite:" + dbPath.get()
                )) {
            return findDigest(
                    conn,
                    repositoryName,
                    reference
            );
        }
        catch (SQLException e) {
            log.warn(
                    "Failed to read metadata.db for {}:{}: {}",
                    repositoryName,
                    reference,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    private Optional<Path> findMetadataDb(
            Path dockerRoot
    ) {
        if (dockerRoot == null) {
            return Optional.empty();
        }
        String[] directories = new String[] {
                "overlay2", "overlayfs"
        };
        for (String directory : directories) {
            Path candidate = dockerRoot.resolve(
                    "image/" + directory + "/metadata.db"
            );
            if (Files.isRegularFile(
                    candidate
            )) {
                return Optional.of(
                        candidate
                );
            }
        }
        return Optional.empty();
    }

    private Optional<String> findDigest(
            Connection conn,
            String repositoryName,
            String reference
    )
            throws SQLException {
        List<String> candidates = buildCandidateNames(
                repositoryName,
                reference
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<TableSchema> schemas = detectTables(
                conn
        );
        for (TableSchema schema : schemas) {
            Optional<String> digest = tryFindInSchema(
                    conn,
                    schema,
                    candidates
            );
            if (digest.isPresent()) {
                return digest;
            }
        }
        return Optional.empty();
    }

    private List<String> buildCandidateNames(
            String repositoryName,
            String reference
    ) {
        List<String> result = new ArrayList<>();
        if (repositoryName == null || reference == null) {
            return result;
        }
        String fullName = repositoryName + ":" + reference;
        result.add(
                fullName
        );
        if (repositoryName.contains(
                "/"
        ) || repositoryName.contains(
                "."
        )) {
            return result;
        }
        result.add(
                "docker.io/library/" + fullName
        );
        result.add(
                "docker.io/" + fullName
        );
        return result;
    }

    private List<TableSchema> detectTables(
            Connection conn
    )
            throws SQLException {
        List<TableSchema> result = new ArrayList<>();
        try (
                ResultSet tables = conn.getMetaData()
                        .getTables(
                                null,
                                null,
                                "%",
                                new String[] {
                                        "TABLE"
                                }
                        )) {
            while (tables.next()) {
                String tableName = tables.getString(
                        "TABLE_NAME"
                );
                if (tableName == null || tableName.startsWith(
                        "sqlite_"
                )) {
                    continue;
                }
                List<String> columns = detectColumns(
                        conn,
                        tableName
                );
                result.add(
                        new TableSchema(
                                tableName,
                                columns
                        )
                );
            }
        }
        return result;
    }

    private List<String> detectColumns(
            Connection conn,
            String tableName
    )
            throws SQLException {
        List<String> columns = new ArrayList<>();
        try (
                ResultSet rs = conn.createStatement()
                        .executeQuery(
                                "SELECT * FROM " + quote(
                                        tableName
                                ) + " LIMIT 0"
                        )) {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                columns.add(
                        meta.getColumnName(
                                i
                        )
                                .toLowerCase(
                                        Locale.ROOT
                                )
                );
            }
        }
        return columns;
    }

    private String quote(
            String identifier
    ) {
        return "\"" + identifier.replace(
                "\"",
                "\"\""
        ) + "\"";
    }

    private Optional<String> tryFindInSchema(
            Connection conn,
            TableSchema schema,
            List<String> candidates
    )
            throws SQLException {
        String nameColumn = findColumn(
                schema.columns,
                "name",
                "key",
                "namespace"
        );
        if (nameColumn == null) {
            return Optional.empty();
        }
        String valueColumn = findColumn(
                schema.columns,
                "target",
                "image_id",
                "id",
                "digest",
                "value"
        );
        if (valueColumn == null) {
            return Optional.empty();
        }
        String sql = "SELECT " + quote(
                valueColumn
        ) + " FROM " + quote(
                schema.tableName
        ) + " WHERE " + quote(
                nameColumn
        ) + " = ?";
        for (String candidate : candidates) {
            try (
                    PreparedStatement ps = conn.prepareStatement(
                            sql
                    )) {
                ps.setString(
                        1,
                        candidate
                );
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String value = rs.getString(
                                1
                        );
                        Optional<String> digest = extractDigest(
                                value
                        );
                        if (digest.isPresent()) {
                            return digest;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String findColumn(
            List<String> columns,
            String... preferred
    ) {
        for (String pref : preferred) {
            if (columns.contains(
                    pref
            )) {
                return pref;
            }
        }
        return null;
    }

    private Optional<String> extractDigest(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(
                "sha256:"
        )) {
            return Optional.of(
                    trimmed
            );
        }
        try {
            JsonNode node = MAPPER.readTree(
                    trimmed
            );
            Optional<String> fromTarget = extractJsonPath(
                    node,
                    "target",
                    "digest"
            );
            if (fromTarget.isPresent()) {
                return fromTarget;
            }
            Optional<String> fromDigest = extractJsonPath(
                    node,
                    "digest"
            );
            if (fromDigest.isPresent()) {
                return fromDigest;
            }
            Optional<String> fromId = extractJsonPath(
                    node,
                    "id"
            );
            if (fromId.isPresent()) {
                return fromId;
            }
        }
        catch (Exception e) {
            // not JSON, ignore
        }
        return Optional.empty();
    }

    private Optional<String> extractJsonPath(
            JsonNode node,
            String... fields
    ) {
        JsonNode current = node;
        for (String field : fields) {
            if (current == null || !current.isObject()) {
                return Optional.empty();
            }
            current = current.get(
                    field
            );
        }
        if (current != null && current.isTextual()) {
            String text = current.asText();
            if (text.startsWith(
                    "sha256:"
            )) {
                return Optional.of(
                        text
                );
            }
        }
        return Optional.empty();
    }

    private static class TableSchema {

        final String tableName;

        final List<String> columns;

        TableSchema(
                String tableName,
                List<String> columns
        ) {
            this.tableName = tableName;
            this.columns = columns;
        }
    }
}
