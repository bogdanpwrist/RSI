package com.example.email.grpc;

import com.example.email.proto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrpcServer {
    
    public static void main(String[] args) {
        int port = 50001;
        System.out.println("Starting Async Email Encryption gRPC Server on port " + port + "...");
        
        Server server = ServerBuilder.forPort(port)
                .addService(new EmailServiceImpl())
                .build();
        
        try {
            server.start();
            System.out.println("Async email encryption service started successfully!");
            server.awaitTermination();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    // Async Email service implementation
    static class EmailServiceImpl extends EmailServiceGrpc.EmailServiceImplBase {
        private final ExecutorService executor = Executors.newFixedThreadPool(10);
        
        @Override
        public void sendEmail(SendEmailRequest request, StreamObserver<SendEmailReply> responseObserver) {
            System.out.println("\n[Async Email] Request received: " + request.getAddress());
            
            executor.submit(() -> {
                try {
                    System.out.println("[Async Email] Processing in thread: " + Thread.currentThread().getName());
                    
                    
                    String encrypted = Base64.getEncoder().encodeToString(request.getBody().getBytes());
                    System.out.println("[Async Email] Encrypted: " + encrypted.substring(0, Math.min(30, encrypted.length())) + "...");
                    
                    SendEmailReply reply = SendEmailReply.newBuilder()
                            .setStatus("SUCCESS")
                            .setDetails("Encrypted: " + encrypted)
                            .build();
                    
                    // Send response asynchronously
                    responseObserver.onNext(reply);
                    responseObserver.onCompleted();
                    System.out.println("[Async Email] Response sent successfully");
                    
                } catch (Exception e) {
                    System.err.println("[Async Email] Error: " + e.getMessage());
                    SendEmailReply errorReply = SendEmailReply.newBuilder()
                            .setStatus("ERROR")
                            .setDetails("Failed: " + e.getMessage())
                            .build();
                    
                    responseObserver.onNext(errorReply);
                    responseObserver.onCompleted();
                }
            });
        }
    }
}
