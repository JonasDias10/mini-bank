# SimpleBank

A small banking system built with two Spring Boot microservices. It is meant as a learning project: the code is kept simple and follows a plain Controller, Service, Repository structure.

| Service               | Port | Responsibility                                            | Database        |
|-----------------------|------|-----------------------------------------------------------|-----------------|
| `account-service`     | 8081 | Register customers, open accounts, read details/balances  | `accountdb`     |
| `transaction-service` | 8082 | Deposits, withdrawals and transfers                       | `transactiondb` |
| `frontend`            | 4200 | Angular test console that calls both services             | none            |

Both databases live in a single PostgreSQL container, but each service only talks to its own database. The transaction-service never touches account data directly: it calls account-service over REST.

```
            ┌──────────────────────┐   REST (credit / debit)   ┌──────────────────┐
 client ──► │ transaction-service  │ ────────────────────────► │ account-service  │ ◄── client
            │       :8082          │                           │      :8081       │
            └──────────┬───────────┘                           └────────┬─────────┘
                       │                                                │
                 transactiondb  ◄──────── PostgreSQL :5432 ────────►  accountdb
```

## Tech stack

Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, Flyway, PostgreSQL 16, Maven, Docker Compose. Test frontend: Angular 20 served by nginx.

## Project structure

```
mini-bank/
├── docker-compose.yml
├── docker/postgres/init.sql          # creates the second database (transactiondb)
├── requests.http                     # ready-to-run example requests
├── run-tests.sh                      # runs both test suites in Docker
├── account-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/simplebank/account/
│       ├── controller/               # REST endpoints
│       ├── service/                  # business rules
│       ├── repository/               # Spring Data JPA repositories
│       ├── model/                    # JPA entities (Customer, Account)
│       ├── dto/                      # request/response objects + validation
│       └── exception/                # custom exceptions + GlobalExceptionHandler
├── transaction-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/simplebank/transaction/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/                    # Transaction entity, TransactionType enum
│       ├── dto/
│       ├── client/                   # AccountClient: REST calls to account-service
│       └── exception/
└── frontend/                         # Angular test console
    ├── Dockerfile                    # builds the app, serves it with nginx
    ├── nginx.conf                    # proxies /api/* to the two services
    ├── proxy.conf.json               # same proxy for `npm start` (ng serve)
    └── src/app/
        ├── services/                 # HTTP clients + ActivityLogService
        ├── panels/                   # Customers, Accounts, Transactions forms
        └── activity-log/             # shows every API response or error
```

## Running with Docker Compose

Requirements: Docker Desktop (or Docker Engine + Compose v2). You do not need Java or Maven installed: the images build the code themselves.

```bash
docker compose up --build
```

The first build downloads Maven and npm dependencies and takes a few minutes. When you see `Started AccountServiceApplication` and `Started TransactionServiceApplication` in the logs, both services are ready.

Open **http://localhost:4200** to use the test console: register customers, open accounts, deposit, withdraw, transfer and read history. Every response (or error, with its status and field errors) appears in the "Responses" panel on the right.

If port 5432 is already used by a local PostgreSQL, publish the database on another port (the services connect over the Docker network, so they are not affected):

```bash
POSTGRES_PORT=5433 docker compose up --build
```

Stop everything:

```bash
docker compose down        # keep the data
docker compose down -v     # also delete the database volume
```

## Running a service from your IDE (optional)

Requires JDK 21 and Maven 3.9+.

```bash
docker compose up -d postgres        # only the database
cd account-service && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run   # in another terminal
```

To run the frontend with hot reload (requires Node 20.19+ or 22.12+), with both services running:

```bash
cd frontend && npm install && npm start     # http://localhost:4200
```

Default settings in each `application.yml` point to `localhost`, so no extra configuration is needed. They can be overridden with the environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and (transaction-service only) `ACCOUNT_SERVICE_URL`.

## API

### account-service (http://localhost:8081)

| Method | Path                            | Description                                |
|--------|---------------------------------|--------------------------------------------|
| POST   | `/api/customers`                | Register a customer                        |
| GET    | `/api/customers/{id}`           | Get a customer                             |
| GET    | `/api/customers/{id}/accounts`  | List a customer's accounts                 |
| POST   | `/api/accounts`                 | Open an account for a customer             |
| GET    | `/api/accounts/{id}`            | Get account details                        |
| GET    | `/api/accounts/{id}/balance`    | Get account balance                        |
| POST   | `/api/accounts/{id}/credit`     | Add money (used by transaction-service)    |
| POST   | `/api/accounts/{id}/debit`      | Remove money (used by transaction-service) |

### transaction-service (http://localhost:8082)

| Method | Path                                         | Description                           |
|--------|----------------------------------------------|---------------------------------------|
| POST   | `/api/transactions/deposit`                  | Deposit into an account               |
| POST   | `/api/transactions/withdraw`                 | Withdraw from an account              |
| POST   | `/api/transactions/transfer`                 | Transfer between two accounts         |
| GET    | `/api/transactions/{id}`                     | Get a transaction                     |
| GET    | `/api/transactions?accountId=1&page=0&size=20` | Transaction history (newest first)  |

## Example walkthrough (curl)

All requests are also in [requests.http](requests.http).

```bash
# 1. Register two customers
curl -s -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
  -d '{"fullName":"Ana Souza","email":"ana@example.com"}'
curl -s -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
  -d '{"fullName":"Bruno Lima","email":"bruno@example.com"}'

# 2. Open one account for each
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' -d '{"customerId":1}'
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' -d '{"customerId":2}'

# 3. Deposit, withdraw, transfer
curl -s -X POST localhost:8082/api/transactions/deposit -H 'Content-Type: application/json' \
  -d '{"accountId":1,"amount":500.00}'
curl -s -X POST localhost:8082/api/transactions/withdraw -H 'Content-Type: application/json' \
  -d '{"accountId":1,"amount":50.00}'
curl -s -X POST localhost:8082/api/transactions/transfer -H 'Content-Type: application/json' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":100.00}'

# 4. Check balances and history
curl -s localhost:8081/api/accounts/1/balance    # 350.00
curl -s localhost:8081/api/accounts/2/balance    # 100.00
curl -s "localhost:8082/api/transactions?accountId=1"
```

Example responses:

```json
// POST /api/accounts
{
  "id": 1,
  "accountNumber": "1000000000",
  "balance": 0.00,
  "customerId": 1,
  "customerName": "Ana Souza",
  "createdAt": "2026-10-20T13:00:00.000Z"
}

// POST /api/transactions/transfer
{
  "id": 3,
  "type": "TRANSFER",
  "sourceAccountId": 1,
  "targetAccountId": 2,
  "amount": 100.00,
  "createdAt": "2026-10-20T13:05:00.000Z"
}
```

## Error handling

Each service has a `GlobalExceptionHandler` (`@RestControllerAdvice`) that turns exceptions into a consistent JSON body:

```json
{
  "timestamp": "2026-10-20T13:10:00.000Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Insufficient funds in account 1000000000",
  "path": "/api/transactions/withdraw"
}
```

Validation errors also include a `fieldErrors` map, for example `{"amount": "Amount must be at least 0.01"}`.

| Status | When                                                              |
|--------|-------------------------------------------------------------------|
| 400    | Invalid body, missing field, negative amount, bad path parameter  |
| 404    | Customer, account or transaction not found                        |
| 409    | Email already registered                                          |
| 422    | Insufficient funds, transfer to the same account                  |
| 503    | transaction-service cannot reach account-service                  |
| 500    | Unexpected error (details are logged, not returned)               |

When account-service returns an error, `AccountClient` reads its message and rethrows it as the matching exception, so the client of transaction-service sees the same message.

## Testing

Each service has four kinds of tests, written with JUnit 5, Mockito, AssertJ and Spring Boot Test:

| Kind              | Tools                                   | What it covers                                                                 |
|-------------------|-----------------------------------------|--------------------------------------------------------------------------------|
| Unit              | JUnit + Mockito                         | Services and entities in isolation (repositories and `AccountClient` mocked)   |
| Web layer         | `@WebMvcTest` + MockMvc + `@MockitoBean` | Endpoints, status codes, validation messages, `GlobalExceptionHandler`         |
| HTTP client       | `MockRestServiceServer`                 | `AccountClient` request format and how each error status is translated         |
| Integration       | `@SpringBootTest` + Testcontainers      | Real PostgreSQL: Flyway migrations, DB constraints, paging, row locking        |

The integration tests start a throwaway PostgreSQL container, so **Docker must be running**. Highlights worth reading:

- `AccountServiceIntegrationTest.concurrentDebitsNeverOverdrawTheAccount`: 10 parallel debits of 20.00 against a balance of 100.00; exactly 5 succeed thanks to the pessimistic lock.
- `TransactionServiceTest.transferRefundsSourceWhenCreditFails`: the compensation logic of a transfer.

Run all tests without installing Java or Maven (uses a Maven container):

```bash
./run-tests.sh                      # both services
./run-tests.sh transaction-service  # just one
```

Or, with JDK 21 and Maven installed: `cd account-service && mvn test`.

## Database migrations

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway), not by Hibernate. Each service applies its own SQL scripts on startup:

```
account-service/src/main/resources/db/migration/V1__create_customers_and_accounts.sql
transaction-service/src/main/resources/db/migration/V1__create_transactions.sql
```

Hibernate runs with `ddl-auto: validate`: it never changes the database, it only checks on startup that the entities match the tables, and the service fails to start if they don't.

To change the schema, add a new file with the next version number (for example `V2__add_phone_to_customers.sql`) and update the entity. Never edit a migration that has already run; Flyway stores a checksum of each applied script in `flyway_schema_history` and will refuse to start if it changes.

To start from an empty database: `docker compose down -v`.

## How a transfer works

1. Reject the request if source and destination are the same account.
2. Check that the destination account exists (fail before moving any money).
3. Debit the source account. account-service locks the row and checks the balance.
4. Credit the destination account.
5. If step 4 fails, credit the source account back (a simple compensation).
6. Save the transaction record.

## Design notes and limitations

These are deliberate simplifications to keep the project easy to follow:

- **Distributed consistency**: the transfer uses a basic compensating refund. If the transaction-service crashes between steps, balances and history can drift. Real systems use patterns like Saga or the Outbox pattern.
- **CORS**: the frontend calls `/api/...` on its own origin and nginx (or the Angular dev proxy) forwards the request, so the backends need no CORS configuration.
- **Security**: there is no authentication. The `credit`/`debit` endpoints are public here; in a real deployment they would only be reachable by internal services.
- **Concurrency**: account balance updates use a pessimistic row lock, so two simultaneous withdrawals cannot overdraw an account.
- **Money**: amounts use `BigDecimal` with 2 decimal places, never `double`.
