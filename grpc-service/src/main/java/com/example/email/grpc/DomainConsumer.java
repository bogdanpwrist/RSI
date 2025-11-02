package com.example.email.grpc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumes messages for a single email domain and persists them.
 */
public final class DomainConsumer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(DomainConsumer.class.getName());
    private final String domain;
    private final BlockingQueue<EmailMessage> queue;
    private final JsonEmailRepository repository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DomainConsumer(String domain, BlockingQueue<EmailMessage> queue, JsonEmailRepository repository) {
        this.domain = domain;
        this.queue = queue;
        this.repository = repository;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                EmailMessage message = queue.take();
                repository.append(domain, message);
                LOGGER.info(() -> "Stored message for domain " + domain + ": " + message.encryptedBody());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Consumer interrupted for domain " + domain, ex);
                break;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to process message for domain " + domain, ex);
            }
        }
        LOGGER.info(() -> "Consumer stopped for domain " + domain);
    }

    public void shutdown() {
        running.set(false);
    }
}
