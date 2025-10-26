# API Gateway Architecture Guide

## Overview

The FinPay API Gateway is built using **Spring Cloud Gateway** with a reactive, non-blocking architecture. It serves as the single entry point for all client requests, providing routing, security, rate limiting, and resilience patterns.

---

## Table of Contents

1. [Technology Stack](#technology-stack)
2. [Reactive Architecture](#reactive-architecture)
3. [Circuit Breaker Implementation](#circuit-breaker-implementation)
4. [Rate Limiting](#rate-limiting)
5. [Security Integration](#security-integration)
6. [Routing Configuration](#routing-configuration)
7. [Why WebFlux vs Spring MVC](#why-webflux-vs-spring-mvc)

---

## Technology Stack

### Core Dependencies

```xml
<!-- Spring Cloud Gateway - Built on WebFlux -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>

<!-- Reactive Web Framework -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- Circuit Breaker with Resilience4j -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>

<!-- Reactive Redis for Rate Limiting -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

**Reference:** [pom.xml](pom.xml)

---

## Reactive Architecture

### What is Reactive Programming?

Reactive programming is an **asynchronous, non-blocking** programming paradigm designed to handle high concurrency with minimal resources.

### Traditional vs Reactive

#### Traditional Spring MVC (Blocking)
```
Thread 1: Receive Request → Call Service → WAIT (blocked) → Response
Thread 2: Receive Request → Call Service → WAIT (blocked) → Response
Thread 3: Receive Request → Call Service → WAIT (blocked) → Response
```
- **One thread per request**
- Threads block while waiting for I/O
- Limited scalability (200 threads = ~200 concurrent requests)

#### Spring WebFlux (Non-Blocking)
```
Thread 1: Receive Request A → Register callback → Handle Request B → Handle Request C
          ↓ (callback fires when service responds)
Thread 1: Process Response A → Send to client
```
- **Few threads handle many requests**
- Threads never block on I/O
- High scalability (4-8 threads = 10,000+ concurrent requests)

### The Reactive Stack

```
┌─────────────────────────────────────┐
│  Spring Cloud Gateway (Routing)    │
├─────────────────────────────────────┤
│  Spring WebFlux (Reactive Web)     │
├─────────────────────────────────────┤
│  Project Reactor (Mono/Flux)       │
├─────────────────────────────────────┤
│  Reactor Netty (Wrapper)           │
├─────────────────────────────────────┤
│  Netty (HTTP Server)               │  ← Event-driven, non-blocking
├─────────────────────────────────────┤
│  Operating System (NIO)            │
└─────────────────────────────────────┘
```

### Netty - The Hidden Engine

**Netty** is automatically included as a transitive dependency:

```
spring-boot-starter-webflux
  └── spring-boot-starter-reactor-netty
       └── reactor-netty-http (v1.1.18)
            └── io.netty:netty-* (v4.1.109.Final)
```

**What is Netty?**
- High-performance, asynchronous, event-driven network framework
- Handles TCP/IP, HTTP/1.1, HTTP/2, WebSockets
- Powers the gateway's non-blocking I/O

**Netty's Event Loop Model:**
```
Event Loop (4-8 threads)
├── Thread 1: Accept connections, read HTTP requests
├── Thread 2: Route requests, apply filters
├── Thread 3: Handle responses, write to sockets
└── Thread 4: Async operations (Redis, circuit breaker)
```

### Reactive Code Examples

#### Explicit Reactive Code
**File:** [config/RateLimiterConfig.java](src/main/java/com/finpay/gateway/config/RateLimiterConfig.java)

```java
import reactor.core.publisher.Mono;  // Reactive type

@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Mono.just(authHeader.substring(7));  // Returns async publisher
        }
        return Mono.just("anonymous");
    };
}
```

**Key Reactive Types:**
- `Mono<T>`: Publisher of 0 or 1 item (like `Optional` but async)
- `Flux<T>`: Publisher of 0 to N items (like `Stream` but async)

#### Implicit Reactive Code
**File:** [config/GatewayRoutesConfig.java](src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java)

All routing is reactive under the hood, even though you don't see `Mono`/`Flux`:

```java
builder.routes()
    .route("transaction-service", r -> r.path("/transactions/**")
        .filters(f -> f.circuitBreaker(c -> c
            .setName("transactionCB")
            .setFallbackUri("forward:/fallback/transactions")))
        .uri("http://localhost:8083"))
```

**What happens:**
1. Request arrives (non-blocking)
2. Filters applied (reactive pipeline)
3. Forward to backend (non-blocking I/O)
4. Thread freed to handle other requests
5. Callback processes response when it arrives

---

## Circuit Breaker Implementation

### Why Circuit Breaker?

Protects the system from cascading failures when downstream services are unavailable or slow.

### Resilience4j vs Hystrix

| Feature | Hystrix | Resilience4j (Used Here) |
|---------|---------|--------------------------|
| Status | **Deprecated (2018)** | **Actively maintained** |
| Design | Thread pool isolation | Lightweight, functional |
| Reactive Support | Limited | Excellent (built-in) |
| Compatibility | Blocking apps | Reactive + blocking apps |

**Why Resilience4j?**
- Modern alternative to deprecated Hystrix
- Built for Java 8+ and functional programming
- Native reactive support (perfect for WebFlux)
- Spring Cloud official recommendation

### Configuration

**File:** [application.yml](src/main/resources/application.yml)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      transactionCB:
        failureRateThreshold: 50        # Open circuit if 50% requests fail
        waitDurationInOpenState: 10s    # Wait 10s before attempting recovery
        slidingWindowSize: 10            # Evaluate last 10 requests
  timelimiter:
    instances:
      transactionCB:
        timeoutDuration: 5s             # Request timeout: 5 seconds
```

### Circuit Breaker States

```
┌──────────────┐
│   CLOSED     │ ← Normal operation, requests pass through
│ (Monitoring) │
└──────┬───────┘
       │ 50% failure rate detected
       ↓
┌──────────────┐
│     OPEN     │ ← Circuit tripped, return fallback immediately
│  (Failing)   │   No requests to backend
└──────┬───────┘
       │ Wait 10 seconds
       ↓
┌──────────────┐
│  HALF-OPEN   │ ← Test request to check recovery
│  (Testing)   │
└──────┬───────┘
       │ Success → Close
       │ Failure → Open again
       ↓
```

### Route with Circuit Breaker

**File:** [config/GatewayRoutesConfig.java](src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java)

```java
.route("transaction-service", r -> r.path("/transactions/**")
    .filters(f -> f.circuitBreaker(c -> c
        .setName("transactionCB")                          // Links to config
        .setFallbackUri("forward:/fallback/transactions"))) // Fallback endpoint
    .uri("http://localhost:8083"))
```

### Fallback Handler

**File:** [controller/FallbackController.java](src/main/java/com/finpay/gateway/controller/FallbackController.java)

```java
@RestController
public class FallbackController {
    @RequestMapping("/fallback/transactions")
    public ResponseEntity<String> transactionFallback() {
        return ResponseEntity.ok(
            "Transaction Service is currently unavailable. Please try again later."
        );
    }
}
```

### How It Works

1. **Normal Operation:** Requests go to transaction service
2. **Failures Detected:** 5 out of 10 requests fail
3. **Circuit Opens:** Immediately return fallback response
4. **Recovery Attempt:** After 10s, allow one test request
5. **Success:** Circuit closes, normal operation resumes

---

## Rate Limiting

### Purpose

Prevent API abuse and ensure fair resource allocation across clients.

### Implementation

**File:** [config/RateLimiterConfig.java](src/main/java/com/finpay/gateway/config/RateLimiterConfig.java)

```java
@Bean
public RedisRateLimiter redisRateLimiter() {
    // Parameters: replenishRate, burstCapacity
    return new RedisRateLimiter(1, 10);
}
```

**Parameters:**
- **Replenish Rate (1):** 1 request per second steady rate
- **Burst Capacity (10):** Allow bursts up to 10 requests

### Key Resolver

Determines how to identify clients (rate limit per user):

```java
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        String authHeader = exchange.getRequest()
            .getHeaders()
            .getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Mono.just(authHeader.substring(7)); // Use JWT token as key
        }
        return Mono.just("anonymous"); // Anonymous users share limit
    };
}
```

### Route with Rate Limiting

**File:** [config/GatewayRoutesConfig.java](src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java)

```java
.route("fraud-service", r -> r.path("/frauds/**")
    .filters(f -> f.requestRateLimiter(c -> {
        c.setRateLimiter(redisRateLimiter);
        c.setKeyResolver(userKeyResolver);
    }))
    .uri("http://localhost:8085"))
```

### Redis Dependency

**File:** [application.yml](src/main/resources/application.yml)

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**Why Redis?**
- Fast in-memory data store
- Supports distributed rate limiting (multiple gateway instances)
- Atomic operations for accurate counting

---

## Security Integration

### Gateway Security Posture

The gateway itself **does not implement authentication**. It delegates to:
1. **Auth Service:** Generates JWT tokens
2. **Microservices:** Validate JWT tokens

### CORS Configuration

**File:** [application.yml](src/main/resources/application.yml)

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:5173"
              - "http://localhost:5174"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
```

**Purpose:** Allow browser-based clients to access the API from specific origins.

### Security Flow

```
1. Client → POST /auth-services/login (credentials)
   ↓
2. Gateway → Forward to Auth Service (port 8081)
   ↓
3. Auth Service → Validate & generate JWT
   ↓
4. Client receives JWT token
   ↓
5. Client → Request with Authorization: Bearer <JWT>
   ↓
6. Gateway → Forward to microservice with JWT
   ↓
7. Microservice → Validate JWT & process request
```

---

## Routing Configuration

### All Routes

**File:** [config/GatewayRoutesConfig.java](src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java)

| Route | Path | Backend | Filters |
|-------|------|---------|---------|
| auth-service | `/auth-services/**` | `localhost:8081` | None |
| account-service | `/accounts/**` | `localhost:8082` | None |
| transaction-service | `/transactions/**` | `localhost:8083` | Circuit Breaker |
| notification-service | `/notifications/**` | `localhost:8084` | None |
| fraud-service | `/frauds/**` | `localhost:8085` | Rate Limiter |

### Swagger/OpenAPI Routes

The gateway aggregates API documentation from all services:

```java
.route("auth-docs", r -> r.path("/v3/api-docs/auth")
    .filters(f -> f.rewritePath("/v3/api-docs/auth", "/v3/api-docs"))
    .uri("http://localhost:8081"))
```

**Access:** http://localhost:8080/swagger-ui.html

---

## Why WebFlux vs Spring MVC

### Architecture Comparison

| Component | Technology | Reason |
|-----------|------------|--------|
| **API Gateway** | WebFlux + Netty | High concurrency, minimal computation |
| **Microservices** | Spring MVC + Tomcat | Business logic, blocking DB operations |

### Gateway Requirements

**Perfect for WebFlux:**
- Routes 1000s of concurrent requests
- Mostly I/O operations (forwarding requests)
- Minimal computation
- Benefits from non-blocking I/O

### Microservice Requirements

**Better with Spring MVC:**
- Traditional CRUD operations
- JPA/Hibernate (blocking by nature)
- Complex business logic
- Simpler code, easier debugging
- Mature ecosystem

### Hybrid Architecture Diagram

```
┌─────────────────────────────────────────┐
│        Client Applications              │
│   (Web, Mobile, Third-party APIs)       │
└──────────────────┬──────────────────────┘
                   │
                   ↓
        ┌──────────────────────┐
        │   API Gateway        │
        │   Port: 8080         │
        │   WebFlux/Netty      │ ← Reactive, Non-blocking
        │   - Routing          │   (Few threads, high concurrency)
        │   - Circuit Breaker  │
        │   - Rate Limiting    │
        │   - CORS             │
        └──────────┬───────────┘
                   │
    ┌──────────────┼──────────────┬──────────────┐
    │              │              │              │
    ↓              ↓              ↓              ↓
┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐
│ Auth   │   │Account │   │ Trans. │   │ Fraud  │
│ :8081  │   │ :8082  │   │ :8083  │   │ :8085  │
│ MVC    │   │ MVC    │   │ MVC    │   │ MVC    │ ← Blocking, Traditional
└───┬────┘   └───┬────┘   └───┬────┘   └───┬────┘   (Thread-per-request)
    │            │            │            │
    └────────────┴────────────┴────────────┘
                       │
                       ↓
            ┌──────────────────────┐
            │   PostgreSQL         │
            │   (Relational DB)    │
            └──────────────────────┘
```

### Performance Comparison

#### Gateway (WebFlux/Netty)
- **Threads:** 4-8 event loop threads
- **Concurrent Connections:** 10,000+
- **Memory:** Low (no thread stack overhead)
- **Latency:** Microseconds (routing overhead)

#### Microservices (Spring MVC/Tomcat)
- **Threads:** 200 default (configurable)
- **Concurrent Requests:** ~200 (thread pool size)
- **Memory:** Higher (each thread ~1MB stack)
- **Latency:** Depends on business logic + DB

### When to Use WebFlux

✅ **Good Use Cases:**
- API Gateways
- Proxy services
- Event-driven applications
- Streaming data
- Microservices with high I/O, low computation

❌ **Avoid WebFlux:**
- Traditional CRUD apps with JPA
- Synchronous business logic
- Team unfamiliar with reactive programming
- Blocking libraries (JDBC, traditional Hibernate)

---

## Key Takeaways

1. **Reactive Architecture:** WebFlux + Netty enable high concurrency with minimal resources
2. **Circuit Breaker:** Resilience4j (not Hystrix) protects against cascading failures
3. **Rate Limiting:** Redis-based limiting prevents abuse
4. **Hybrid Stack:** Gateway is reactive, microservices are traditional MVC
5. **Netty Powers Everything:** Hidden under the hood, handling all network I/O
6. **Best Practices:** Use reactive for gateways, traditional for business services

---

## References

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Project Reactor](https://projectreactor.io/)
- [Resilience4j](https://resilience4j.readme.io/)
- [Netty](https://netty.io/)

---

## Project Files Reference

- **Configuration:** [application.yml](src/main/resources/application.yml)
- **Routes:** [GatewayRoutesConfig.java](src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java)
- **Rate Limiter:** [RateLimiterConfig.java](src/main/java/com/finpay/gateway/config/RateLimiterConfig.java)
- **Fallback:** [FallbackController.java](src/main/java/com/finpay/gateway/controller/FallbackController.java)
- **Dependencies:** [pom.xml](pom.xml)
