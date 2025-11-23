package com.example.email.rest;

import com.example.email.proto.SendEmailReply;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

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
    
    @Autowired
    private ServiceManager serviceManager;
    
    @PostMapping("/email")
    @Operation(summary = "Accept email payload and trigger async encryption workflow")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailPayload payload) {
        if (payload == null || !payload.isValid()) {
            LOGGER.warning("Received invalid payload");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid email payload"));
        }
        
        // Check if service is enabled for this email domain
        String domain = extractDomain(payload.address());
        if (!serviceManager.isServiceEnabled(domain)) {
            LOGGER.warning("Service disabled for domain: " + domain);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service is currently disabled for " + domain + " emails"));
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
    
    @GetMapping("/services/status")
    @Operation(summary = "Get status of all email services")
    public ResponseEntity<Map<String, ServiceStatus>> getServicesStatus() {
        return ResponseEntity.ok(serviceManager.getAllServicesStatus());
    }
    
    @GetMapping("/services/{serviceName}")
    @Operation(summary = "Get a specific service with HATEOAS links")
    public ResponseEntity<?> getService(@PathVariable String serviceName) {
        if (!serviceManager.serviceExists(serviceName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Service not found: " + serviceName));
        }
        
        ServiceStatus status = serviceManager.getServiceStatus(serviceName);
        ServiceInfo serviceInfo = new ServiceInfo(serviceName, status);
        
        EntityModel<ServiceInfo> em = EntityModel.of(serviceInfo);
        
        // Add self link
        em.add(linkTo(methodOn(EmailRestController.class).getService(serviceName))
                .withSelfRel());
        
        // Add links based on current status
        if (status == ServiceStatus.ACTIVE) {
            em.add(linkTo(methodOn(EmailRestController.class).disableService(serviceName))
                    .withRel("disable"));
        } else if (status == ServiceStatus.DISABLED) {
            em.add(linkTo(methodOn(EmailRestController.class).activateService(serviceName))
                    .withRel("activate"));
        }
        
        // Add link to list all services
        em.add(linkTo(methodOn(EmailRestController.class).getServicesStatus())
                .withRel("list all"));
        
        return ResponseEntity.ok(em);
    }
    
    @PatchMapping("/services/{serviceName}/activate")
    @Operation(summary = "Activate a service")
    public ResponseEntity<?> activateService(@PathVariable String serviceName) {
        if (!serviceManager.serviceExists(serviceName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Service not found: " + serviceName));
        }
        
        ServiceStatus currentStatus = serviceManager.getServiceStatus(serviceName);
        
        if (currentStatus == ServiceStatus.ACTIVE) {
            throw new ConflictException("You CAN'T activate a service with status " + currentStatus);
        }
        
        // Change status to ACTIVE
        serviceManager.setServiceStatus(serviceName, ServiceStatus.ACTIVE);
        ServiceInfo serviceInfo = new ServiceInfo(serviceName, ServiceStatus.ACTIVE);
        
        EntityModel<ServiceInfo> em = EntityModel.of(serviceInfo);
        
        // Add links for ACTIVE service
        em.add(linkTo(methodOn(EmailRestController.class).getService(serviceName))
                .withSelfRel());
        em.add(linkTo(methodOn(EmailRestController.class).disableService(serviceName))
                .withRel("disable"));
        em.add(linkTo(methodOn(EmailRestController.class).getServicesStatus())
                .withRel("list all"));
        
        LOGGER.info("Service " + serviceName + " has been activated");
        return ResponseEntity.ok(em);
    }
    
    @PatchMapping("/services/{serviceName}/disable")
    @Operation(summary = "Disable a service")
    public ResponseEntity<?> disableService(@PathVariable String serviceName) {
        if (!serviceManager.serviceExists(serviceName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Service not found: " + serviceName));
        }
        
        ServiceStatus currentStatus = serviceManager.getServiceStatus(serviceName);
        
        if (currentStatus == ServiceStatus.DISABLED) {
            throw new ConflictException("You CAN'T disable a service with status " + currentStatus);
        }
        
        // Change status to DISABLED
        serviceManager.setServiceStatus(serviceName, ServiceStatus.DISABLED);
        ServiceInfo serviceInfo = new ServiceInfo(serviceName, ServiceStatus.DISABLED);
        
        EntityModel<ServiceInfo> em = EntityModel.of(serviceInfo);
        
        // Add links for DISABLED service
        em.add(linkTo(methodOn(EmailRestController.class).getService(serviceName))
                .withSelfRel());
        em.add(linkTo(methodOn(EmailRestController.class).activateService(serviceName))
                .withRel("activate"));
        em.add(linkTo(methodOn(EmailRestController.class).getServicesStatus())
                .withRel("list all"));
        
        LOGGER.info("Service " + serviceName + " has been disabled");
        return ResponseEntity.ok(em);
    }
    
    
    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return "other";
        }
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (domain.contains("gmail")) {
            return "gmail";
        } else if (domain.contains("wp")) {
            return "wp";
        } else {
            return "other";
        }
    }
}
