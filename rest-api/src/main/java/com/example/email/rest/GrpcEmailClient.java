package com.example.email.rest;

import com.example.email.proto.EmailServiceGrpc;
import com.example.email.proto.SendEmailReply;
import com.example.email.proto.SendEmailRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a gRPC blocking stub with simple logging.
 */
public final class GrpcEmailClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GrpcEmailClient.class.getName());
    private final ManagedChannel channel;
    private final EmailServiceGrpc.EmailServiceBlockingStub stub;

    public GrpcEmailClient(String target) {
        this.channel = buildChannel(target);
        this.stub = EmailServiceGrpc.newBlockingStub(channel);
        LOGGER.info(() -> "Connected to gRPC target " + target);
    }

    public SendEmailReply send(EmailPayload payload) {
        SendEmailRequest request = SendEmailRequest.newBuilder()
                .setAddress(payload.address())
                .setBody(payload.body())
                .build();
        SendEmailReply reply = stub.sendEmail(request);
        LOGGER.info(() -> "Received gRPC reply: " + reply.getStatus());
        return reply;
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
        int port = 50051;
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
