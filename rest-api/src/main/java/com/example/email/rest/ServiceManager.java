package com.example.email.rest;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the status of email services (Gmail, WP, Other).
 */
@Service
public class ServiceManager {
    
    private final Map<String, ServiceStatus> serviceStatus = new ConcurrentHashMap<>();
    
    public ServiceManager() {
        // Initialize all services as active by default
        serviceStatus.put("gmail", ServiceStatus.ACTIVE);
        serviceStatus.put("wp", ServiceStatus.ACTIVE);
        serviceStatus.put("other", ServiceStatus.ACTIVE);
    }
    
    /**
     * Check if a service is active and available.
     * @param serviceName Service name (gmail, wp, other)
     * @return true if active, false otherwise
     */
    public boolean isServiceEnabled(String serviceName) {
        if (serviceName == null) {
            return false;
        }
        ServiceStatus status = serviceStatus.getOrDefault(serviceName.toLowerCase(), ServiceStatus.DISABLED);
        return status == ServiceStatus.ACTIVE;
    }
    
    /**
     * Get the status of a service.
     * @param serviceName Service name (gmail, wp, other)
     * @return ServiceStatus
     */
    public ServiceStatus getServiceStatus(String serviceName) {
        if (serviceName == null) {
            return ServiceStatus.DISABLED;
        }
        return serviceStatus.getOrDefault(serviceName.toLowerCase(), ServiceStatus.DISABLED);
    }
    
    /**
     * Set the status of a service.
     * @param serviceName Service name (gmail, wp, other)
     * @param status New status
     * @return true if successful, false if invalid service name
     */
    public boolean setServiceStatus(String serviceName, ServiceStatus status) {
        if (serviceName == null || status == null) {
            return false;
        }
        String normalizedName = serviceName.toLowerCase();
        if (!serviceStatus.containsKey(normalizedName)) {
            return false;
        }
        serviceStatus.put(normalizedName, status);
        return true;
    }
    
    /**
     * Get the status of all services.
     * @return Map of service names to their status
     */
    public Map<String, ServiceStatus> getAllServicesStatus() {
        return new HashMap<>(serviceStatus);
    }
    
    /**
     * Check if service exists.
     * @param serviceName Service name
     * @return true if service exists
     */
    public boolean serviceExists(String serviceName) {
        if (serviceName == null) {
            return false;
        }
        return serviceStatus.containsKey(serviceName.toLowerCase());
    }
}
