# TSO Redispatching Order Service - Claude Code Instructions

## Project Overview

This project implements a **TSO (Transmission System Operator) Redispatching Order Service** for sending redispatching orders to renewable energy generator entities. The service uses **Server-Sent Events (SSE)** for real-time notifications and **REST** for data exchange.

### Business Context
- TSO operators issue redispatching orders to energy entities
- Entities subscribe to SSE streams to receive order notifications
- Upon receiving an `ORDER_ISSUED` event, entities fetch order details via REST
- Entities acknowledge orders with status: `RECEIVED`, `ACCEPTED`, or `REJECTED`

### API Specification
The OpenAPI specification is in `docs/redispatching-order-openapi.yml`. Always refer to it for schema definitions and endpoint contracts.

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| Framework | Spring Boot 3.4.x |
| API Gateway | Spring Cloud Gateway |
| Build Tool | Maven 3.9+ |
| SSE | Spring WebFlux (Reactor) |
| JSON | Jackson |
| Testing | JUnit 5, MockMvc, WebTestClient |

---

## Project Structure

```
tso-redispatching/
├── pom.xml                          # Parent POM
├── docs/
│   └── redispatching-order-openapi.yml
├── service/                         # API Implementation Module
│   ├── pom.xml
│   └── src/main/java/
│       └── pl/tso/redispatch/service/
├── gateway/                         # Spring Cloud Gateway Module
│   ├── pom.xml
│   └── src/main/java/
│       └── pl/tso/redispatch/gateway/
└── client/                          # Test Client Module
    ├── pom.xml
    └── src/main/java/
        └── pl/tso/redispatch/client/
```

---

## Module Details

### 1. Service Module (`service`)

**Purpose:** Implements the Redispatching API (SSE + REST endpoints).

**Package Structure:**
```
pl.tso.redispatch.service
├── RedispatchServiceApplication.java
├── controller/
│   └── RedispatchController.java       # Single controller for all endpoints
├── model/                              # DTOs matching OpenAPI schemas
│   ├── event/
│   │   ├── ConnectedEvent.java
│   │   ├── HeartbeatEvent.java
│   │   └── RedispatchOrderIssuedEvent.java
│   ├── RedispatchOrder.java
│   ├── RedispatchOrderItem.java
│   ├── RedispatchOrderPeriod.java
│   ├── SeriesPeriod.java
│   ├── SeriesPoint.java
│   ├── TimeInterval.java
│   └── RedispatchAcknowledgement.java
├── service/
│   ├── RedispatchOrderService.java     # Business logic + mock data
│   └── SseEmitterService.java          # SSE connection management
└── config/
    └── WebConfig.java                  # CORS, etc.
```

**Key Implementation Requirements:**

#### SSE Endpoint (`GET /redispatch/{entityId}/stream`)
- Use `SseEmitter` or reactive `Flux<ServerSentEvent<T>>` approach
- Send `connected` event immediately on connection
- Send `heartbeat` events every 30 seconds
- Support `Last-Event-ID` header for event replay (store events in memory with incrementing IDs)
- Generate mock `ORDER_ISSUED` events every 60-90 seconds (random interval)

#### REST Endpoints
- `GET /redispatch/{entityId}/orders/{redispatchOrderId}` - Return mock order data
- `POST /redispatch/{entityId}/orders/{redispatchOrderId}/acknowledgement` - Accept and log acknowledgement

#### Mock Data Generation
- Use `java.util.Random` or `ThreadLocalRandom` for random data
- Order IDs format: `{sequence}/I/{dd.MM.yyyy}` (e.g., "1/I/22.07.2025")
- Entity IDs: 5-character strings (e.g., "ENT01", "ENT02")
- Generate realistic series points with quantityMin < quantityMax

### 2. Gateway Module (`gateway`)

**Purpose:** API Gateway routing requests to the service.

**Package Structure:**
```
pl.tso.redispatch.gateway
├── RedispatchGatewayApplication.java
└── config/
    └── GatewayConfig.java
```

**Configuration (`application.yml`):**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: redispatch-service
          uri: http://localhost:8081
          predicates:
            - Path=/v1/redispatch/**
          filters:
            - StripPrefix=1
```

**Requirements:**
- Route all `/v1/redispatch/**` requests to service module
- Support SSE streaming (configure appropriate timeouts)
- Run on port 8080

### 3. Client Module (`client`)

**Purpose:** Test client demonstrating the full workflow.

**Package Structure:**
```
pl.tso.redispatch.client
├── RedispatchClientApplication.java
├── client/
│   ├── SseClient.java                  # SSE subscription handling
│   └── RedispatchApiClient.java        # REST client for GET/POST
└── handler/
    └── OrderEventHandler.java          # Process received events
```

**Requirements:**
- Connect to SSE endpoint via gateway
- Handle `connected`, `heartbeat`, and `ORDER_ISSUED` events
- On `ORDER_ISSUED`: fetch order details, then send `RECEIVED` acknowledgement
- Log all events and responses
- Implement reconnection with `Last-Event-ID` on connection loss
- Use `WebClient` for both SSE and REST calls

---

## Implementation Guidelines

### KISS Principles - Follow Strictly

1. **No unnecessary abstractions** - Don't create interfaces with single implementations
2. **No service layer if controller can handle it** - For mock data, controller + one service class is enough
3. **Use Java records** for DTOs where immutability is desired
4. **Avoid over-engineering** - No factory patterns, no builders unless genuinely needed
5. **Flat package structure** - Don't nest packages more than 2 levels deep

### Code Style

```java
// GOOD: Simple record for DTO
public record ConnectedEvent(
    String eventType,
    UUID connectionId,
    Instant timestamp
) {
    public static ConnectedEvent create() {
        return new ConnectedEvent("connected", UUID.randomUUID(), Instant.now());
    }
}

// BAD: Over-engineered with builder
public class ConnectedEvent {
    private final String eventType;
    // ... boilerplate
    public static class Builder { /* unnecessary */ }
}
```

### SSE Implementation Pattern

Use Spring WebFlux with `Flux<ServerSentEvent<?>>`:

```java
@GetMapping(path = "/redispatch/{entityId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<?>> streamEvents(
        @PathVariable String entityId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    
    return sseService.createEventStream(entityId, lastEventId);
}
```

### Mock Data Strategy

```java
@Service
public class RedispatchOrderService {
    
    private final AtomicLong orderSequence = new AtomicLong(1);
    private final Map<String, RedispatchOrder> orderStore = new ConcurrentHashMap<>();
    
    public RedispatchOrder generateMockOrder(String entityId) {
        var orderId = orderSequence.getAndIncrement() + "/I/" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        
        var order = createRandomOrder(orderId, entityId);
        orderStore.put(orderId, order);
        return order;
    }
}
```

### Error Handling

- Use `@ControllerAdvice` for global exception handling
- Return proper HTTP status codes (404 for not found, 400 for bad request)
- Log errors with appropriate levels

---

## Configuration

### Service Module (`service/src/main/resources/application.yml`)
```yaml
server:
  port: 8081

spring:
  application:
    name: redispatch-service

# SSE Configuration
sse:
  heartbeat-interval-seconds: 30
  order-generation-interval-seconds: 60
```

### Gateway Module (`gateway/src/main/resources/application.yml`)
```yaml
server:
  port: 8080

spring:
  application:
    name: redispatch-gateway
  cloud:
    gateway:
      routes:
        - id: redispatch-sse
          uri: http://localhost:8081
          predicates:
            - Path=/v1/redispatch/{entityId}/stream
          filters:
            - StripPrefix=1
        - id: redispatch-api
          uri: http://localhost:8081
          predicates:
            - Path=/v1/redispatch/**
          filters:
            - StripPrefix=1
      httpclient:
        response-timeout: 0  # No timeout for SSE
```

### Client Module (`client/src/main/resources/application.yml`)
```yaml
spring:
  application:
    name: redispatch-client

client:
  gateway-url: http://localhost:8080/v1
  entity-id: ENT01
```

---

## Maven Configuration

### Parent POM Requirements
```xml
<properties>
    <java.version>25</java.version>
    <spring-boot.version>3.4.1</spring-boot.version>
    <spring-cloud.version>2024.0.0</spring-cloud.version>
</properties>
```

### Module Dependencies

**Service:**
- `spring-boot-starter-webflux`
- `spring-boot-starter-validation`

**Gateway:**
- `spring-cloud-starter-gateway`

**Client:**
- `spring-boot-starter-webflux`

---

## Testing Requirements

### Service Module Tests
1. **Unit tests** for `RedispatchOrderService` - mock data generation
2. **Integration tests** with `WebTestClient`:
   - SSE connection and event reception
   - GET order details
   - POST acknowledgement

### Client Module
- Runs as executable demonstrating full flow
- Command line output showing events and responses

---

## Development Workflow

### Build
```bash
mvn clean install
```

### Run (in order)
```bash
# Terminal 1: Service
cd service && mvn spring-boot:run

# Terminal 2: Gateway
cd gateway && mvn spring-boot:run

# Terminal 3: Client
cd client && mvn spring-boot:run
```

---

## Security Note

For this mock implementation, **skip OAuth2 security**. Add a comment in code indicating where security would be implemented:

```java
// TODO: Add OAuth2 security with scopes: redispatch.read, redispatch.write
// See OpenAPI spec securitySchemes for details
```

---

## Do NOT Do

1. ❌ Don't create separate interfaces for services with single implementations
2. ❌ Don't use Lombok (Java records are sufficient for DTOs)
3. ❌ Don't implement database persistence (use in-memory storage)
4. ❌ Don't add OAuth2 security implementation (mock only)
5. ❌ Don't create excessive configuration classes
6. ❌ Don't use abstract base classes for DTOs
7. ❌ Don't add OpenAPI/Swagger UI generation (spec is already provided)

---

## Checklist Before Completion

- [ ] All three modules compile and run independently
- [ ] SSE stream delivers `connected`, `heartbeat`, `ORDER_ISSUED` events
- [ ] `Last-Event-ID` replay works correctly
- [ ] REST GET returns mock order with all required fields
- [ ] REST POST accepts acknowledgement and returns 202
- [ ] Client successfully receives events and completes workflow
- [ ] Gateway correctly routes both SSE and REST traffic
- [ ] Code follows KISS principles - minimal abstractions
- [ ] All DTOs match OpenAPI schema exactly
