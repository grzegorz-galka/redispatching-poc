#!/bin/bash

##############################################################################
# Run SSE Baseline Load Test
#
# This test opens 1000 SSE connections and keeps them alive for 10 minutes.
# Tests connection capacity without API calls.
#
# Usage:
#   ./run-baseline.sh [baseUrl] [users] [rampUp] [hold]
#
# Examples:
#   ./run-baseline.sh
#   ./run-baseline.sh http://localhost:8080 500 180 300
##############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

BASE_URL="${1:-http://localhost:8080}"
USERS="${2:-1000}"
RAMP_UP="${3:-300}"
HOLD="${4:-600}"

echo "========================================"
echo "SSE Baseline Load Test"
echo "========================================"
echo "Base URL: $BASE_URL"
echo "Users: $USERS"
echo "Ramp-up: ${RAMP_UP}s"
echo "Hold: ${HOLD}s"
echo "========================================"
echo ""

cd "$PROJECT_DIR"

mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.SseBaselineScenario \
  -DbaseUrl="$BASE_URL" \
  -DbaselineUsers="$USERS" \
  -DrampUp="$RAMP_UP" \
  -DbaselineHold="$HOLD"

echo ""
echo "========================================"
echo "Test completed!"
echo "Report location: $PROJECT_DIR/target/gatling/"
echo "========================================"
