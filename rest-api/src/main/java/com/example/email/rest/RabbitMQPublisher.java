package com.example.email.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class RabbitMQPublisher {
    
    private static final Logger LOGGER = Logger.getLogger(RabbitMQPublisher.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Value("${rabbitmq.host:localhost}")
    private String host;
    
    @Value("${rabbitmq.port:5672}")
    private int port;
    
    @Value("${rabbitmq.user:guest}")
    private String user;
    
    @Value("${rabbitmq.pass:guest}")
    private String pass;
    
    private Connection connection;
    private Channel channel;
    private boolean initialized = false;
    
    private synchronized void ensureConnected() throws IOException, TimeoutException {
        if (initialized && connection != null && connection.isOpen()) {
            return;
        }
        
        LOGGER.info("Connecting to RabbitMQ at " + host + ":" + port + "...");
        
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(pass);
        factory.setConnectionTimeout(5000);
        factory.setRequestedHeartbeat(30);
        
        connection = factory.newConnection();
        channel = connection.createChannel();
        
        // Declare topic exchange
        channel.exchangeDeclare("emails", "topic", true);
        
        initialized = true;
        LOGGER.info("RabbitMQ publisher connected successfully");
    }
    
    public void publishEmail(String address, String encryptedBody) throws IOException {
        try {
            ensureConnected();
            
            String domain = extractDomain(address);
            
            EmailMessage message = new EmailMessage(address, encryptedBody);
            String json = MAPPER.writeValueAsString(message);
            
            channel.basicPublish("emails", domain, null, json.getBytes("UTF-8"));
            
            LOGGER.info("Published email to RabbitMQ with routing key: " + domain);
        } catch (TimeoutException e) {
            LOGGER.log(Level.SEVERE, "Timeout connecting to RabbitMQ", e);
            throw new IOException("RabbitMQ connection timeout", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to publish to RabbitMQ", e);
            initialized = false; // Reset connection state
            throw e;
        }
    }
    
    private String extractDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0 && atIndex < email.length() - 1) {
            return email.substring(atIndex + 1);
        }
        return "unknown";
    }
    
    @PreDestroy
    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            LOGGER.info("RabbitMQ publisher closed");
        } catch (Exception e) {
            LOGGER.warning("Error closing RabbitMQ connection: " + e.getMessage());
        }
    }
    
    static class EmailMessage {
        public String address;
        public String encryptedBody;
        
        public EmailMessage() {}
        
        public EmailMessage(String address, String encryptedBody) {
            this.address = address;
            this.encryptedBody = encryptedBody;
        }
    }
}
