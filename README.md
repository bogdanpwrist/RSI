# Email Encryption Pipeline

Complete email encryption and storage pipeline built with Java, Spring Boot, gRPC, and RabbitMQ.

## Architecture

```
Frontend (AJAX) → REST API (Spring Boot) → gRPC Service (Encryption) → RabbitMQ → Consumer Services → PostgreSQL Storage
```

**Flow:**
1. Frontend sends email via AJAX to REST API
2. REST API forwards to gRPC service for encryption
3. gRPC encrypts email body using Base64
4. REST API publishes encrypted email to RabbitMQ
5. Consumer services receive messages based on domain routing
6. Consumers persist encrypted emails into dedicated PostgreSQL databases per domain (gmail.com, wp.com, other)

## Modules

| Module            | Role |
|-------------------|------|
| `email-proto`     | gRPC protocol definitions and generated classes from `email.proto`. |
| `grpc-service`    | gRPC server that encrypts email body using Base64 encoding. |
| `rest-api`        | Spring Boot REST API that receives emails, calls gRPC for encryption, and publishes to RabbitMQ. |
| `consumer-service`| RabbitMQ consumers that receive encrypted emails and store them in per-domain PostgreSQL databases. |
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
- **Storage**: PostgreSQL containers exposed on ports 5433 (gmail), 5434 (wp), 5435 (other)

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
- `storage.gmail.url` / `storage.gmail.user` / `storage.gmail.password` - Connection info for Gmail Postgres store
- `storage.wp.url` / `storage.wp.user` / `storage.wp.password` - Connection info for WP Postgres store
- `storage.other.url` / `storage.other.user` / `storage.other.password` - Connection info for the "other" Postgres store

### Consumer Service Environment Variables
- `RABBITMQ_HOST` - RabbitMQ host
- `RABBITMQ_PORT` - RabbitMQ port
- `CONSUMER_NAME` - Consumer identifier
- `DOMAIN_FILTER` - Email domain to consume (`gmail.com`, `wp.com`, or `*` for others)
- `STORAGE_BUCKET` - Logical bucket for stored emails (`gmail.com`, `wp.com`, or `other`)
- `DB_URL` - JDBC URL to the target PostgreSQL database
- `DB_USER` / `DB_PASS` - Credentials for the database connection
- `DB_CONNECT_RETRIES` / `DB_CONNECT_DELAY_MS` *(optional)* - Retry configuration for database bootstrapping

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

4. **Consumer Storage** (PostgreSQL `emails` table)
```sql
SELECT address, encrypted_body, domain, created_at
FROM emails
WHERE domain = 'gmail.com'
ORDER BY created_at DESC;
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

## Service Management (HATEOAS)

The REST API implements HATEOAS (Hypermedia as the Engine of Application State) for managing email services (Gmail, WP, Other). Each service can be in one of three states:

- **ACTIVE** - Service is operational and accepting emails
- **DISABLED** - Service is disabled, emails will be rejected
- **MAINTENANCE** - Service is under maintenance, emails will be rejected

### Service Status Endpoints

**Get all services status:**
```bash
GET /api/services/status
```
Response:
```json
{
  "gmail": "ACTIVE",
  "wp": "ACTIVE",
  "other": "DISABLED"
}
```

**Get specific service with HATEOAS links:**
```bash
GET /api/services/{serviceName}
```
Example response for ACTIVE service:
```json
{
  "name": "gmail",
  "status": "ACTIVE",
  "_links": {
    "self": {
      "href": "http://localhost:7000/api/services/gmail"
    },
    "disable": {
      "href": "http://localhost:7000/api/services/gmail/disable"
    },
    "maintenance": {
      "href": "http://localhost:7000/api/services/gmail/maintenance"
    },
    "list all": {
      "href": "http://localhost:7000/api/services/status"
    }
  }
}
```

### Service Control Endpoints

**Activate a service:**
```bash
PATCH /api/services/{serviceName}/activate
```
- Only works for DISABLED or MAINTENANCE services
- Returns 409 Conflict if already ACTIVE

**Disable a service:**
```bash
PATCH /api/services/{serviceName}/disable
```
- Only works for ACTIVE or MAINTENANCE services
- Returns 409 Conflict if already DISABLED

**Set to maintenance mode:**
```bash
PATCH /api/services/{serviceName}/maintenance
```
- Only works for ACTIVE services
- Returns 409 Conflict if not ACTIVE

### Error Responses

When trying to perform an invalid operation, the API returns HTTP 409 Conflict with RFC7807 problem detail:
```json
{
  "type": "about:blank",
  "title": "CONFLICT",
  "status": 409,
  "detail": "You CAN'T activate a service with status ACTIVE"
}
```

### Frontend Service Controls

The frontend includes three toggle switches for managing services:
- Toggle each service on/off
- Visual status indicators (Włączony/Wyłączony)
- Real-time synchronization with backend
- Error messages when service is unavailable

## Storage

Each consumer writes to a dedicated PostgreSQL instance (gmail, wp, other). Every instance maintains an `emails` table with:
- `address` - Email address
- `encrypted_body` - Base64 encrypted content
- `domain` - Original routing domain
- `created_at` - Storage timestamp

Docker compose exposes the three databases on host ports 5433, 5434, and 5435 for direct inspection.

## Logging

- **REST API**: Request/response logging
- **gRPC Service**: Encryption operations
- **RabbitMQ**: Message publishing/consumption
- **Consumers**: Storage operations and errors
