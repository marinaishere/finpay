# Payment & Transaction System (Banking Simulation)

-   Features:

    -   Multi-user accounts with balance tracking
    -   Transaction processing with ACID guarantees
    -   Support for deposits, withdrawals, transfers
    -   Fraud detection rules (e.g., flag large or frequent transfers)
    -   Audit logs & reporting

-   Tech Depth:
    -   Spring Data JPA with PostgreSQL/MySQL
    -   @Transactional handling
    -   Distributed locks (Redisson) for concurrency safety
    -   Event-driven notifications with Kafka
    -   Security (Spring Security, JWT, role-based access)

## Downloads

-   [FinPay Architecture (draw.io)](./docs/finpay-architecture.drawio)
-   [Docker Compose (dev stack)](./docs/docker-compose.yml)

## Modules (microservices)

-   **API Gateway:** Spring Cloud Gateway, rate limiting, request logging, JWT validation.
-   Auth Service: Spring Security, JWT, RBAC (admin, auditor, user).
-   Account Service: Account CRUD, KYC flags, balance snapshot reads, idempotent create.
-   **Transaction Service:** Transfers with ACID, idempotency keys, optimistic locking, saga outbox → Kafka.
-   **Fraud/Risk:** Rule engine (amount, velocity, geo), risk score, “review/deny” events.
-   **Notification:** Email/SMS on tx.completed, fraud.flagged.
-   **Observability:** Micrometer → Prometheus, Grafana dashboards; central logs (OpenSearch/ELK).

## Data model (PostgresSQL)

-   users(id, username, role, status, created_at)
-   accounts(id, user_id, currency, balance_minor, status, version) ← @Version for optimistic locking
-   transactions(id, from_account, to_account, amount_minor, currency, status, idempotency_key, created_at)
-   ledger_entries(id, tx_id, account_id, delta_minor, type[DEBIT|CREDIT])
-   fraud_events(id, tx_id, rule, score, action)
-   outbox(id, aggregate_type, aggregate_id, event_type, payload_json, published_at null) ← processed by a scheduler to Kafka

## Key design choices

-   **Idempotency** via `Idempotency-Key` header stored against `transactions`.
-   **Double-entry ledger** (balanced DEBIT/CREDIT rows) for auditability.
-   **Transactional outbox** pattern to avoid dual-write issues when publishing Kafka events.
-   **Pessimistic vs optimistic**: prefer optimistic on `accounts.version`; fall back to retry-with-backoff.
-   **Read scalability**: balances read through Redis cache (write-through on commit).
-   **Sagas**: `tx.initiated` → fraud check → reserve funds → finalize → notify (compensate on failure).

## API highlights

-   `POST /auth-services/login` → JWT
-   `POST /accounts` | `GET /accounts/{id}`
-   `POST /transactions/transfer` (header `Idempotency-Key`) → `202 Accepted`
-   `GET /transactions/{id}` → status: `PENDING|COMPLETED|REVERSED|FLAGGED`

    _(Full paths in the OpenAPI starter.)_

## Tech stack

Spring Boot 3.x, Spring Data JPA, Spring Security, Spring Cloud Gateway, Kafka, PostgresSQL, Redis, Testcontainers, Docker Compose, Prometheus/Grafana, OpenSearch/ELK, React (admin UI).

**How to run**:
`docker-compose up -d`
then `mvn spring-boot:run` per service

## FinPay — Local Dev Setup

We’ll build 6 microservices + supporting infra:

-   gateway → Spring Cloud Gateway
-   auth-service → Spring Security + JWT
-   account-service → Account CRUD + balance
-   transaction-service → Transfers (idempotent, ACID)
-   fraud-service → Rule-based fraud checks
-   notification-service → Email/SMS events

Infra (via docker-compose.yml):

-   PostgreSQL (main DB)
-   Redis (cache)
-   Kafka + Zookeeper (event bus)
-   Prometheus + Grafana (monitoring)
-   ELK + Kibana (logging)

## 📂 Project Structure

```bash
finpay/
  pom.xml
  docker-compose.yml
  README.md
  gateway/
  auth-service/
  account-service/
  transaction-service/
  fraud-service/
  notification-service/
  common/

```

1. [Root pom.xml](./pom.xml)
2. [docker-compose.yml](./docker-compose.yml)
3. [account-service/pom.xml](./account-service/pom.xml)
4. [account-service/src/main/resources/application.yml](./account-service/src/main/resources/application.yml)
5. [AccountServiceApplication.java](./account-service/src/main/java/AccountServiceApplication.java)
6. [AccountController.java](./account-service/src/main/java/AccountServiceApplication.java)
7. Repeat for other services
   Each service follows the same structure:

-   `pom.xml` (with needed dependencies: Security for Auth, WebFlux for Gateway, etc.)
-   `application.yml` (unique port + service name)
-   `ServiceApplication.java`
-   `Controller.java` (minimal endpoints for now)
-   `Dockerfile` (simple Spring Boot jar run)
## In depth each microservice
1. [Auth Service](./auth-service/README.md)
2. [Api Gateway](./api-gateway/README.md)
3. [Account Service](./account-service/README.md)
## TODO

-   Write clean README.md (setup instructions, tech stack, screenshots).
-   Add Swagger/OpenAPI docs for APIs.
-   Use Docker Compose to spin up the whole system.
-   Deploy a live demo on AWS/GCP/Heroku.
-   Include unit + integration tests (JUnit, Testcontainers).
-   Add GitHub Actions CI/CD pipeline.
