# Load Test Implementation Summary

## What Was Implemented

A complete Java-based Gatling load testing suite for the TSO Redispatching Order Service with support for testing up to 1000+ concurrent SSE connections.

## Project Structure

```
load-test/
├── pom.xml                                          # Maven configuration
├── README.md                                        # Full documentation
├── QUICK_START.md                                   # Quick start guide
├── scripts/
│   ├── run-baseline.sh                             # Run SSE baseline test
│   ├── run-workflow.sh                             # Run full workflow test
│   ├── run-stress.sh                               # Run stress test
│   └── run-all.sh                                  # Run all tests
└── src/test/
    ├── java/pl/tso/redispatch/loadtest/
    │   ├── config/
    │   │   └── LoadTestConfig.java                 # Centralized configuration
    │   ├── utils/
    │   │   └── EntityIdFeeder.java                 # Entity ID generators
    │   └── scenarios/
    │       ├── SseBaselineScenario.java            # Scenario 1: SSE connections only
    │       ├── FullWorkflowScenario.java           # Scenario 2: Complete workflow
    │       └── StressTestScenario.java             # Scenario 3: System limits
    └── resources/
        ├── gatling.conf                            # Gatling configuration
        ├── logback-test.xml                        # Logging configuration
        └── entity-ids.csv                          # Test data
```

## Three Test Scenarios

### 1. SSE Baseline Scenario
**Purpose:** Test SSE connection capacity

**What it does:**
- Opens 1000 concurrent SSE connections
- Waits for 'connected' event on each connection
- Keeps connections alive for 10 minutes (receives heartbeats)
- Measures connection success rate and stability

**How to run:**
```bash
./scripts/run-baseline.sh
# Or with custom parameters:
./scripts/run-baseline.sh http://localhost:8080 500 180 300
```

**Success criteria:**
- ≥95% connection success rate

### 2. Full Workflow Scenario
**Purpose:** Test realistic end-to-end workflow

**What it does:**
- Opens 1000 SSE connections
- Waits for 'ORDER_ISSUED' events
- Fetches order details using resourceUrl from SSE event (handles encoded URLs correctly)
- Sends acknowledgements via POST
- Repeats 3 times per connection
- Measures end-to-end latency and API performance

**How to run:**
```bash
./scripts/run-workflow.sh
# Or with custom parameters:
./scripts/run-workflow.sh http://localhost:8080 1000 300 900
```

**Success criteria:**
- ≥95% request success rate
- p95 response time <2000ms
- p99 response time <5000ms
- GET order p95 <1000ms
- POST ack p95 <500ms

### 3. Stress Test Scenario
**Purpose:** Find system capacity limits

**What it does:**
- Multi-phase load increase:
  - Phase 1: Ramp 0→500 users (1 min), hold (2 min)
  - Phase 2: Ramp 500→1000 users (1 min), hold (3 min)
  - Phase 3: Ramp 1000→1500 users (1 min), hold (5 min)
- Processes 5 orders per connection
- Accepts some failures (90% success threshold)

**How to run:**
```bash
./scripts/run-stress.sh
# Or with custom max users:
./scripts/run-stress.sh http://localhost:8080 2000
```

**Success criteria:**
- ≥90% success rate (relaxed for stress testing)
- p99 response time <5000ms

## Key Features

### 1. Configurable Parameters
All test parameters can be customized via system properties:

```bash
mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.FullWorkflowScenario \
  -DbaseUrl=http://localhost:8080 \
  -DworkflowUsers=2000 \
  -DrampUp=600 \
  -Dhold=1800
```

Available properties:
- `baseUrl` - Gateway URL
- `baselineUsers`, `workflowUsers`, `stressMaxUsers` - User counts
- `rampUp` - Ramp-up duration (seconds)
- `hold` - Hold duration (seconds)
- `maxEntityId` - Entity ID range (1-N)

### 2. URL Encoding Handling
The implementation correctly handles order IDs with slashes:
- Uses `resourceUrl` from SSE events (already encoded by server)
- Avoids double-encoding issues
- Works with order IDs like `14/I/03.02.2026` → `14%2FI%2F03.02.2026`

### 3. Comprehensive Reporting
Gatling generates HTML reports with:
- Response time percentiles (p50, p95, p99)
- Throughput graphs (requests/second)
- Status code distribution
- Active users over time
- Detailed request statistics

### 4. Entity ID Distribution
Two strategies for distributing load across entity IDs:
- **Random**: `EntityIdFeeder.random()` - Random distribution
- **Sequential**: `EntityIdFeeder.sequential()` - Even distribution

### 5. Performance Assertions
Built-in assertions validate test results:
```java
.assertions(
    global().responseTime().percentile3().lt(2000),  // p95 < 2s
    global().successfulRequests().percent().gte(95.0),  // >95% success
    details("GET Order Details").responseTime().percentile3().lt(1000)  // GET p95 < 1s
)
```

## Technology Stack

- **Gatling 3.10.3** - Load testing framework
- **Java 25** - Implementation language
- **Maven** - Build tool
- **Jackson** - JSON parsing for SSE events
- **Netty** - High-performance networking (via Gatling)

## How to Use

### Quick Test (Small Scale)
```bash
cd load-test

# Build
mvn clean install

# Run small baseline test (100 users, 3 minutes)
./scripts/run-baseline.sh http://localhost:8080 100 60 120

# View report
open target/gatling/ssebaselinescenario-*/index.html
```

### Full Test Suite (1000 Users)
```bash
# SSE Baseline - 1000 connections, 10 min
./scripts/run-baseline.sh

# Full Workflow - 1000 users, realistic behavior, 15 min
./scripts/run-workflow.sh

# Stress Test - up to 1500 users, 13 min
./scripts/run-stress.sh
```

### Run All Tests
```bash
# Sequential execution of all tests (~40 minutes)
./scripts/run-all.sh
```

### Custom Test Execution
```bash
# Via Maven with custom parameters
mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.FullWorkflowScenario \
  -DbaseUrl=http://staging:8080 \
  -DworkflowUsers=500 \
  -DrampUp=300

# Programmatic configuration
# Edit: src/test/java/pl/tso/redispatch/loadtest/config/LoadTestConfig.java
```

## Expected Results

### For 1000 Concurrent Users

**SSE Baseline:**
- 1000 successful connections
- <1s connection establishment time
- 0 disconnections during test
- Stable heartbeat reception

**Full Workflow:**
- >950 successful order fetches (>95%)
- GET order p95: <1000ms
- POST ack p95: <500ms
- Overall p95: <2000ms
- 0 HTTP 5xx errors

**Stress Test:**
- System handles 500-1000 users smoothly
- Some degradation at 1500+ users (acceptable)
- <10% error rate at peak load
- Graceful handling of overload (no crashes)

## Performance Thresholds

Configured in `LoadTestConfig.java`:
```java
PERCENTILE_95_THRESHOLD_MS = 2000   // p95 < 2s
PERCENTILE_99_THRESHOLD_MS = 5000   // p99 < 5s
SUCCESS_RATE_THRESHOLD = 95.0       // >95% success
```

## Monitoring Recommendations

During load tests, monitor:

**Service Metrics:**
- CPU usage (should stay <80%)
- Memory usage (watch for leaks)
- Thread count (check for thread pool exhaustion)
- GC activity (avoid long pauses)

**Network Metrics:**
- Active connections
- Bytes sent/received
- Connection errors

**Application Logs:**
- Check for exceptions
- Monitor error rates
- Watch for timeout messages

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Ensure service and gateway are running
   - Check ports: 8080 (gateway), 8081 (service)

2. **High Failure Rate**
   - Reduce user count
   - Increase ramp-up time
   - Check service logs for errors

3. **Too Many Open Files**
   ```bash
   ulimit -n 65536
   ```

4. **Out of Memory**
   - Increase JVM heap: `-Xmx4g`
   - Reduce concurrent users
   - Decrease test duration

## Integration with CI/CD

Example GitHub Actions workflow:
```yaml
- name: Run Load Tests
  run: |
    cd load-test
    mvn gatling:test -DbaselineUsers=100 -DrampUp=60 -Dhold=180

- name: Upload Reports
  uses: actions/upload-artifact@v2
  with:
    name: gatling-reports
    path: load-test/target/gatling/
```

## Next Steps

1. **Baseline Testing**
   - Run baseline tests to establish performance benchmarks
   - Document results for future comparison

2. **Gradual Scale-Up**
   - Start with 100 users
   - Increase to 500, then 1000
   - Monitor system behavior at each level

3. **Identify Bottlenecks**
   - Run stress tests to find limits
   - Profile application during peak load
   - Optimize based on findings

4. **Regular Testing**
   - Include load tests in CI/CD pipeline
   - Test before major releases
   - Track performance trends over time

5. **Custom Scenarios**
   - Create scenario variants for specific use cases
   - Test edge cases (rapid reconnections, error conditions)
   - Simulate production traffic patterns

## Files and Documentation

- **README.md** - Comprehensive documentation (detailed)
- **QUICK_START.md** - Get started in 5 minutes (beginner-friendly)
- **IMPLEMENTATION_SUMMARY.md** - This file (overview)

## Build Status

✅ Module compiles successfully
✅ All configuration files in place
✅ Execution scripts ready
✅ Documentation complete

Ready to run load tests!
