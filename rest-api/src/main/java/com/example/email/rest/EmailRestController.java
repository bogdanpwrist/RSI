package com.example.email.rest;

import com.example.email.proto.SendEmailReply;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Tag(name = "Email")
public class EmailRestController {
    
    private static final Logger LOGGER = Logger.getLogger(EmailRestController.class.getName());
    
    @Autowired
    private GrpcEmailClient grpcClient;
    
    @Autowired
    private RabbitMQPublisher rabbitPublisher;
    
    @Autowired
    private StorageService storageService;
    
    @PostMapping("/email")
    @Operation(summary = "Accept email payload and trigger async encryption workflow")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailPayload payload) {
        if (payload == null || !payload.isValid()) {
            LOGGER.warning("Received invalid payload");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid email payload"));
        }

        LOGGER.info(() -> "REST received email for " + payload.address() + ". Processing asynchronously.");

        CompletableFuture<SendEmailReply> grpcFuture = grpcClient.sendAsync(payload);

        grpcFuture.thenAccept(reply -> {
            if ("SUCCESS".equals(reply.getStatus())) {
                try {
                    String encryptedBody = reply.getDetails();
                    if (encryptedBody.startsWith("Encrypted: ")) {
                        encryptedBody = encryptedBody.substring("Encrypted: ".length());
                    }

                    rabbitPublisher.publishEmail(payload.address(), encryptedBody);
                    LOGGER.info("Async task completed: Email for " + payload.address() + " encrypted and published to RabbitMQ.");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to publish to RabbitMQ in async chain for " + payload.address(), e);
                }
            } else {
                 LOGGER.warning("gRPC call was not successful in async chain: " + reply.getDetails());
            }
        }).exceptionally(ex -> {
            LOGGER.log(Level.SEVERE, "gRPC call failed in async chain for " + payload.address(), ex);
            return null; // Musimy zwrócić null, to standard w exceptionally
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "ACCEPTED", "message", "Request accepted for processing."));
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check for Kubernetes/containers")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    
    @GetMapping("/storage")
    @Operation(summary = "Fetch every stored email grouped by domain")
    public ResponseEntity<?> getAllStorages() {
        try {
            Map<String, Object> storages = storageService.getAllStorages();
            return ResponseEntity.ok(storages);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to read storages", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read storage: " + ex.getMessage()));
        }
    }
    
    @GetMapping("/storage/{domain}")
    @Operation(summary = "Return stored emails for a specific domain")
    public ResponseEntity<?> getDomainStorage(@PathVariable String domain) {
        try {
            Map<String, Object> storage = storageService.getDomainStorage(domain);
            return ResponseEntity.ok(storage);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to read storage for domain: " + domain, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read storage: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/storage")
    @Operation(summary = "Remove all stored emails produced by RabbitMQ consumers")
    public ResponseEntity<?> clearStorages() {
        try {
            Map<String, Object> result = storageService.clearAllStorages();
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to clear storages", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to clear storage: " + ex.getMessage()));
        }
    }
}
