# Quick Start Guide - Load Testing

This guide will help you run your first load test in 5 minutes.

## Prerequisites Check

```bash
# Check Java version (should be 25+)
java -version

# Check Maven version (should be 3.9+)
mvn -version

# Verify services are running
curl http://localhost:8080/v1/redispatch/ENT01/stream
```

If the curl command returns SSE events, you're ready to proceed!

## Step 1: Build the Load Test Module

```bash
cd load-test
mvn clean install
```

Expected output: `BUILD SUCCESS`

## Step 2: Run Your First Test (Small Scale)

Start with a small baseline test (100 users):

```bash
./scripts/run-baseline.sh http://localhost:8080 100 60 120
```

This runs:
- 100 concurrent SSE connections
- 60 second ramp-up
- 120 second hold time
- Total duration: ~3 minutes

## Step 3: View Results

After the test completes, you'll see:
```
Test completed!
Report location: /path/to/load-test/target/gatling/
```

Open the HTML report in your browser:
```bash
# Linux/WSL
xdg-open target/gatling/ssebaselinescenario-*/index.html

# macOS
open target/gatling/ssebaselinescenario-*/index.html

# Windows
start target/gatling/ssebaselinescenario-*/index.html
```

## Step 4: Understand the Report

Look at these key metrics:

1. **Global Statistics**
   - Total Requests: Should show ~100
   - OK: Should be 100%
   - Mean Response Time: Should be <1000ms

2. **Response Time Distribution**
   - Most bars should be in the green zone
   - Red bars indicate slow responses

3. **Active Users Over Time**
   - Should show smooth ramp-up to 100 users

## Step 5: Scale Up (Optional)

If the small test passed, try with more users:

### Medium Test (500 users)
```bash
./scripts/run-baseline.sh http://localhost:8080 500 180 300
```

### Full Test (1000 users)
```bash
./scripts/run-baseline.sh http://localhost:8080 1000 300 600
```

### Full Workflow Test
```bash
./scripts/run-workflow.sh
```

## Common Issues

### Issue: "Connection refused"
**Solution:** Ensure service and gateway are running
```bash
# Check if processes are running
ps aux | grep -E "(RedispatchService|RedispatchGateway)"

# Restart if needed
cd ../service && mvn spring-boot:run &
cd ../gateway && mvn spring-boot:run &
```

### Issue: "Too many open files"
**Solution:** Increase file descriptor limit
```bash
ulimit -n 65536
```

### Issue: High failure rate
**Solution:**
1. Check service logs for errors
2. Reduce number of users
3. Increase ramp-up time
4. Monitor system resources (CPU, memory)

## Next Steps

1. **Run Full Workflow Test**
   ```bash
   ./scripts/run-workflow.sh
   ```

2. **Run Stress Test** (when ready)
   ```bash
   ./scripts/run-stress.sh
   ```

3. **Customize Tests**
   - Edit `src/test/java/pl/tso/redispatch/loadtest/config/LoadTestConfig.java`
   - Modify scenario classes in `src/test/java/.../scenarios/`

4. **Read Full Documentation**
   - See [README.md](README.md) for detailed information

## Quick Reference

### Run Baseline Test
```bash
./scripts/run-baseline.sh [url] [users] [rampUp] [hold]
```

### Run Workflow Test
```bash
./scripts/run-workflow.sh [url] [users] [rampUp] [hold]
```

### Run Stress Test
```bash
./scripts/run-stress.sh [url] [maxUsers]
```

### Run via Maven
```bash
mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.SseBaselineScenario \
  -DbaseUrl=http://localhost:8080 \
  -DbaselineUsers=100
```

## Expected Results (for 1000 users)

**Good Performance:**
- Success Rate: >95%
- p95 Response Time: <2000ms
- No 5xx errors
- Stable throughput

**Warning Signs:**
- Success Rate: <90%
- p95 Response Time: >5000ms
- Increasing error rate over time
- Connection timeouts

## Need Help?

1. Check the [README.md](README.md) for detailed documentation
2. Review service/gateway logs
3. Monitor system resources during test
4. Start with fewer users and scale up gradually

Happy Load Testing! 🚀
