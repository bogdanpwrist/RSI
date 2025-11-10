package com.example.email.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ConsumerApplication {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void main(String[] args) throws IOException, TimeoutException {
        Map<String, String> env = System.getenv();

        String host = env.getOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(env.getOrDefault("RABBITMQ_PORT", "5672"));
        String user = env.getOrDefault("RABBITMQ_USER", "guest");
        String pass = env.getOrDefault("RABBITMQ_PASS", "guest");
        String consumerName = env.getOrDefault("CONSUMER_NAME", "consumer");
        String domainFilter = env.getOrDefault("DOMAIN_FILTER", "*");

        DatabaseClient databaseClient = DatabaseClient.fromEnvironment(env, domainFilter);

        System.out.println(consumerName + " starting...");
        System.out.println("Domain filter: " + domainFilter);
        System.out.println("Database bucket: " + databaseClient.bucket());
        System.out.println("Connecting to RabbitMQ at " + host + ":" + port);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(pass);

    com.rabbitmq.client.Connection mqConnection = factory.newConnection();
    Channel channel = mqConnection.createChannel();

        channel.exchangeDeclare("emails", BuiltinExchangeType.TOPIC, true);

        String queueName = channel.queueDeclare(consumerName + "-queue", true, false, false, null).getQueue();

        if ("*".equals(domainFilter)) {
            channel.queueBind(queueName, "emails", "#");
            System.out.println("Bound to ALL domains (will filter out gmail.com and wp.com)");
        } else {
            channel.queueBind(queueName, "emails", domainFilter);
            System.out.println("Bound to domain: " + domainFilter);
        }

        System.out.println(consumerName + " ready. Waiting for messages...");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();
            try {
                if (skipDomain(domainFilter, routingKey)) {
                    System.out.println("Skipping " + routingKey + " (handled by dedicated consumer)");
                    return;
                }

                String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
                EmailMessage email = MAPPER.readValue(payload, EmailMessage.class);

                StoredEmail storedEmail = new StoredEmail(
                        email.address,
                        email.encryptedBody,
                        routingKey,
                        Instant.now()
                );

                databaseClient.save(storedEmail);

                System.out.println("[" + consumerName + "] Persisted message for domain " + routingKey +
                        " into " + databaseClient.bucket());
            } catch (SQLException sqlException) {
                System.err.println("[" + consumerName + "] Database error: " + sqlException.getMessage());
                sqlException.printStackTrace(System.err);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                System.err.println("[" + consumerName + "] Interrupted while writing to database");
            } catch (Exception exception) {
                System.err.println("[" + consumerName + "] Error processing message: " + exception.getMessage());
                exception.printStackTrace(System.err);
            }
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private static boolean skipDomain(String domainFilter, String routingKey) {
        if (!"*".equals(domainFilter)) {
            return false;
        }
        return Objects.equals("gmail.com", routingKey) || Objects.equals("wp.com", routingKey);
    }

    static class EmailMessage {
        public String address;
        public String encryptedBody;
    }

    static class StoredEmail {
        public final String address;
        public final String encryptedBody;
        public final String domain;
        public final Instant timestamp;

        StoredEmail(String address, String encryptedBody, String domain, Instant timestamp) {
            this.address = address;
            this.encryptedBody = encryptedBody;
            this.domain = domain;
            this.timestamp = timestamp;
        }
    }

    @FunctionalInterface
    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    static final class DatabaseClient {
        private static final int DEFAULT_MAX_RETRIES = 15;
        private static final long DEFAULT_RETRY_DELAY_MS = 2000L;

        private final String bucket;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final int maxRetries;
        private final long retryDelayMillis;
    private final Object schemaLock = new Object();
    private volatile boolean schemaEnsured = false;

        private DatabaseClient(String bucket,
                               String jdbcUrl,
                               String username,
                               String password,
                               int maxRetries,
                               long retryDelayMillis) {
            this.bucket = bucket;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.maxRetries = maxRetries;
            this.retryDelayMillis = retryDelayMillis;
        }

        static DatabaseClient fromEnvironment(Map<String, String> env, String domainFilter) {
            String bucket = env.getOrDefault("STORAGE_BUCKET", domainFilter);
            String explicitUrl = env.get("DB_URL");

            String host = env.getOrDefault("DB_HOST", hostFromBucket(bucket));
            String port = env.getOrDefault("DB_PORT", "5432");
            String dbName = env.getOrDefault("DB_DATABASE", bucket.replace('.', '_'));
            String user = env.getOrDefault("DB_USER", "email_user");
            String password = env.getOrDefault("DB_PASS", "email_pass");
            int retries = Integer.parseInt(env.getOrDefault("DB_CONNECT_RETRIES", String.valueOf(DEFAULT_MAX_RETRIES)));
            long delay = Long.parseLong(env.getOrDefault("DB_CONNECT_DELAY_MS", String.valueOf(DEFAULT_RETRY_DELAY_MS)));

            String jdbcUrl = Optional.ofNullable(explicitUrl)
                    .filter(url -> !url.isBlank())
                    .orElse("jdbc:postgresql://" + host + ":" + port + "/" + dbName);

            return new DatabaseClient(bucket, jdbcUrl, user, password, retries, delay);
        }

        String bucket() {
            return bucket;
        }

        void save(StoredEmail email) throws SQLException, InterruptedException {
            executeWithRetry(connection -> {
                ensureSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO emails(address, encrypted_body, domain, created_at) VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, email.address);
                    statement.setString(2, email.encryptedBody);
                    statement.setString(3, email.domain);
                    statement.setTimestamp(4, Timestamp.from(email.timestamp));
                    statement.executeUpdate();
                }
            });
        }

        private void executeWithRetry(SqlConsumer<Connection> operation) throws SQLException, InterruptedException {
            SQLException lastException = null;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try (Connection connection = openConnection()) {
                    operation.accept(connection);
                    return;
                } catch (SQLException ex) {
                    lastException = ex;
                    System.err.println("[DatabaseClient] Attempt " + attempt + " failed: " + ex.getMessage());
                    if (attempt < maxRetries) {
                        Thread.sleep(retryDelayMillis);
                    }
                }
            }
            throw lastException != null ? lastException : new SQLException("Unknown database error");
        }

        private Connection openConnection() throws SQLException {
            if (username == null || username.isBlank()) {
                return java.sql.DriverManager.getConnection(jdbcUrl);
            }
            return java.sql.DriverManager.getConnection(jdbcUrl, username, password);
        }

        private void ensureSchema(Connection connection) throws SQLException {
            if (schemaEnsured) {
                return;
            }
            synchronized (schemaLock) {
                if (schemaEnsured) {
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
                    schemaEnsured = true;
                } catch (SQLException ex) {
                    schemaEnsured = false;
                    throw ex;
                }
            }
        }

        private static String hostFromBucket(String bucket) {
            return switch (bucket) {
                case "gmail.com" -> "gmail-db";
                case "wp.com" -> "wp-db";
                case "other" -> "other-db";
                default -> "localhost";
            };
        }
    }
}
