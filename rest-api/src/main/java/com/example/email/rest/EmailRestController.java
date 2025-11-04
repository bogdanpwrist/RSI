package com.example.email.rest;

import com.example.email.proto.SendEmailReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EmailRestController {
    
    private static final Logger LOGGER = Logger.getLogger(EmailRestController.class.getName());
    
    @Autowired
    private GrpcEmailClient grpcClient;
    
    @Autowired
    private RabbitMQPublisher rabbitPublisher;
    
    @Autowired
    private StorageService storageService;
    
    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailPayload payload) {
        try {
            if (payload == null || !payload.isValid()) {
                LOGGER.warning("Received invalid payload");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid email payload"));
            }
            
            LOGGER.info(() -> "REST received email for " + payload.address());
            
            // Step 1: Send to gRPC for encryption
            SendEmailReply reply = grpcClient.send(payload);
            
            if ("SUCCESS".equals(reply.getStatus())) {

                String encryptedBody = reply.getDetails();
                if (encryptedBody.startsWith("Encrypted: ")) {
                    encryptedBody = encryptedBody.substring("Encrypted: ".length());
                }
                
                rabbitPublisher.publishEmail(payload.address(), encryptedBody);
                
                LOGGER.info("Email encrypted and published to RabbitMQ");
            }
            
            return ResponseEntity.ok(Map.of(
                    "status", reply.getStatus(),
                    "details", reply.getDetails(),
                    "message", "Email encrypted and queued for storage"));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to process request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    
    @GetMapping("/storage")
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
}
