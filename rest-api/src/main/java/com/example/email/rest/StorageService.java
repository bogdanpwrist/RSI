package com.example.email.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class StorageService {
    
    private static final Logger LOGGER = Logger.getLogger(StorageService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    @Value("${storage.dir:/data/storage}")
    private String storageDir;
    
    /**
     * Get all stored emails grouped by domain
     */
    public Map<String, Object> getAllStorages() throws IOException {
        Path storagePath = Paths.get(storageDir);
        
        if (!Files.exists(storagePath)) {
            LOGGER.warning("Storage directory does not exist: " + storageDir);
            return Map.of(
                "domains", List.of(),
                "totalEmails", 0,
                "message", "Storage directory not found"
            );
        }
        
        Map<String, List<StoredEmail>> domainEmails = new HashMap<>();
        int totalCount = 0;
        
        File[] files = storagePath.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        
        if (files != null) {
            for (File file : files) {
                String domain = file.getName().replace(".json", "");
                try {
                    List<StoredEmail> emails = readStorageFile(file);
                    domainEmails.put(domain, emails);
                    totalCount += emails.size();
                } catch (Exception e) {
                    LOGGER.warning("Failed to read " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        
        return Map.of(
            "domains", domainEmails.keySet().stream().sorted().collect(Collectors.toList()),
            "emails", domainEmails,
            "totalEmails", totalCount,
            "storageDir", storageDir
        );
    }
    
    /**
     * Get emails for a specific domain
     */
    public Map<String, Object> getDomainStorage(String domain) throws IOException {
        Path filePath = Paths.get(storageDir, domain + ".json");
        
        if (!Files.exists(filePath)) {
            return Map.of(
                "domain", domain,
                "emails", List.of(),
                "count", 0,
                "message", "No emails found for domain: " + domain
            );
        }
        
        List<StoredEmail> emails = readStorageFile(filePath.toFile());
        
        return Map.of(
            "domain", domain,
            "emails", emails,
            "count", emails.size()
        );
    }
    
    private List<StoredEmail> readStorageFile(File file) throws IOException {
        if (file.length() == 0) {
            return new ArrayList<>();
        }
        
        StoredEmail[] array = MAPPER.readValue(file, StoredEmail[].class);
        return Arrays.asList(array);
    }
    
    public static class StoredEmail {
        public String address;
        public String encryptedBody;
        public Instant timestamp;
        
        public StoredEmail() {}
        
        public StoredEmail(String address, String encryptedBody, Instant timestamp) {
            this.address = address;
            this.encryptedBody = encryptedBody;
            this.timestamp = timestamp;
        }
    }
}
