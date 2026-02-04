#!/bin/bash

##############################################################################
# Run All Load Tests Sequentially
#
# Executes all load test scenarios in order:
# 1. SSE Baseline (connection capacity)
# 2. Full Workflow (realistic load)
# 3. Stress Test (system limits)
#
# Usage:
#   ./run-all.sh [baseUrl]
#
# Example:
#   ./run-all.sh http://localhost:8080
##############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${1:-http://localhost:8080}"

echo "========================================"
echo "Running All Load Tests"
echo "========================================"
echo "Base URL: $BASE_URL"
echo ""
echo "Test sequence:"
echo "1. SSE Baseline (1000 users, 10 min)"
echo "2. Full Workflow (1000 users, 15 min)"
echo "3. Stress Test (up to 1500 users, 13 min)"
echo ""
echo "Total estimated time: ~40 minutes"
echo "========================================"
echo ""
read -p "Press Enter to continue..."

# Test 1: SSE Baseline
echo ""
echo "========================================"
echo "Test 1/3: SSE Baseline"
echo "========================================"
bash "$SCRIPT_DIR/run-baseline.sh" "$BASE_URL" 1000 300 600

# Wait between tests
echo ""
echo "Waiting 60 seconds before next test..."
sleep 60

# Test 2: Full Workflow
echo ""
echo "========================================"
echo "Test 2/3: Full Workflow"
echo "========================================"
bash "$SCRIPT_DIR/run-workflow.sh" "$BASE_URL" 1000 300 900

# Wait between tests
echo ""
echo "Waiting 60 seconds before next test..."
sleep 60

# Test 3: Stress Test
echo ""
echo "========================================"
echo "Test 3/3: Stress Test"
echo "========================================"
bash "$SCRIPT_DIR/run-stress.sh" "$BASE_URL" 1500

echo ""
echo "========================================"
echo "All tests completed!"
echo "========================================"
echo ""
echo "Reports are available in:"
echo "  $(dirname "$SCRIPT_DIR")/target/gatling/"
echo ""
