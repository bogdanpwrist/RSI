# Email Encryption Pipeline

Complete email encryption and storage pipeline built with Java, Spring Boot, gRPC, and RabbitMQ.

## Architecture

```
Frontend (AJAX) → REST API (Spring Boot) → gRPC Service (Encryption) → RabbitMQ → Consumer Services → JSON Storage
```

**Flow:**
1. Frontend sends email via AJAX to REST API
2. REST API forwards to gRPC service for encryption
3. gRPC encrypts email body using Base64
4. REST API publishes encrypted email to RabbitMQ
5. Consumer services receive messages based on domain routing
6. Consumers store encrypted emails in JSON files

## Modules

| Module            | Role |
|-------------------|------|
| `email-proto`     | gRPC protocol definitions and generated classes from `email.proto`. |
| `grpc-service`    | gRPC server that encrypts email body using Base64 encoding. |
| `rest-api`        | Spring Boot REST API that receives emails, calls gRPC for encryption, and publishes to RabbitMQ. |
| `consumer-service`| RabbitMQ consumers that receive encrypted emails and store them in JSON files by domain. |
| `frontend`        | Simple HTML/JavaScript interface for sending emails. |

## Requirements

- Java 17+
- Maven 3.9+
- Docker & Docker Compose (for full stack deployment)
- RabbitMQ (included in docker-compose)

## Building

```bash
mvn clean package
```

## Running

### Full Stack with Docker Compose (Recommended)

```bash
docker compose up --build
```

This starts:
- **Frontend**: http://localhost:8080
- **REST API**: http://localhost:7000
- **gRPC Service**: localhost:50051
- **RabbitMQ**: localhost:5672 (Management UI: http://localhost:15672)
- **Consumers**: gmail-consumer, wp-consumer, other-consumer

### Local Development

#### 1. Start RabbitMQ
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine
```

#### 2. Start gRPC Server
```bash
mvn -pl grpc-service exec:java
```
- Runs on port `50001`
- Encrypts email bodies using Base64

#### 3. Start REST API
```bash
mvn -pl rest-api spring-boot:run
```
- REST API: http://localhost:8080
- Connects to gRPC on `localhost:50001`
- Publishes to RabbitMQ on `localhost:5672`

#### 4. Start Consumer Services
```bash
# Gmail consumer
java -jar consumer-service/target/consumer-service-1.0-SNAPSHOT.jar

# For different domains, set environment variables:
# CONSUMER_NAME=wp-consumer DOMAIN_FILTER=wp.com java -jar consumer-service/target/consumer-service-1.0-SNAPSHOT.jar
```

### VS Code Tasks

Pre-configured tasks in `.vscode/tasks.json`:
- `Run gRPC Server` - Starts email encryption service
- `Run REST API` - Starts Spring Boot REST API

Run from Command Palette (`Ctrl+Shift+P` → `Tasks: Run Task`)

## Configuration

### REST API Environment Variables
- `server.port` - REST API port (default: `8080`)
- `rabbitmq.host` - RabbitMQ host (default: `localhost`)
- `rabbitmq.port` - RabbitMQ port (default: `5672`)
- `rabbitmq.user` - RabbitMQ username (default: `guest`)
- `rabbitmq.pass` - RabbitMQ password (default: `guest`)

### Consumer Service Environment Variables
- `RABBITMQ_HOST` - RabbitMQ host
- `RABBITMQ_PORT` - RabbitMQ port
- `CONSUMER_NAME` - Consumer identifier
- `DOMAIN_FILTER` - Email domain to consume (`gmail.com`, `wp.com`, or `*` for others)
- `STORAGE_DIR` - JSON storage directory (default: `/data/storage`)

## Data Flow Example

1. **Send Email via Frontend**
   ```json
   POST /api/email
   {
     "address": "user@gmail.com",
     "body": "Hello World"
   }
   ```

2. **gRPC Encryption**
   - Receives: `body: "Hello World"`
   - Returns: `status: "SUCCESS", details: "Encrypted: SGVsbG8gV29ybGQ="`

3. **RabbitMQ Message**
   ```json
   {
     "address": "user@gmail.com",
     "encryptedBody": "SGVsbG8gV29ybGQ="
   }
   ```
   - Published to exchange `emails` with routing key `gmail.com`

4. **Consumer Storage** (`storage/gmail.com.json`)
   ```json
   [
     {
       "address": "user@gmail.com",
       "encryptedBody": "SGVsbG8gV29ybGQ=",
       "timestamp": "2025-11-03T19:40:00Z"
     }
   ]
   ```

## Testing

1. Open http://localhost:8080 (or http://localhost:7000 for local dev)
2. Fill in the email form
3. Click "Send Email"
4. Check logs to see the flow:
   - REST API receives request
   - gRPC encrypts email
   - RabbitMQ receives message
   - Consumer stores to JSON

## Storage

JSON files are stored in `storage/<domain>.json`:
- `storage/gmail.com.json` - All Gmail emails
- `storage/wp.com.json` - All WP emails
- `storage/yahoo.com.json` - Other domains

Each entry contains:
- `address` - Email address
- `encryptedBody` - Base64 encrypted content
- `timestamp` - Storage time

## Logging

- **REST API**: Request/response logging
- **gRPC Service**: Encryption operations
- **RabbitMQ**: Message publishing/consumption
- **Consumers**: Storage operations and errors
