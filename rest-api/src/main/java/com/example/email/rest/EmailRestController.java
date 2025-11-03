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
    
    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailPayload payload) {
        try {
            if (payload == null || !payload.isValid()) {
                LOGGER.warning("Received invalid payload");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid email payload"));
            }
            
            LOGGER.info(() -> "REST received email for " + payload.address());
            SendEmailReply reply = grpcClient.send(payload);
            
            return ResponseEntity.ok(Map.of(
                    "status", reply.getStatus(),
                    "details", reply.getDetails()));
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
}
