package com.example.email.rest;

/**
 * DTO representing a service with its status.
 */
public class ServiceInfo {
    private String name;
    private ServiceStatus status;
    
    public ServiceInfo() {
    }
    
    public ServiceInfo(String name, ServiceStatus status) {
        this.name = name;
        this.status = status;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public ServiceStatus getStatus() {
        return status;
    }
    
    public void setStatus(ServiceStatus status) {
        this.status = status;
    }
}
