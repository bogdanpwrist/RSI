package com.example.email.grpc;

import java.time.Instant;

/**
 * Represents an email at different processing stages.
 */
public record EmailMessage(String address, String originalBody, String encryptedBody, Instant receivedAt) {
    public String domain() {
        int atIndex = address.indexOf('@');
        if (atIndex == -1 || atIndex == address.length() - 1) {
            return "unknown";
        }
        return address.substring(atIndex + 1).toLowerCase();
    }
}
