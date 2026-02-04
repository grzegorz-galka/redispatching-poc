# TSO Redispatching Load Tests

Gatling-based load tests for the TSO Redispatching Order Service. Tests measure performance and capacity for SSE connections and REST API endpoints.

## Overview

### Test Scenarios

1. **SSE Baseline** - Connection capacity test
   - Opens 1000 SSE connections
   - Keeps connections alive for 10 minutes
   - Measures connection success rate and stability

2. **Full Workflow** - Realistic user behavior
   - Opens 1000 SSE connections
   - Waits for ORDER_ISSUED events
   - Fetches order details via GET
   - Sends acknowledgements via POST
   - Measures end-to-end latency and throughput

3. **Stress Test** - System limits
   - Gradually increases load to 1500 users
   - Multi-phase ramp-up (500 → 1000 → 1500)
   - Identifies breaking points and bottlenecks

## Prerequisites

- Java 25+
- Maven 3.9+
- Running service and gateway instances
- Adequate system resources (RAM, CPU, network)

## Quick Start

### 1. Build the project

```bash
cd load-test
mvn clean install
```

### 2. Run a test scenario

**SSE Baseline Test:**
```bash
./scripts/run-baseline.sh
```

**Full Workflow Test:**
```bash
./scripts/run-workflow.sh
```

**Stress Test:**
```bash
./scripts/run-stress.sh
```

**All Tests:**
```bash
./scripts/run-all.sh
```

### 3. View results

After test completion, open the HTML report:
```
target/gatling/[simulation-name]-[timestamp]/index.html
```

## Configuration

### System Properties

Override test parameters via system properties:

```bash
mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.FullWorkflowScenario \
  -DbaseUrl=http://localhost:8080 \
  -DworkflowUsers=2000 \
  -DrampUp=600 \
  -Dhold=1800
```

### Available Properties

| Property | Description | Default |
|----------|-------------|---------|
| `baseUrl` | Base URL of the gateway | `http://localhost:8080` |
| `baselineUsers` | Users for baseline test | `1000` |
| `workflowUsers` | Users for workflow test | `1000` |
| `stressMaxUsers` | Max users for stress test | `1500` |
| `rampUp` | Ramp-up duration (seconds) | `300` |
| `hold` | Hold duration (seconds) | `900` |
| `baselineHold` | Baseline hold duration (seconds) | `600` |
| `maxEntityId` | Max entity ID number | `10` |

## Test Scenarios Details

### 1. SSE Baseline Scenario

**Purpose:** Measure SSE connection capacity

**Load Profile:**
- Ramp up to 1000 users over 5 minutes
- Hold 1000 connections for 10 minutes

**Actions:**
1. Open SSE connection
2. Receive 'connected' event
3. Keep alive (receive heartbeats)
4. Close connection

**Success Criteria:**
- ≥95% connection success rate

**Execution:**
```bash
./scripts/run-baseline.sh http://localhost:8080 1000 300 600
```

### 2. Full Workflow Scenario

**Purpose:** Test realistic end-to-end workflow

**Load Profile:**
- Ramp up to 1000 users over 5 minutes
- Maintain constant rate of 20 new users/sec for 15 minutes

**Actions:**
1. Open SSE connection
2. Receive 'connected' event
3. Wait for 'ORDER_ISSUED' event
4. GET order details (using resourceUrl from event)
5. POST acknowledgement
6. Repeat 3 times
7. Close connection

**Success Criteria:**
- ≥95% request success rate
- p95 response time <2000ms
- p99 response time <5000ms
- GET order p95 <1000ms
- POST ack p95 <500ms

**Execution:**
```bash
./scripts/run-workflow.sh http://localhost:8080 1000 300 900
```

### 3. Stress Test Scenario

**Purpose:** Find system capacity limits

**Load Profile:**
- Phase 1: Ramp 0→500 (1 min), hold 500 (2 min)
- Phase 2: Ramp 500→1000 (1 min), hold 1000 (3 min)
- Phase 3: Ramp 1000→1500 (1 min), hold 1500 (5 min)

**Actions:**
- Same as Full Workflow but with 5 order iterations

**Success Criteria:**
- ≥90% success rate (relaxed for stress)
- p99 response time <5000ms

**Execution:**
```bash
./scripts/run-stress.sh http://localhost:8080 1500
```

## Running Tests via Maven

**Run specific scenario:**
```bash
mvn gatling:test -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.SseBaselineScenario
```

**Run with custom parameters:**
```bash
mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.FullWorkflowScenario \
  -DbaseUrl=http://staging-server:8080 \
  -DworkflowUsers=2000 \
  -DrampUp=600
```

**Run all simulations:**
```bash
mvn gatling:test
```

## Interpreting Results

### Key Metrics

**Response Time:**
- **p50 (median)**: Half of requests are faster
- **p95**: 95% of requests are faster
- **p99**: 99% of requests are faster

**Throughput:**
- Requests per second (req/s)

**Status Codes:**
- **2xx**: Success
- **4xx**: Client errors (check request format)
- **5xx**: Server errors (system overload)

### HTML Report Sections

1. **Global Statistics**: Overall test performance
2. **Request Statistics**: Per-request metrics
3. **Response Time Distribution**: Histogram
4. **Response Time Percentiles**: Over time
5. **Requests Per Second**: Throughput graph
6. **Responses Per Second**: By status code

### What to Look For

**Good Performance:**
- Low response times (<1s for GET, <500ms for POST)
- High success rate (>95%)
- Stable throughput
- No 5xx errors

**Performance Issues:**
- Increasing response times over test duration
- High p99 response times (>5s)
- 5xx errors (system overload)
- Connection failures

**System Bottlenecks:**
- CPU saturation: High response times, stable error rate
- Memory issues: Increasing errors, GC pauses
- Network saturation: Connection timeouts
- Thread pool exhaustion: 5xx errors, failed connections

## Monitoring During Tests

Monitor system metrics while tests run:

**Service/Gateway Metrics:**
```bash
# CPU usage
top -p $(pgrep -f RedispatchServiceApplication)

# Memory usage
jstat -gc $(pgrep -f RedispatchServiceApplication) 1000

# Network connections
ss -tan | grep :8080 | wc -l
```

**System Metrics:**
```bash
# Overall system load
htop

# Network I/O
iftop

# Disk I/O
iostat -x 1
```

## Troubleshooting

### Common Issues

**Connection Refused:**
- Verify service and gateway are running
- Check ports (8080 for gateway, 8081 for service)
- Verify firewall settings

**Out of Memory:**
- Increase JVM heap: `-Xmx4g -Xms4g`
- Reduce concurrent users
- Decrease test duration

**Too Many Open Files:**
```bash
# Increase file descriptor limit
ulimit -n 65536
```

**High Response Times:**
- Check service logs for errors
- Monitor CPU/memory usage
- Verify database/external service performance

## Best Practices

1. **Start Small**: Begin with 100 users, then scale up
2. **Warm Up**: Services perform better after JIT compilation
3. **Monitor Resources**: Watch CPU, memory, network during tests
4. **Test Incrementally**: Baseline → Workflow → Stress
5. **Compare Results**: Establish baselines, track changes over time
6. **Test in Isolation**: Avoid running multiple tests simultaneously
7. **Clean Environment**: Restart services between major test runs

## Example Test Run

```bash
# 1. Start services
cd service && mvn spring-boot:run &
cd gateway && mvn spring-boot:run &

# 2. Wait for startup
sleep 30

# 3. Run baseline test
cd load-test
./scripts/run-baseline.sh

# 4. Analyze results
open target/gatling/ssebaselinescenario-*/index.html

# 5. Run full workflow
./scripts/run-workflow.sh

# 6. Compare results
```

## Advanced Configuration

### Custom Gatling Configuration

Edit `src/test/resources/gatling.conf` to modify:
- Connection timeouts
- Pool sizes
- SSL settings
- Report generation

### Custom Entity IDs

Edit `src/test/resources/entity-ids.csv` or use `maxEntityId` property:
```bash
mvn gatling:test -DmaxEntityId=50
```

### Integration with CI/CD

```yaml
# Example GitHub Actions workflow
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

## Support

For issues or questions:
1. Check service/gateway logs
2. Review Gatling reports for errors
3. Verify system resources are adequate
4. Consult TSO Redispatching documentation
