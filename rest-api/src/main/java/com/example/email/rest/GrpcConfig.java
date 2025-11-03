package com.example.email.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {
    
    @Value("${grpc.target:localhost:50051}")
    private String grpcTarget;
    
    @Bean
    public GrpcEmailClient grpcEmailClient() {
        return new GrpcEmailClient(grpcTarget);
    }
}
