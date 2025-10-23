# FinPay - Complete Learning Guide

Welcome to FinPay! This comprehensive guide will help you understand every aspect of this microservices-based banking system. Follow this guide sequentially to build a deep understanding of modern distributed systems.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Deep Dive](#architecture-deep-dive)
3. [Microservices Explained](#microservices-explained)
4. [Key Design Patterns](#key-design-patterns)
5. [Data Flow & Communication](#data-flow--communication)
6. [Observability Stack](#observability-stack)
7. [Hands-On Learning Exercises](#hands-on-learning-exercises)
8. [Common Scenarios & Troubleshooting](#common-scenarios--troubleshooting)

---

## System Overview

### What is FinPay?

FinPay is a **production-ready microservices architecture** demonstrating how to build a scalable, secure, and observable banking/payment system. It showcases enterprise patterns used by companies like PayPal, Stripe, and Square.

### Tech Stack at a Glance

| Layer | Technologies |
|-------|-------------|
| **Backend** | Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1 |
| **Database** | PostgreSQL 16 (transactional data) |
| **Cache** | Redis 7 (read optimization, rate limiting) |
| **Messaging** | Apache Kafka 7.6.0 (event streaming) |
| **API Gateway** | Spring Cloud Gateway (routing, security) |
| **Security** | Spring Security, JWT, OAuth2 Resource Server |
| **Tracing** | Zipkin (distributed tracing) |
| **Metrics** | Prometheus + Grafana |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) |
| **Testing** | JUnit 5, Mockito, TestContainers |

### Core Features

- **Multi-user Accounts**: Create accounts with email ownership
- **Secure Transfers**: Money transfers with ACID guarantees
- **Fraud Detection**: Rule-based fraud checking (amount, velocity, geo)
- **Idempotency**: Prevent duplicate transactions with idempotency keys
- **Audit Trail**: Double-entry ledger for complete transaction history
- **Real-time Notifications**: Email/SMS via Kafka events
- **Role-Based Access**: Admin, Auditor, User roles

---

## Architecture Deep Dive

### High-Level Architecture

```
┌─────────────┐
│   Client    │
│ (Browser/   │
│   Mobile)   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│      API Gateway (Port 8080)        │
│  - Rate Limiting (Redis)            │
│  - JWT Validation                   │
│  - Request Routing                  │
└──────┬──────────────────────────────┘
       │
       ├──────────────┬────────────┬────────────┬───────────────┐
       ▼              ▼            ▼            ▼               ▼
┌──────────┐   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│   Auth   │   │ Account  │  │Transaction│ │  Fraud   │  │Notification│
│ Service  │   │ Service  │  │ Service   │ │ Service  │  │  Service  │
│ :8081    │   │ :8082    │  │  :8083    │ │  :8085   │  │   :8086   │
└────┬─────┘   └────┬─────┘  └────┬──────┘ └────┬─────┘  └─────┬─────┘
     │              │             │             │              │
     └──────┬───────┴─────┬───────┴──────┬──────┴──────────────┘
            │             │              │
            ▼             ▼              ▼
     ┌────────────┐  ┌─────────┐   ┌─────────┐
     │ PostgreSQL │  │  Redis  │   │  Kafka  │
     │   :5432    │  │  :6379  │   │  :9092  │
     └────────────┘  └─────────┘   └─────────┘
```

### Observability Stack

```
┌──────────────────────────────────────────┐
│          All Microservices               │
│  - Emit Metrics (Micrometer)             │
│  - Send Traces (Zipkin)                  │
│  - Stream Logs (Logstash)                │
└──────┬──────────────┬──────────┬─────────┘
       │              │          │
       ▼              ▼          ▼
┌────────────┐  ┌─────────┐  ┌────────────┐
│ Prometheus │  │ Zipkin  │  │ Logstash   │
│   :9090    │  │  :9411  │  │   :5001    │
└─────┬──────┘  └─────────┘  └──────┬─────┘
      │                             │
      ▼                             ▼
┌────────────┐              ┌────────────────┐
│  Grafana   │              │ Elasticsearch  │
│   :3000    │              │     :9200      │
└────────────┘              └────────┬───────┘
                                     │
                                     ▼
                            ┌────────────────┐
                            │    Kibana      │
                            │     :5601      │
                            └────────────────┘
```

### Service Dependencies

```
Transaction Service
├── Depends on: Account Service (Feign Client)
├── Depends on: Fraud Service (Feign Client)
├── Depends on: Notification Service (Feign Client)
└── Publishes to: Kafka (TransactionCreatedEvent)

Notification Service
└── Consumes from: Kafka (transaction events)

Fraud Service
└── Receives calls from: Transaction Service

Account Service
└── Receives calls from: Transaction Service

Auth Service
└── Standalone (issues JWT tokens)
```

---

## Microservices Explained

### 1. Auth Service (Port 8081)

**Purpose**: Central authentication and user management

**Key Components**:
- [AuthController.java](auth-service/src/main/java/com/finpay/authservice/controllers/AuthController.java) - Login, Register endpoints
- [UserEntity.java](auth-service/src/main/java/com/finpay/authservice/models/UserEntity.java) - User model with roles
- [SpringSecurityConfiguration.java](auth-service/src/main/java/com/finpay/authservice/securities/SpringSecurityConfiguration.java) - Security config

**Responsibilities**:
- User registration and login
- JWT token generation
- Password hashing (BCrypt)
- Role-based access control (ADMIN, AUDITOR, USER)
- User location tracking

**Database Tables**:
```sql
users (id, username, email, password, role_id, location_id)
roles (id, role_name)
locations (id, country, city, ip_address)
```

**API Endpoints**:
- `POST /auth/register` - Create new user
- `POST /auth/login` - Get JWT token
- `GET /users/{id}` - Get user details

**Learning Focus**:
- Spring Security configuration
- JWT token creation and validation
- Password encoding with BCrypt
- Role-based authorization
- Entity relationships (ManyToOne)

---

### 2. Account Service (Port 8082)

**Purpose**: Manage user accounts and balances

**Key Components**:
- [AccountController.java](account-service/src/main/java/com/finpay/accounts/controllers/AccountController.java) - CRUD operations
- [Account.java](account-service/src/main/java/com/finpay/accounts/models/Account.java) - Account entity
- [AccountService.java](account-service/src/main/java/com/finpay/accounts/services/AccountService.java) - Business logic

**Responsibilities**:
- Create accounts for users
- Track account balances with `BigDecimal` precision
- Handle debit/credit operations
- Prevent negative balances
- Account ownership via email

**Database Tables**:
```sql
accounts (id, owner_email, balance)
```

**API Endpoints**:
- `POST /accounts` - Create account
- `GET /accounts/{id}` - Get account details
- `GET /accounts/email/{email}` - Get account by owner email
- `POST /accounts/{id}/debit` - Debit amount
- `POST /accounts/{id}/credit` - Credit amount

**Learning Focus**:
- BigDecimal for monetary precision
- Transactional consistency with `@Transactional`
- RESTful API design
- DTO (Data Transfer Object) pattern
- Input validation

---

### 3. Transaction Service (Port 8083)

**Purpose**: Process transfers between accounts with ACID guarantees

**Key Components**:
- [TransactionController.java](transaction-service/src/main/java/com/finpay/transactions/controllers/TransactionController.java) - Transfer endpoints
- [Transaction.java](transaction-service/src/main/java/com/finpay/transactions/models/Transaction.java) - Transaction entity
- [TransactionService.java](transaction-service/src/main/java/com/finpay/transactions/services/TransactionService.java) - Transfer orchestration
- [AccountClient.java](transaction-service/src/main/java/com/finpay/transactions/clients/AccountClient.java) - Feign client
- [TransactionProducer.java](transaction-service/src/main/java/com/finpay/transactions/producers/TransactionProducer.java) - Kafka producer

**Responsibilities**:
- Orchestrate money transfers
- Ensure ACID properties
- Implement idempotency via `Idempotency-Key` header
- Call fraud detection
- Publish events to Kafka
- Send notifications on success/failure

**Database Tables**:
```sql
transactions (id, from_account_id, to_account_id, amount, status, idempotency_key, created_at)
```

**Transaction States**:
- `PENDING` - In progress
- `COMPLETED` - Successfully processed
- `FAILED` - Error occurred (can retry)

**API Endpoints**:
- `POST /transactions/transfer` - Initiate transfer (requires `Idempotency-Key` header)
- `GET /transactions/{id}` - Get transaction status

**Learning Focus**:
- Idempotency pattern (crucial for payments!)
- Distributed transactions
- Feign client for inter-service communication
- Kafka event publishing
- Error handling and retry logic
- Distributed tracing with Zipkin

**Transfer Flow**:
```
1. Client sends POST /transactions/transfer with Idempotency-Key
2. Check if transaction already exists with this key
   - If exists: Return existing result (idempotent!)
3. Create PENDING transaction
4. Get account details (Feign call to Account Service)
5. Publish TransactionCreatedEvent to Kafka
6. Debit from source account
7. Credit to destination account
8. Mark transaction as COMPLETED
9. Send success notification
10. Return transaction response
```

---

### 4. Fraud Service (Port 8085)

**Purpose**: Detect and prevent fraudulent transactions

**Key Components**:
- Rule-based fraud detection engine
- Risk scoring system
- Kafka integration for real-time monitoring

**Fraud Rules**:
1. **Amount Threshold**: Flag transactions > $10,000
2. **Velocity Check**: Flag if user has >5 transactions in 10 minutes
3. **Geolocation**: Flag if transaction from unusual location
4. **Pattern Matching**: Detect suspicious patterns

**Database Tables**:
```sql
fraud_events (id, transaction_id, rule_triggered, risk_score, action)
```

**Actions**:
- `ALLOW` - Transaction approved
- `REVIEW` - Manual review required
- `DENY` - Transaction blocked

**Learning Focus**:
- Rule engine implementation
- Risk scoring algorithms
- Real-time event processing
- Prometheus metrics for fraud detection

---

### 5. Notification Service (Port 8086)

**Purpose**: Send email/SMS notifications for events

**Key Components**:
- Kafka consumer for transaction events
- Email sender (Spring Mail)
- SMS integration (placeholder)

**Event Subscriptions**:
- `transaction.created` - New transaction initiated
- `transaction.completed` - Transaction successful
- `transaction.failed` - Transaction error
- `fraud.flagged` - Fraud detected

**Learning Focus**:
- Kafka consumer implementation
- Async event processing
- Spring Mail configuration
- Error handling for external services

---

### 6. API Gateway (Port 8080)

**Purpose**: Single entry point for all client requests

**Key Components**:
- Route configuration
- JWT validation filter
- Rate limiting with Redis
- Request logging

**Responsibilities**:
- Route requests to appropriate services
- Validate JWT tokens
- Apply rate limiting (prevent abuse)
- CORS configuration
- Centralized logging

**Route Examples**:
```yaml
/auth/** → auth-service:8081
/accounts/** → account-service:8082
/transactions/** → transaction-service:8083
```

**Learning Focus**:
- Spring Cloud Gateway
- Route predicates and filters
- Redis-based rate limiting
- WebFlux (reactive programming)

---

## Key Design Patterns

### 1. Idempotency Pattern

**Problem**: Network failures can cause duplicate requests. In banking, processing the same transfer twice is catastrophic!

**Solution**: Use `Idempotency-Key` header

**Implementation** ([TransactionService.java:52](transaction-service/src/main/java/com/finpay/transactions/services/TransactionService.java#L52)):

```java
@Transactional
public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {
    // Check if transaction already exists
    Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);

    if (existing.isPresent()) {
        // Return cached result - NO duplicate processing!
        return toResponse(existing.get());
    }

    // Process new transaction...
}
```

**How to Use**:
```bash
curl -X POST http://localhost:8080/transactions/transfer \
  -H "Idempotency-Key: abc123" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "uuid1",
    "toAccountId": "uuid2",
    "amount": 100.00
  }'
```

If you retry with the same `Idempotency-Key`, you get the same response without re-processing!

---

### 2. Double-Entry Ledger

**Problem**: Need complete audit trail of all money movements

**Solution**: For every transaction, create two ledger entries:
- **DEBIT**: Money leaving source account
- **CREDIT**: Money entering destination account

**Benefits**:
- Always know where money came from and where it went
- Audit compliance
- Balance reconciliation
- Fraud investigation

**Example**:
```
Transaction: Alice transfers $50 to Bob

Ledger Entries:
1. account_id=Alice,  delta=-50, type=DEBIT
2. account_id=Bob,    delta=+50, type=CREDIT

Sum of all deltas MUST = 0 (money conserved!)
```

---

### 3. Transactional Outbox Pattern

**Problem**: How to update database AND send Kafka event atomically?

**Naive Approach (BROKEN)**:
```java
// DANGER: Dual-write problem!
repository.save(tx);        // Write 1: Database
kafka.send(event);          // Write 2: Kafka
// What if Kafka send fails? Database has wrong data!
```

**Outbox Solution**:
```sql
CREATE TABLE outbox (
    id UUID,
    event_type VARCHAR,
    payload JSON,
    published_at TIMESTAMP
);
```

**Flow**:
1. In same DB transaction, insert transaction + outbox entry
2. Background scheduler polls outbox table
3. Publish unpublished events to Kafka
4. Mark as published

**Benefits**:
- Atomic updates (database transaction guarantees)
- At-least-once delivery
- No lost events

---

### 4. Optimistic Locking

**Problem**: Two concurrent transfers from same account might overdraw

**Solution**: Use `@Version` field

```java
@Entity
public class Account {
    @Id
    private UUID id;

    private BigDecimal balance;

    @Version
    private Long version;  // Auto-incremented on each update
}
```

**How it Works**:
```
Thread 1: Read account (version=5)
Thread 2: Read account (version=5)

Thread 1: Update balance, version=6 ✅ SUCCESS
Thread 2: Try update with version=5 ❌ FAIL (OptimisticLockException)

Thread 2 must retry with fresh data
```

---

### 5. Circuit Breaker Pattern

**Problem**: If Account Service is down, Transaction Service will keep making failing calls

**Solution**: Circuit Breaker (implemented in Phase 7)

**States**:
1. **CLOSED**: Normal operation
2. **OPEN**: Too many failures, stop calling service
3. **HALF-OPEN**: Try one request to check if service recovered

**Benefits**:
- Fail fast instead of waiting for timeouts
- Prevent cascading failures
- Automatic recovery detection

---

## Data Flow & Communication

### Synchronous Communication (Feign Clients)

**Example**: Transaction Service → Account Service

[AccountClient.java](transaction-service/src/main/java/com/finpay/transactions/clients/AccountClient.java):
```java
@FeignClient(name = "account-service", url = "http://localhost:8082")
public interface AccountClient {

    @GetMapping("/accounts/{id}")
    AccountDto getAccount(@PathVariable UUID id);

    @PostMapping("/accounts/debit")
    void debit(@RequestBody DebitRequest request);

    @PostMapping("/accounts/credit")
    void credit(@RequestBody CreditRequest request);
}
```

**Benefits**:
- Type-safe HTTP clients
- Automatic serialization/deserialization
- Easy to mock for testing
- Built-in load balancing (with Eureka)

**Drawback**: Tight coupling (service must be available)

---

### Asynchronous Communication (Kafka)

**Example**: Transaction Service → Notification Service

**Producer** ([TransactionProducer.java](transaction-service/src/main/java/com/finpay/transactions/producers/TransactionProducer.java)):
```java
@Service
public class TransactionProducer {

    @Autowired
    private KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    public void sendTransaction(TransactionCreatedEvent event) {
        kafkaTemplate.send("transaction-events", event);
    }
}
```

**Consumer** (Notification Service):
```java
@KafkaListener(topics = "transaction-events")
public void handleTransactionEvent(TransactionCreatedEvent event) {
    // Send email notification
    emailService.send(event.getUserEmail(), "Transaction Completed");
}
```

**Benefits**:
- Loose coupling (fire-and-forget)
- Asynchronous processing
- Event replay capability
- Scalable (multiple consumers)

**When to Use Each**:
- **Synchronous (Feign)**: When you need immediate response (account balance check)
- **Asynchronous (Kafka)**: When you don't need immediate response (notifications, analytics)

---

## Observability Stack

### 1. Distributed Tracing (Zipkin)

**Why**: In microservices, a single request touches multiple services. How to trace the full journey?

**Solution**: Each request gets a unique `trace-id`. All service calls include this ID.

**Setup** ([pom.xml](transaction-service/pom.xml#L94)):
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**Configuration** ([application.yml](transaction-service/src/main/resources/application.yml)):
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% of requests (use 0.1 in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**Viewing Traces**:
1. Open http://localhost:9411
2. Search for traces
3. See the full request flow across services

**Example Trace**:
```
API Gateway [50ms]
  └─> Transaction Service [200ms]
        ├─> Account Service /accounts/uuid1 [30ms]
        ├─> Account Service /debit [50ms]
        ├─> Account Service /credit [40ms]
        └─> Notification Service (Kafka) [10ms]
```

---

### 2. Metrics (Prometheus + Grafana)

**Why**: Need real-time metrics for system health

**Exposed Metrics**:
- HTTP request rates
- Response times (p50, p95, p99)
- Error rates
- JVM metrics (heap, threads, GC)
- Kafka lag
- Database connection pool

**Setup**:
1. Services expose `/actuator/prometheus` endpoint
2. Prometheus scrapes these endpoints every 5s ([prometheus.yml](prometheus.yml))
3. Grafana visualizes the data

**Access**:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

**Key Metrics to Watch**:
- `http_server_requests_seconds_sum` - Total request time
- `jvm_memory_used_bytes` - Memory usage
- `kafka_consumer_lag` - Message processing delay

---

### 3. Centralized Logging (ELK Stack)

**Why**: Logs scattered across services are hard to search

**Flow**:
```
Services → Logstash → Elasticsearch → Kibana
           (collect)   (index)       (visualize)
```

**Setup** ([logback-spring.xml](transaction-service/src/main/resources/logback-spring.xml)):
```xml
<appender name="logstash" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>localhost:5001</destination>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeContext>true</includeContext>
        <includeMdc>true</includeMdc>
    </encoder>
</appender>
```

**Structured Logging**:
```java
log.info("Processing transfer | key={} | from={} | to={} | amount={}",
    idempotencyKey, fromId, toId, amount);
```

**Viewing Logs**:
1. Open Kibana: http://localhost:5601
2. Create index pattern: `logstash-*`
3. Search: `level:ERROR AND service:transaction-service`

**Advanced Searches**:
- All failed transactions: `status:FAILED`
- Slow requests: `duration:>1000`
- Errors from user: `userId:"alice@example.com"`

---

## Hands-On Learning Exercises

### Exercise 1: Setup and Run FinPay

**Goal**: Get the entire system running locally

**Steps**:

1. **Prerequisites**:
```bash
# Check versions
java -version     # Should be 21+
mvn -version      # Should be 3.6+
docker --version  # Should be 20+
```

2. **Start Infrastructure**:
```bash
cd /Users/mengruwang/Github/finpay
docker-compose up -d
```

3. **Verify Containers**:
```bash
docker ps
# Should see: postgres, redis, kafka, zookeeper, prometheus, grafana,
# elasticsearch, kibana, logstash, zipkin, pgadmin
```

4. **Build All Services**:
```bash
mvn clean install -DskipTests
```

5. **Run Each Service** (in separate terminals):
```bash
# Terminal 1
cd auth-service && mvn spring-boot:run

# Terminal 2
cd account-service && mvn spring-boot:run

# Terminal 3
cd transaction-service && mvn spring-boot:run

# Terminal 4
cd fraud-service && mvn spring-boot:run

# Terminal 5
cd notification-service && mvn spring-boot:run

# Terminal 6
cd api-gateway && mvn spring-boot:run
```

6. **Verify Services**:
```bash
curl http://localhost:8081/actuator/health  # Auth
curl http://localhost:8082/actuator/health  # Account
curl http://localhost:8083/actuator/health  # Transaction
curl http://localhost:8085/actuator/health  # Fraud
curl http://localhost:8086/actuator/health  # Notification
curl http://localhost:8080/actuator/health  # Gateway
```

---

### Exercise 2: End-to-End Transaction Flow

**Goal**: Understand the complete flow by executing a real transaction

**Steps**:

1. **Register a User**:
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "firstName": "Alice",
    "lastName": "Smith"
  }'
```

2. **Login to Get JWT**:
```bash
JWT=$(curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "password123"
  }' | jq -r '.token')

echo $JWT
```

3. **Create Two Accounts**:
```bash
# Account A
ACCOUNT_A=$(curl -X POST http://localhost:8082/accounts \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "ownerEmail": "alice@example.com",
    "balance": 1000.00
  }' | jq -r '.id')

# Account B
ACCOUNT_B=$(curl -X POST http://localhost:8082/accounts \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "ownerEmail": "bob@example.com",
    "balance": 500.00
  }' | jq -r '.id')

echo "Account A: $ACCOUNT_A"
echo "Account B: $ACCOUNT_B"
```

4. **Check Balances Before Transfer**:
```bash
curl http://localhost:8082/accounts/$ACCOUNT_A \
  -H "Authorization: Bearer $JWT"

curl http://localhost:8082/accounts/$ACCOUNT_B \
  -H "Authorization: Bearer $JWT"
```

5. **Initiate Transfer**:
```bash
TX_ID=$(curl -X POST http://localhost:8083/transactions/transfer \
  -H "Authorization: Bearer $JWT" \
  -H "Idempotency-Key: transfer-001" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\": \"$ACCOUNT_A\",
    \"toAccountId\": \"$ACCOUNT_B\",
    \"amount\": 200.00
  }" | jq -r '.id')

echo "Transaction ID: $TX_ID"
```

6. **Check Transaction Status**:
```bash
curl http://localhost:8083/transactions/$TX_ID \
  -H "Authorization: Bearer $JWT"
```

7. **Verify Balances After Transfer**:
```bash
# Account A should have 800.00 (1000 - 200)
curl http://localhost:8082/accounts/$ACCOUNT_A \
  -H "Authorization: Bearer $JWT"

# Account B should have 700.00 (500 + 200)
curl http://localhost:8082/accounts/$ACCOUNT_B \
  -H "Authorization: Bearer $JWT"
```

8. **Test Idempotency**:
```bash
# Retry with SAME Idempotency-Key
curl -X POST http://localhost:8083/transactions/transfer \
  -H "Authorization: Bearer $JWT" \
  -H "Idempotency-Key: transfer-001" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\": \"$ACCOUNT_A\",
    \"toAccountId\": \"$ACCOUNT_B\",
    \"amount\": 200.00
  }"

# Should return SAME transaction, balances unchanged!
```

9. **Check Traces in Zipkin**:
- Open http://localhost:9411
- Search for traces
- Click on the transfer request
- See the service calls

10. **Check Logs in Kibana**:
- Open http://localhost:5601
- Create index pattern `logstash-*`
- Search for: `transaction_id:"$TX_ID"`
- See logs from all services

---

### Exercise 3: Explore Observability

**Goal**: Use monitoring tools to understand system behavior

**3.1 Prometheus Metrics**:

1. Open http://localhost:9090
2. Query: `http_server_requests_seconds_count`
   - See total requests per service
3. Query: `rate(http_server_requests_seconds_count[1m])`
   - See requests per second
4. Query: `http_server_requests_seconds_sum / http_server_requests_seconds_count`
   - See average response time

**3.2 Grafana Dashboards**:

1. Open http://localhost:3000 (admin/admin)
2. Add Prometheus data source: `http://prometheus:9090`
3. Import dashboard:
   - Dashboard ID: 4701 (JVM Micrometer)
   - Data source: Prometheus
4. Create custom panel:
   - Metric: `http_server_requests_seconds_count{uri="/transactions/transfer"}`
   - Visualization: Graph

**3.3 Kafka Monitoring**:

1. In Grafana, import Kafka dashboard ([docs/Kafka Exporter Overview-1758267773264.json](docs/Kafka%20Exporter%20Overview-1758267773264.json))
2. Check:
   - Message rate
   - Consumer lag
   - Topic size

**3.4 Database Monitoring**:

1. Open PgAdmin: http://localhost:5050 (admin@example.com/finpay)
2. Add server:
   - Hostname: postgres
   - Database: finpay
   - Username: finpay
   - Password: finpay
3. Explore tables:
   - View users, accounts, transactions

---

### Exercise 4: Error Scenarios

**Goal**: Understand error handling and recovery

**4.1 Test Insufficient Balance**:
```bash
# Try to transfer more than account balance
curl -X POST http://localhost:8083/transactions/transfer \
  -H "Authorization: Bearer $JWT" \
  -H "Idempotency-Key: transfer-002" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\": \"$ACCOUNT_A\",
    \"toAccountId\": \"$ACCOUNT_B\",
    \"amount\": 999999.00
  }"

# Should get error response
# Check transaction status (should be FAILED)
```

**4.2 Test Service Unavailability**:
```bash
# Stop Account Service
# Try a transfer
# Should fail with connection error

# Check Zipkin - see the failed call
# Check logs - see the error stack trace
```

**4.3 Test Kafka Failure**:
```bash
# Stop Kafka
docker stop kafka

# Try a transfer
# Transaction should still work (event lost)
# Restart Kafka, check if event was queued
```

---

### Exercise 5: Performance Testing

**Goal**: Understand system limits

**5.1 Concurrent Transfers**:
```bash
# Install Apache Bench
brew install httpd  # macOS

# Send 100 concurrent requests
ab -n 100 -c 10 -p transfer.json -T 'application/json' \
  -H "Authorization: Bearer $JWT" \
  -H "Idempotency-Key: transfer-perf-001" \
  http://localhost:8083/transactions/transfer
```

**5.2 Monitor During Load**:
- Watch CPU/Memory in Grafana
- Check response times
- Monitor Kafka lag
- Check database connections

---

## Common Scenarios & Troubleshooting

### Scenario 1: Service Won't Start

**Symptom**: `Port 8081 already in use`

**Solution**:
```bash
# Find process using port
lsof -i :8081

# Kill the process
kill -9 <PID>

# Or change port in application.yml
```

---

### Scenario 2: Database Connection Fails

**Symptom**: `Connection refused: localhost:5432`

**Checks**:
```bash
# Is PostgreSQL running?
docker ps | grep postgres

# Can you connect manually?
docker exec -it <postgres-container> psql -U finpay -d finpay

# Check application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/finpay
    username: finpay
    password: finpay
```

---

### Scenario 3: Kafka Events Not Consuming

**Symptom**: Notification service not sending emails

**Debug**:
```bash
# Check Kafka topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Check consumer group
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group notification-service

# Check for lag
```

---

### Scenario 4: Zipkin Not Showing Traces

**Checks**:
1. Is Zipkin running? `curl http://localhost:9411/health`
2. Is sampling enabled? `management.tracing.sampling.probability=1.0`
3. Are services sending spans? Check logs for `zipkin` mentions

---

### Scenario 5: JWT Authentication Fails

**Symptom**: `401 Unauthorized`

**Debug**:
```bash
# Decode JWT to check expiration
echo $JWT | cut -d'.' -f2 | base64 -d | jq

# Check if token is valid
curl http://localhost:8081/users/me \
  -H "Authorization: Bearer $JWT"

# Re-login to get fresh token
```

---

## Next Steps

After mastering this learning guide, you're ready for:

1. **Phase 2**: Add comprehensive tests (see [TESTING-GUIDE.md](TESTING-GUIDE.md))
2. **Phase 3**: Implement Eureka service discovery
3. **Phase 4**: Set up CI/CD with GitHub Actions
4. **Phase 5**: Add Flyway database migrations
5. **Phase 6**: Implement circuit breakers with Resilience4j
6. **Phase 7**: Add API versioning

---

## Resources

### Official Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- [Spring Security](https://docs.spring.io/spring-security/reference/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Manual](https://www.postgresql.org/docs/)

### Microservices Patterns
- [Microservices.io Patterns](https://microservices.io/patterns/)
- [Martin Fowler on Microservices](https://martinfowler.com/microservices/)

### Books
- *Building Microservices* by Sam Newman
- *Spring Microservices in Action* by John Carnell
- *Release It!* by Michael Nygard (resilience patterns)

---

## Conclusion

Congratulations on completing the FinPay learning guide! You now understand:

- Microservices architecture
- Event-driven design with Kafka
- Distributed tracing and observability
- Idempotency and transaction safety
- Spring Boot and Spring Cloud ecosystem

**Key Takeaways**:
1. Idempotency is CRITICAL in distributed systems
2. Observability (metrics, tracing, logging) is not optional
3. Async communication (Kafka) enables loose coupling
4. Security must be designed in from the start
5. Testing is essential (coming in Phase 2!)

Keep learning, keep building!
