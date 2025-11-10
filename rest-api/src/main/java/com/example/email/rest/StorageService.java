package com.example.email.rest;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private static final Logger LOGGER = Logger.getLogger(StorageService.class.getName());

    @Value("${storage.gmail.url:jdbc:postgresql://gmail-db:5432/gmail_store}")
    private String gmailUrl;
    @Value("${storage.gmail.user:email_user}")
    private String gmailUser;
    @Value("${storage.gmail.password:email_pass}")
    private String gmailPassword;

    @Value("${storage.wp.url:jdbc:postgresql://wp-db:5432/wp_store}")
    private String wpUrl;
    @Value("${storage.wp.user:email_user}")
    private String wpUser;
    @Value("${storage.wp.password:email_pass}")
    private String wpPassword;

    @Value("${storage.other.url:jdbc:postgresql://other-db:5432/other_store}")
    private String otherUrl;
    @Value("${storage.other.user:email_user}")
    private String otherUser;
    @Value("${storage.other.password:email_pass}")
    private String otherPassword;

    private final Map<String, DatabaseClient> clients = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        registerClient("gmail.com", gmailUrl, gmailUser, gmailPassword);
        registerClient("wp.com", wpUrl, wpUser, wpPassword);
        registerClient("other", otherUrl, otherUser, otherPassword);
        LOGGER.info(() -> "StorageService configured for domains: " + String.join(", ", clients.keySet()));
    }

    public Map<String, Object> getAllStorages() throws IOException {
        ensureConfigured();

        try {
            Map<String, List<StoredEmail>> domainEmails = new LinkedHashMap<>();
            int totalCount = 0;

            for (Map.Entry<String, DatabaseClient> entry : clients.entrySet()) {
                List<StoredEmail> emails = entry.getValue().fetchAll();
                domainEmails.put(entry.getKey(), emails);
                totalCount += emails.size();
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("domains", new ArrayList<>(clients.keySet()));
            response.put("emails", domainEmails);
            response.put("totalEmails", totalCount);
            response.put("storageType", "database");
            response.put("databases", clients.entrySet().stream()
                    .map(entry -> Map.of(
                            "domain", entry.getKey(),
                            "url", entry.getValue().jdbcUrl))
                    .collect(Collectors.toList()));
            return response;
        } catch (SQLException ex) {
            throw new IOException("Failed to read storage databases", ex);
        }
    }

    public Map<String, Object> getDomainStorage(String domain) throws IOException {
        ensureConfigured();

        String key = normalizeDomain(domain);
        DatabaseClient client = clients.get(key);

        if (client == null) {
            return Map.of(
                    "domain", domain,
                    "emails", List.of(),
                    "count", 0,
                    "message", "No storage configured for domain: " + domain
            );
        }

        try {
            List<StoredEmail> emails = client.fetchAll();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("domain", key);
            response.put("emails", emails);
            response.put("count", emails.size());
            return response;
        } catch (SQLException ex) {
            throw new IOException("Failed to read storage database for domain: " + key, ex);
        }
    }

    public Map<String, Object> clearAllStorages() throws IOException {
        ensureConfigured();

        try {
            int clearedRows = 0;
            int clearedBuckets = 0;

            for (DatabaseClient client : clients.values()) {
                clearedRows += client.clear();
                clearedBuckets++;
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deletedEmails", clearedRows);
            response.put("deletedFiles", clearedBuckets);
            response.put("storageType", "database");
            response.put("databases", new ArrayList<>(clients.keySet()));
            return response;
        } catch (SQLException ex) {
            throw new IOException("Failed to clear storage databases", ex);
        }
    }

    private void ensureConfigured() throws IOException {
        if (clients.isEmpty()) {
            throw new IOException("No storage databases configured");
        }
    }

    private void registerClient(String domainKey, String url, String user, String password) {
        if (url == null || url.isBlank()) {
            LOGGER.warning("Skipping storage registration for " + domainKey + " - URL missing");
            return;
        }
        clients.put(domainKey, new DatabaseClient(domainKey, url, user, password));
    }

    private String normalizeDomain(String domain) {
        if (domain == null) {
            return "other";
        }
        String trimmed = domain.trim();
        if (Objects.equals("gmail.com", trimmed)) {
            return "gmail.com";
        }
        if (Objects.equals("wp.com", trimmed)) {
            return "wp.com";
        }
        return "other";
    }

    public static final class StoredEmail {
        public final String address;
        public final String encryptedBody;
        public final String domain;
        public final Instant timestamp;

        public StoredEmail(String address, String encryptedBody, String domain, Instant timestamp) {
            this.address = address;
            this.encryptedBody = encryptedBody;
            this.domain = domain;
            this.timestamp = timestamp;
        }
    }

    private static final class DatabaseClient {
        private final String bucket;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final Object schemaLock = new Object();
        private volatile boolean schemaReady = false;

        private DatabaseClient(String bucket, String jdbcUrl, String username, String password) {
            this.bucket = bucket;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        List<StoredEmail> fetchAll() throws SQLException {
            try (Connection connection = openConnection()) {
                ensureSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT address, encrypted_body, domain, created_at FROM emails ORDER BY created_at DESC")) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<StoredEmail> emails = new ArrayList<>();
                        while (resultSet.next()) {
                            String address = resultSet.getString("address");
                            String encrypted = resultSet.getString("encrypted_body");
                            String domain = resultSet.getString("domain");
                            Timestamp timestamp = resultSet.getTimestamp("created_at");
                            Instant instant = timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
                            emails.add(new StoredEmail(address, encrypted, domain, instant));
                        }
                        return emails;
                    }
                }
            }
        }

        int clear() throws SQLException {
            try (Connection connection = openConnection()) {
                ensureSchema(connection);
                try (Statement statement = connection.createStatement()) {
                    return statement.executeUpdate("DELETE FROM emails");
                }
            }
        }

        private Connection openConnection() throws SQLException {
            if (username == null || username.isBlank()) {
                return DriverManager.getConnection(jdbcUrl);
            }
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        private void ensureSchema(Connection connection) throws SQLException {
            if (schemaReady) {
                return;
            }
            synchronized (schemaLock) {
                if (schemaReady) {
                    return;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS emails (
                            id SERIAL PRIMARY KEY,
                            address TEXT NOT NULL,
                            encrypted_body TEXT NOT NULL,
                            domain TEXT NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL
                        )
                        """);
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_emails_created_at ON emails(created_at DESC)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_emails_domain_created ON emails(domain, created_at DESC)");
                    schemaReady = true;
                } catch (SQLException ex) {
                    schemaReady = false;
                    LOGGER.log(Level.SEVERE, "Failed to prepare schema for " + bucket, ex);
                    throw ex;
                }
            }
        }
    }
}
