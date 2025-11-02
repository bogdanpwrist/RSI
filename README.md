# Simple Email Pipeline

Prosty przykład pokazujący przepływ danych przez warstwy: frontend (AJAX), REST API, gRPC, przetwarzanie i zapisywanie e-maili w plikach JSON. Całość została zbudowana w Javie przy użyciu Mavena oraz lekkich bibliotek.

## Moduły

| Moduł         | Rola |
|---------------|------|
| `email-proto` | Deklaracje protokołu gRPC oraz klasy wygenerowane z `email.proto`. |
| `grpc-service` | Serwer gRPC, który szyfruje treść (+1 ASCII), loguje działania, publikuje wiadomości do imitacji RabbitMQ i uruchamia konsumery zapisujące dane do JSON. |
| `rest-api`     | Serwer HTTP oparty o Javalin. Obsługuje frontend, końcówkę REST `/api/email` oraz komunikuje się z gRPC. |

## Wymagania

- Java 17+
- Maven 3.9+

## Budowanie

```bash
mvn clean package
```

## Uruchamianie

### Docker Compose (zalecane)

```bash
docker compose up --build
```

Następnie otwórz `http://localhost:7000/` w przeglądarce.

### Lokalnie z Maven

1. **Serwer gRPC**
   ```bash
   mvn -pl grpc-service exec:java
   ```
   - konfiguracja portu: `-Dgrpc.port=50051`
   - katalog zapisu JSON: `-Dstorage.dir=storage`

2. **REST API + Frontend** (w nowym terminalu)
   ```bash
   mvn -pl rest-api exec:java
   ```
   - port REST: `-Drest.port=7000`
   - docelowy serwer gRPC: `-Dgrpc.target=localhost:50051`

3. Otwórz przeglądarkę na `http://localhost:7000/` i wyślij formularz.

### Uruchomienie przez Docker Compose

```bash
docker compose up --build
```

- REST API: `http://localhost:7000`
- gRPC serwis dostępny lokalnie na porcie `50051`
- Dane JSON trafiają do woluminu `storage` (możesz je podejrzeć w `docker volume inspect` lub zamapować katalog lokalny).

Zmienne środowiskowe:
- `REST_PORT` (domyślnie `7000`)
- `GRPC_TARGET` (domyślnie `grpc-service:50051`)
- `GRPC_PORT` (domyślnie `50051`)
- `STORAGE_DIR` (domyślnie `/data/storage` wewnątrz kontenera gRPC)

### VS Code Tasks

Zdefiniowano zadania w `.vscode/tasks.json`:
- `Run gRPC Server`
- `Run REST API`

Możesz je uruchomić z palety poleceń (`Ctrl+Shift+P` → `Run Task`).

## Repozytorium JSON

Każda domena e-mail otrzymuje własny plik `storage/<domena>.json` z historią wiadomości. Pliki są aktualizowane przez konsumentów działających w tle.

## Logowanie

- REST API loguje przyjęcie żądania oraz odpowiedzi gRPC.
- gRPC loguje szyfrowanie, kolejkowanie oraz działania konsumentów.
- Błędy zapisu do plików JSON są raportowane w logach.

## Frontend

`rest-api/src/main/resources/public/index.html` zawiera prostą stronę z formularzem, wysyła żądania AJAX i prezentuje log z przebiegu (REST → gRPC → RabbitMQ → JSON).
