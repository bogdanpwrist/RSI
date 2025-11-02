package com.example.email.rest;

/**
 * Represents the incoming JSON payload from the frontend.
 */
public record EmailPayload(String address, String body) {
    public boolean isValid() {
        return address != null && address.contains("@") && body != null && !body.isBlank();
    }
}
