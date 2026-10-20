# SimpleBank

A small banking system built with two Spring Boot microservices that talk through RabbitMQ. It is a learning project: the code follows a plain Controller, Service, Repository structure, and each distributed-systems pattern it uses (Saga, Outbox, idempotent consumer, dead-letter queue, contract tests) is kept as small as possible.

| Service               | Port | Responsibility                                                    | Database        |
|-----------------------|------|-------------------------------------------------------------------|-----------------|
| `account-service`     | 8081 | Customers, accounts, balances; applies credit/debit commands      | `accountdb`     |
| `transaction-service` | 8082 | Deposits, withdrawals and transfers (orchestrates the saga)       | `transactiondb` |
| `frontend`            | 4200 | Angular test console that calls both services                     | none            |
| `rabbitmq`            | 5672 | Message broker (management UI on http://localhost:15672)          | none            |

Each service owns its database and its code: nothing is shared, and the services never call each other over HTTP. Money only moves through messages.

```
                         ┌─────────────────────────── RabbitMQ ───────────────────────────┐
                         │                                                                │
 client ──► transaction-service ── account-operation-commands ──► account-service ◄── client
   (202 PENDING)    │    ▲                                              │
                    │    └──────── account-operation-results ◄──────────┘
                    │                                                   │
              transactiondb                                         accountdb
       (transactions + outbox)                (accounts + processed_operations + outbox)
```

## Tech stack

Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Spring AMQP, Bean Validation, Flyway, PostgreSQL 16, RabbitMQ 4.1, Maven, Docker Compose. Tests: JUnit 5, Mockito, AssertJ, Testcontainers, Awaitility, Pact. Test frontend: Angular 20 served by nginx.

## How a transaction flows

1. The client calls `POST /api/transactions/deposit` (or `withdraw`, `transfer`).
2. transaction-service saves the transaction as **`PENDING`** and, **in the same database transaction**, writes the first command to its outbox table. It answers **`202 Accepted`** right away.
3. The outbox relay publishes the command to RabbitMQ.
4. account-service applies the credit or debit, marks the command as processed and writes the result to its own outbox, all in one database transaction. Its relay publishes the result.
5. transaction-service receives the result and moves the saga forward until the transaction is **`COMPLETED`** or **`FAILED`** (with a `failureReason`).
6. The client polls `GET /api/transactions/{id}` to see the final status. The frontend does this automatically.

### Saga steps

| Type         | Steps                                   | Outcome                                                                  |
|--------------|-----------------------------------------|--------------------------------------------------------------------------|
| `DEPOSIT`    | credit target                           | `COMPLETED`, or `FAILED` (e.g. account not found)                        |
| `WITHDRAWAL` | debit source                            | `COMPLETED`, or `FAILED` (e.g. insufficient funds)                       |
| `TRANSFER`   | debit source, then credit target        | `COMPLETED`, or `FAILED` if the debit fails (nothing moved)              |
|              | credit fails, then refund source        | `FAILED` with reason "... Amount refunded to source account"             |

The state machine lives in [TransactionSaga.java](transaction-service/src/main/java/com/simplebank/transaction/service/TransactionSaga.java).

### Reliability patterns

- **Transactional outbox** (both services). A message is never published directly from business code. It is saved to `outbox_messages` in the same transaction as the change it announces, then [OutboxRelay](account-service/src/main/java/com/simplebank/account/outbox/OutboxRelay.java) publishes it every 500 ms and marks it sent only after RabbitMQ confirms it (publisher confirms). If the broker is down, messages wait in the table.
- **At-least-once delivery + idempotent consumers.** Because of the outbox, a message may be published twice (for example, if the app dies after publishing but before marking the row as sent). Both sides handle duplicates:
  - account-service stores every applied `operationId` in `processed_operations`. The primary key guarantees a command is applied once, even under concurrency.
  - transaction-service only accepts a result whose `operationId` matches the step the transaction is currently waiting for, and locks the row while doing it.
- **Business failures are results, not errors.** Insufficient funds or an unknown account come back as `success: false` with a reason.
- **Retries and dead-letter queues.** Technical failures (database down, malformed message) are retried 3 times; after that the message goes to `<queue>.dlq` instead of looping forever. Inspect them in the RabbitMQ UI.
- **No temporal coupling.** transaction-service keeps accepting operations while account-service is down. They stay `PENDING` and complete as soon as it comes back.

Try the last one yourself:

```bash
docker compose stop account-service
curl -s -X POST localhost:8082/api/transactions/deposit -H 'Content-Type: application/json' \
  -d '{"accountId":1,"amount":10}'                     # 202, status PENDING
docker compose start account-service                 # a few seconds later: COMPLETED
```

## Project structure

```
mini-bank/
├── docker-compose.yml
├── docker/postgres/init.sql          # creates the second database (transactiondb)
├── pacts/                            # message contracts between the services (generated by tests)
├── requests.http                     # ready-to-run example requests
├── run-tests.sh                      # runs both test suites in Docker
├── account-service/
│   └── src/main/java/com/simplebank/account/
│       ├── controller/               # REST endpoints
│       ├── service/                  # business rules + AccountOperationProcessor (idempotent)
│       ├── repository/               # Spring Data JPA repositories
│       ├── model/                    # JPA entities (Customer, Account, ProcessedOperation)
│       ├── dto/                      # request/response objects + validation
│       ├── messaging/                # RabbitMQ topology, message records, listener
│       ├── outbox/                   # outbox table, writer and relay
│       └── exception/                # custom exceptions + GlobalExceptionHandler
├── transaction-service/
│   └── src/main/java/com/simplebank/transaction/
│       ├── controller/
│       ├── service/                  # TransactionService + TransactionSaga
│       ├── repository/
│       ├── model/                    # Transaction, TransactionStatus, SagaStep
│       ├── dto/
│       ├── messaging/
│       ├── outbox/
│       └── exception/
└── frontend/                         # Angular test console
    ├── nginx.conf                    # proxies /api/* to the two services
    ├── proxy.conf.json               # same proxy for `npm start` (ng serve)
    └── src/app/
        ├── services/                 # HTTP clients (with status polling) + ActivityLogService
        ├── panels/                   # Customers, Accounts, Transactions forms
        └── activity-log/             # every API response, colored by status
```

The outbox and messaging classes are intentionally duplicated in both services instead of living in a shared library: a shared library would force both services to be released together.

## Running with Docker Compose

Requirements: Docker Desktop (or Docker Engine + Compose v2). You do not need Java, Maven or Node installed.

```bash
docker compose up --build
```

The first build downloads Maven and npm dependencies and takes a few minutes. Then open:

- **http://localhost:4200**: the test console. Transactions first appear in amber as `PENDING`, then a second entry shows the final status (green `COMPLETED` or red `FAILED`).
- **http://localhost:15672** (user `simplebank`, password `simplebank`): RabbitMQ UI, to watch queues and dead letters.

If a port is already taken on your machine, publish it on another one (the services talk over the Docker network, so they are not affected):

```bash
POSTGRES_PORT=5433 RABBITMQ_PORT=5673 RABBITMQ_UI_PORT=15673 docker compose up --build
```

Stop everything:

```bash
docker compose down        # keep the data
docker compose down -v     # also delete the database volume
```

## Running a service from your IDE (optional)

Requires JDK 21 and Maven 3.9+.

```bash
docker compose up -d postgres rabbitmq
cd account-service && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run   # in another terminal
cd frontend && npm install && npm start         # optional, http://localhost:4200
```

Defaults in each `application.yml` point to `localhost`. Override them with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD`.

## API

### account-service (http://localhost:8081)

| Method | Path                            | Description                    |
|--------|---------------------------------|--------------------------------|
| POST   | `/api/customers`                | Register a customer            |
| GET    | `/api/customers/{id}`           | Get a customer                 |
| GET    | `/api/customers/{id}/accounts`  | List a customer's accounts     |
| POST   | `/api/accounts`                 | Open an account for a customer |
| GET    | `/api/accounts/{id}`            | Get account details            |
| GET    | `/api/accounts/{id}/balance`    | Get account balance            |

Balances only change through messages from transaction-service; there is no HTTP endpoint to move money.

### transaction-service (http://localhost:8082)

| Method | Path                                           | Description                                  |
|--------|------------------------------------------------|----------------------------------------------|
| POST   | `/api/transactions/deposit`                    | Start a deposit (`202`, status `PENDING`)    |
| POST   | `/api/transactions/withdraw`                   | Start a withdrawal (`202`)                   |
| POST   | `/api/transactions/transfer`                   | Start a transfer (`202`)                     |
| GET    | `/api/transactions/{id}`                       | Current status of a transaction              |
| GET    | `/api/transactions?accountId=1&page=0&size=20` | Transaction history (newest first)           |

### Messages (RabbitMQ, exchange `simplebank`)

| Queue                        | Producer            | Consumer            | Body                                                       |
|------------------------------|---------------------|---------------------|------------------------------------------------------------|
| `account-operation-commands` | transaction-service | account-service     | `{"operationId", "accountId", "type": "CREDIT"\|"DEBIT", "amount"}` |
| `account-operation-results`  | account-service     | transaction-service | `{"operationId", "success", "failureReason"}`               |

## Example walkthrough (curl)

All requests are also in [requests.http](requests.http).

```bash
# 1. Register two customers and open one account for each
curl -s -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
  -d '{"fullName":"Ana Souza","email":"ana@example.com"}'
curl -s -X POST localhost:8081/api/customers -H 'Content-Type: application/json' \
  -d '{"fullName":"Bruno Lima","email":"bruno@example.com"}'
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' -d '{"customerId":1}'
curl -s -X POST localhost:8081/api/accounts -H 'Content-Type: application/json' -d '{"customerId":2}'

# 2. Deposit, withdraw, transfer (each answers 202 with status PENDING)
curl -s -X POST localhost:8082/api/transactions/deposit -H 'Content-Type: application/json' \
  -d '{"accountId":1,"amount":500.00}'
curl -s -X POST localhost:8082/api/transactions/withdraw -H 'Content-Type: application/json' \
  -d '{"accountId":1,"amount":50.00}'
curl -s -X POST localhost:8082/api/transactions/transfer -H 'Content-Type: application/json' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":100.00}'

# 3. A moment later: final status, balances and history
curl -s localhost:8082/api/transactions/3                  # "status": "COMPLETED"
curl -s localhost:8081/api/accounts/1/balance               # 350.00
curl -s localhost:8081/api/accounts/2/balance               # 100.00
curl -s "localhost:8082/api/transactions?accountId=1"
```

Example responses:

```json
// POST /api/transactions/transfer  (202 Accepted)
{
  "id": 3,
  "type": "TRANSFER",
  "status": "PENDING",
  "sourceAccountId": 1,
  "targetAccountId": 2,
  "amount": 100.00,
  "createdAt": "2026-10-20T13:05:00.000Z"
}

// GET /api/transactions/4  (a transfer to an account that does not exist)
{
  "id": 4,
  "type": "TRANSFER",
  "status": "FAILED",
  "sourceAccountId": 1,
  "targetAccountId": 999,
  "amount": 20.00,
  "failureReason": "Account not found with id 999. Amount refunded to source account",
  "createdAt": "2026-10-20T13:06:00.000Z"
}
```

## Error handling

HTTP errors use a consistent JSON body produced by each service's `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-10-20T13:10:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found with id 999",
  "path": "/api/accounts/999"
}
```

Validation errors also include a `fieldErrors` map, for example `{"amount": "Amount must be at least 0.01"}`.

| Status | When                                                              |
|--------|-------------------------------------------------------------------|
| 400    | Invalid body, missing field, negative amount, bad path parameter  |
| 404    | Customer, account or transaction not found                        |
| 409    | Email already registered                                          |
| 422    | Transfer to the same account                                      |
| 500    | Unexpected error (details are logged, not returned)               |

Problems found while the saga runs (insufficient funds, unknown account) are not HTTP errors: the request was accepted, and the transaction ends as `FAILED` with a `failureReason`.

## Testing

Run everything without installing Java or Maven (uses a Maven container; Docker must be running):

```bash
./run-tests.sh
```

| Kind        | Tools                                     | What it covers                                                                  |
|-------------|-------------------------------------------|---------------------------------------------------------------------------------|
| Unit        | JUnit 5 + Mockito + AssertJ               | Saga transitions, idempotent processor, outbox relay, services, entities        |
| Web layer   | `@WebMvcTest` + MockMvc + `@MockitoBean`  | Endpoints, status codes, validation messages, `GlobalExceptionHandler`          |
| Integration | `@SpringBootTest` + Testcontainers        | Real PostgreSQL and RabbitMQ: migrations, constraints, row locks, message flow  |
| Contract    | Pact (message pacts)                      | The JSON each service sends matches what the other one expects                  |

Highlights worth reading:

- [TransactionSagaTest](transaction-service/src/test/java/com/simplebank/transaction/service/TransactionSagaTest.java): every saga transition, including the refund and a refund that fails.
- [TransactionSagaIntegrationTest](transaction-service/src/test/java/com/simplebank/transaction/messaging/TransactionSagaIntegrationTest.java): the test plays account-service over real RabbitMQ.
- [AccountOperationMessagingIntegrationTest](account-service/src/test/java/com/simplebank/account/messaging/AccountOperationMessagingIntegrationTest.java): a duplicated command is applied once.
- `AccountServiceIntegrationTest.concurrentDebitsNeverOverdrawTheAccount`: 10 parallel debits of 20.00 against 100.00; exactly 5 succeed thanks to the row lock.

### Contract tests

Each service is the **consumer** of one message and the **provider** of the other:

- account-service consumes commands: `AccountOperationCommandConsumerPactTest` writes `pacts/account-service-transaction-service.json`, and transaction-service's `AccountOperationCommandProviderPactTest` checks that the commands the saga really builds match it.
- transaction-service consumes results: the same in the other direction.

`run-tests.sh` runs all consumer tests first (regenerating `/pacts`), then every suite. The pact files are committed, so a change in a message format shows up in the diff. If you rename a field on one side only, the provider test fails with something like `Actual map is missing the following keys: accountId`.

## Database migrations

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Hibernate runs with `ddl-auto: validate`: it never changes the database, it only checks on startup that the entities match the tables.

```
account-service/src/main/resources/db/migration/
  V1__create_customers_and_accounts.sql
  V2__create_account_number_sequence.sql
  V3__create_processed_operations_and_outbox.sql
transaction-service/src/main/resources/db/migration/
  V1__create_transactions.sql
  V2__add_saga_state_and_outbox.sql
```

To change the schema, add a new file with the next version number and update the entity. Never edit a migration that has already run; Flyway stores a checksum of each applied script and refuses to start if it changes. To start from an empty database: `docker compose down -v`.

## Design notes and limitations

- **Eventual consistency**: right after a `202`, the balance has not changed yet. Clients must read the transaction status.
- **Account validation happens inside the saga**: a deposit to an account that does not exist is accepted (`202`) and ends as `FAILED`. This keeps transaction-service independent from account-service being online.
- **Refund failure** needs a human: if the refund of a transfer also fails, the transaction is marked `FAILED` with "Manual review required" and an error is logged. With no way to delete accounts this cannot happen today, but the path is handled.
- **Outbox rows are never deleted**: fine for a study project; a real system would clean up sent rows periodically.
- **Topology is declared by both services** (exchange, queues, dead-letter queues), so neither depends on the other starting first. Names and arguments must stay identical in both `MessagingConfig` classes.
- **Security**: there is no authentication, and RabbitMQ uses a single user.
- **Money**: amounts use `BigDecimal` with 2 decimal places, never `double`.
