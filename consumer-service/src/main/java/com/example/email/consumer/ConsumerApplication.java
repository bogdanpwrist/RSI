package com.example.email.consumer;

import com.rabbitmq.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class ConsumerApplication {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String STORAGE_DIR = System.getenv().getOrDefault("STORAGE_DIR", "/data/storage");

    public static void main(String[] args) throws IOException, TimeoutException {
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        String user = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String pass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");
        String consumerName = System.getenv().getOrDefault("CONSUMER_NAME", "consumer");
        String domainFilter = System.getenv().getOrDefault("DOMAIN_FILTER", "*");

        System.out.println(consumerName + " starting...");
        System.out.println("Storage directory: " + STORAGE_DIR);
        System.out.println("Domain filter: " + domainFilter);
        System.out.println("Connecting to RabbitMQ at " + host + ":" + port);

        Files.createDirectories(Paths.get(STORAGE_DIR));

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(pass);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Declare a topic exchange
        channel.exchangeDeclare("emails", "topic", true);

        // Create queue with consumer name
        String queueName = channel.queueDeclare(consumerName + "-queue", true, false, false, null).getQueue();
        
        // Bind queue based on domain filter
        if ("*".equals(domainFilter)) {
            // Other consumer: bind to all domains except gmail.com and wp.com
            channel.queueBind(queueName, "emails", "#");
            System.out.println("Bound to ALL domains (will filter out gmail.com and wp.com)");
        } else {
            // Specific consumer: bind only to specific domain
            channel.queueBind(queueName, "emails", domainFilter);
            System.out.println("Bound to domain: " + domainFilter);
        }

        System.out.println(consumerName + " ready. Waiting for messages...");

        String finalDomainFilter = domainFilter;
        String finalConsumerName = consumerName;
        
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), "UTF-8");
                String routingKey = delivery.getEnvelope().getRoutingKey(); // domain
                
                // Filter for "other" consumer - skip gmail.com and wp.com
                if ("*".equals(finalDomainFilter)) {
                    if ("gmail.com".equals(routingKey) || "wp.com".equals(routingKey)) {
                        System.out.println("Skipping " + routingKey + " (handled by specific consumer)");
                        return;
                    }
                }
                
                System.out.println("[" + finalConsumerName + "] Received message for domain: " + routingKey);
                EmailMessage email = MAPPER.readValue(message, EmailMessage.class);
                saveToStorage(routingKey, email);
                
                System.out.println("[" + finalConsumerName + "] Saved to storage: " + routingKey + ".json");
                
            } catch (Exception e) {
                System.err.println("[" + finalConsumerName + "] Error processing message: " + e.getMessage());
                e.printStackTrace();
            }
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private static void saveToStorage(String domain, EmailMessage email) throws IOException {
        Path file;
        if (domain.equals("gmail.com") || domain.equals("wp.com")) {
            file = Paths.get(STORAGE_DIR, domain + ".json");
            return;
        }
        else {
            file = Paths.get(STORAGE_DIR, "other" + ".json");
        }
        List<StoredEmail> emails = readExisting(file);
        
        emails.add(new StoredEmail(
            email.address,
            email.encryptedBody,
            Instant.now()
        ));
        
        MAPPER.writeValue(file.toFile(), emails);
    }

    private static List<StoredEmail> readExisting(Path file) throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        if (Files.size(file) == 0) return new ArrayList<>();
        
        StoredEmail[] array = MAPPER.readValue(file.toFile(), StoredEmail[].class);
        return new ArrayList<>(Arrays.asList(array));
    }

    static class EmailMessage {
        public String address;
        public String encryptedBody;
    }

    static class StoredEmail {
        public String address;
        public String encryptedBody;
        public Instant timestamp;

        public StoredEmail() {}

        public StoredEmail(String address, String encryptedBody, Instant timestamp) {
            this.address = address;
            this.encryptedBody = encryptedBody;
            this.timestamp = timestamp;
        }
    }
}
