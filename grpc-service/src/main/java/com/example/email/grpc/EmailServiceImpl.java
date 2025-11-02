package com.example.email.grpc;

import com.example.email.proto.EmailServiceGrpc;
import com.example.email.proto.SendEmailReply;
import com.example.email.proto.SendEmailRequest;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Handles gRPC requests, encrypts bodies, and publishes to RabbitMQ.
 */
public final class EmailServiceImpl extends EmailServiceGrpc.EmailServiceImplBase {
    private static final Logger LOGGER = Logger.getLogger(EmailServiceImpl.class.getName());
    private final RabbitMqPublisher publisher;

    public EmailServiceImpl(RabbitMqPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendEmail(SendEmailRequest request, StreamObserver<SendEmailReply> responseObserver) {
        String encryptedBody = SimpleEncryptor.shiftByOne(request.getBody());
        EmailMessage message = new EmailMessage(request.getAddress(), request.getBody(), encryptedBody, Instant.now());
        String domain = message.domain();
        LOGGER.info(() -> "Encrypting message for domain " + domain);
        publisher.publish(domain, message);
        SendEmailReply reply = SendEmailReply.newBuilder()
                .setStatus("QUEUED")
                .setDetails("Message queued for domain " + domain)
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
