package com.example.email.rest;

/**
 * Exception thrown when there is a conflict in the requested operation.
 * HTTP Status: 409 Conflict
 */
public class ConflictException extends RuntimeException {
    
    public ConflictException(String message) {
        super(message);
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
