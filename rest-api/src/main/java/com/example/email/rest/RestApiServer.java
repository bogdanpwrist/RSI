package com.example.email.rest;

import com.example.email.proto.SendEmailReply;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RestApiServer {
    private static final Logger LOGGER = Logger.getLogger(RestApiServer.class.getName());

    private RestApiServer() {
    }

    public static void main(String[] args) {
    int port = Integer.parseInt(propertyOrEnv("rest.port", "REST_PORT", "7000"));
    String grpcTarget = propertyOrEnv("grpc.target", "GRPC_TARGET", "localhost:50051");
        Gson gson = new Gson();
        GrpcEmailClient grpcClient = new GrpcEmailClient(grpcTarget);

        Javalin app = Javalin.create(config -> {
                config.plugins.enableCors(cors -> {
                    cors.add(it -> it.anyHost());
                });
        })
                .post("/api/email", ctx -> {
                    try {
                        EmailPayload payload = gson.fromJson(ctx.body(), EmailPayload.class);
                        if (payload == null || !payload.isValid()) {
                            LOGGER.warning("Received invalid payload");
                            ctx.status(400).json(Map.of("error", "Invalid email payload"));
                            return;
                        }
                        LOGGER.info(() -> "REST received email for " + payload.address());
                        SendEmailReply reply = grpcClient.send(payload);
                        ctx.json(Map.of(
                                "status", reply.getStatus(),
                                "details", reply.getDetails()));
                    } catch (JsonSyntaxException ex) {
                        LOGGER.log(Level.WARNING, "Failed to parse payload", ex);
                        ctx.status(400).json(Map.of("error", "Malformed JSON"));
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Failed to process request", ex);
                        ctx.status(500).json(Map.of("error", "Internal server error"));
                    }
                })
                .get("/health", ctx -> ctx.result("OK"));

        app.events(event -> {
            event.serverStopping(grpcClient::close);
            event.serverStopped(() -> LOGGER.info("REST server stopped."));
        });

        LOGGER.info(() -> "REST server starting on port " + port + " targeting gRPC " + grpcTarget);
        app.start(port);
    }

    private static String propertyOrEnv(String propertyKey, String envKey, String defaultValue) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
