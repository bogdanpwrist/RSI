package com.example.email.grpc;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * A lightweight in-memory broker that mimics routing by email domain.
 */
public final class SimpleRabbitMq {
    private static final Logger LOGGER = Logger.getLogger(SimpleRabbitMq.class.getName());
    private final Map<String, BlockingQueue<EmailMessage>> queues = new ConcurrentHashMap<>();
    private final Map<String, DomainConsumer> consumers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final JsonEmailRepository repository;

    public SimpleRabbitMq(JsonEmailRepository repository) {
        this.repository = repository;
    }

    public void publish(String domain, EmailMessage message) {
        BlockingQueue<EmailMessage> queue = queues.computeIfAbsent(domain, this::createQueueForDomain);
        queue.offer(message);
        LOGGER.info(() -> "Queued message for domain " + domain + "; queue size=" + queue.size());
    }

    private BlockingQueue<EmailMessage> createQueueForDomain(String domain) {
        BlockingQueue<EmailMessage> queue = new LinkedBlockingQueue<>();
        DomainConsumer consumer = new DomainConsumer(domain, queue, repository);
        consumers.put(domain, consumer);
        executor.submit(consumer);
        LOGGER.info(() -> "Started consumer for domain " + domain);
        return queue;
    }

    public void shutdown() {
        LOGGER.info("Stopping broker and consumers.");
        consumers.values().forEach(DomainConsumer::shutdown);
        executor.shutdownNow();
    }
}
