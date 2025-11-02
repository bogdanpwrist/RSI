package com.example.email.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.logging.Logger;

public final class GrpcEmailServer {
    private static final Logger LOGGER = Logger.getLogger(GrpcEmailServer.class.getName());

    private GrpcEmailServer() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));
        
        String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        int rabbitPort = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        String rabbitUser = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String rabbitPass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");

        LOGGER.info("Starting gRPC Email Service on port " + port);
        LOGGER.info("RabbitMQ: " + rabbitHost + ":" + rabbitPort);

        RabbitMqPublisher publisher = new RabbitMqPublisher(rabbitHost, rabbitPort, rabbitUser, rabbitPass);
        EmailServiceImpl service = new EmailServiceImpl(publisher);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .build()
                .start();

        LOGGER.info("gRPC Server started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down gRPC server...");
            try {
                publisher.close();
                server.shutdown();
            } catch (Exception e) {
                LOGGER.severe("Error during shutdown: " + e.getMessage());
            }
        }));

        server.awaitTermination();
    }
}

