package com.example.email.grpc;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Real RabbitMQ publisher that sends encrypted emails to domain-specific routing keys
 */
public class RabbitMqPublisher implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(RabbitMqPublisher.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Connection connection;
    private final Channel channel;
    private final String exchangeName = "emails";

    public RabbitMqPublisher(String host, int port, String username, String password) 
            throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        LOGGER.info("Connecting to RabbitMQ at " + host + ":" + port);
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
        
        // Declare topic exchange
        channel.exchangeDeclare(exchangeName, "topic", true);
        LOGGER.info("RabbitMQ publisher initialized");
    }

    public void publish(String domain, EmailMessage message) {
        try {
            String json = MAPPER.writeValueAsString(new PublishMessage(
                message.address(),
                message.encryptedBody()
            ));
            
            // Publish to exchange with domain as routing key
            channel.basicPublish(exchangeName, domain, null, json.getBytes("UTF-8"));
            LOGGER.info("Published message to domain: " + domain);
            
        } catch (IOException e) {
            LOGGER.severe("Failed to publish message: " + e.getMessage());
            throw new RuntimeException("Failed to publish to RabbitMQ", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
        LOGGER.info("RabbitMQ connection closed");
    }

    private static class PublishMessage {
        public String address;
        public String encryptedBody;

        public PublishMessage(String address, String encryptedBody) {
            this.address = address;
            this.encryptedBody = encryptedBody;
        }
    }
}
