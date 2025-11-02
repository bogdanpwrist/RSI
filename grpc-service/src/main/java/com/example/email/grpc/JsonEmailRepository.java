package com.example.email.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists messages into JSON files, one per domain.
 */
public final class JsonEmailRepository {
    private static final Logger LOGGER = Logger.getLogger(JsonEmailRepository.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<List<StoredEmail>> LIST_TYPE = new TypeReference<>() {};

    private final Path storageDir;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public JsonEmailRepository(Path storageDir) {
        this.storageDir = storageDir;
    }

    public void append(String domain, EmailMessage message) {
        Object lock = locks.computeIfAbsent(domain, d -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(storageDir);
                Path file = storageDir.resolve(domain + ".json");
                List<StoredEmail> records = readExisting(file);
                records.add(StoredEmail.from(message));
                MAPPER.writeValue(file.toFile(), records);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to persist email for domain " + domain, ex);
            }
        }
    }

    private List<StoredEmail> readExisting(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            // Check if file is empty
            if (Files.size(file) == 0) {
                return new ArrayList<>();
            }
            List<StoredEmail> stored = MAPPER.readValue(file.toFile(), LIST_TYPE);
            if (stored == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(stored);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to read existing emails from " + file + ", starting fresh", ex);
            return new ArrayList<>();
        }
    }

    private record StoredEmail(String address, String encryptedBody, String originalBody, Instant receivedAt) {
        static StoredEmail from(EmailMessage message) {
            return new StoredEmail(message.address(), message.encryptedBody(), message.originalBody(), message.receivedAt());
        }
    }
}
