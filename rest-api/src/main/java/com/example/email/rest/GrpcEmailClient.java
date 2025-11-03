package com.example.email.rest;

import com.example.email.proto.EmailServiceGrpc;
import com.example.email.proto.SendEmailReply;
import com.example.email.proto.SendEmailRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class GrpcEmailClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GrpcEmailClient.class.getName());
    private final ManagedChannel channel;
    private final EmailServiceGrpc.EmailServiceStub asyncStub;

    public GrpcEmailClient(String target) {
        this.channel = buildChannel(target);
        this.asyncStub = EmailServiceGrpc.newStub(channel);
        LOGGER.info(() -> "Connected to gRPC target (async) " + target);
    }

    public CompletableFuture<SendEmailReply> sendAsync(EmailPayload payload) {
        CompletableFuture<SendEmailReply> future = new CompletableFuture<>();
        
        SendEmailRequest request = SendEmailRequest.newBuilder()
                .setAddress(payload.address())
                .setBody(payload.body())
                .build();
        
        LOGGER.info(() -> "Sending async gRPC request for " + payload.address());
        
        asyncStub.sendEmail(request, new StreamObserver<SendEmailReply>() {
            private SendEmailReply response;
            
            @Override
            public void onNext(SendEmailReply value) {
                response = value;
                LOGGER.info(() -> "Received async gRPC reply: " + value.getStatus());
            }
            
            @Override
            public void onError(Throwable t) {
                LOGGER.log(Level.SEVERE, "gRPC async call failed", t);
                future.completeExceptionally(t);
            }
            
            @Override
            public void onCompleted() {
                if (response != null) {
                    future.complete(response);
                } else {
                    future.completeExceptionally(new RuntimeException("No response received"));
                }
            }
        });
        
        return future;
    }

    public SendEmailReply send(EmailPayload payload) {
        try {
            return sendAsync(payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get async response", e);
            throw new RuntimeException("gRPC call failed", e);
        }
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warning("Forcing gRPC channel shutdown.");
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while closing gRPC channel", ex);
            channel.shutdownNow();
        }
    }

    private ManagedChannel buildChannel(String target) {
        if (target.contains("://")) {
            return ManagedChannelBuilder.forTarget(target)
                    .usePlaintext()
                    .build();
        }

        String host = target;
        int port = 50001; // Default port matching GrpcServer
        int separatorIndex = target.lastIndexOf(':');
        if (separatorIndex > -1 && separatorIndex < target.length() - 1) {
            host = target.substring(0, separatorIndex);
            port = Integer.parseInt(target.substring(separatorIndex + 1));
        }

        LOGGER.info("Connecting to gRPC via host:port → " + host + ":" + port);
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }
}
