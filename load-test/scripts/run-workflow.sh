#!/bin/bash

##############################################################################
# Run Full Workflow Load Test
#
# This test simulates realistic user behavior:
# - Opens SSE connections
# - Waits for ORDER_ISSUED events
# - Fetches order details
# - Sends acknowledgements
#
# Usage:
#   ./run-workflow.sh [baseUrl] [users] [rampUp] [hold]
#
# Examples:
#   ./run-workflow.sh
#   ./run-workflow.sh http://localhost:8080 1000 300 900
##############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

BASE_URL="${1:-http://localhost:8080}"
USERS="${2:-1000}"
RAMP_UP="${3:-300}"
HOLD="${4:-900}"

echo "========================================"
echo "Full Workflow Load Test"
echo "========================================"
echo "Base URL: $BASE_URL"
echo "Users: $USERS"
echo "Ramp-up: ${RAMP_UP}s"
echo "Hold: ${HOLD}s"
echo "========================================"
echo ""

cd "$PROJECT_DIR"

mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.FullWorkflowScenario \
  -DbaseUrl="$BASE_URL" \
  -DworkflowUsers="$USERS" \
  -DrampUp="$RAMP_UP" \
  -Dhold="$HOLD"

echo ""
echo "========================================"
echo "Test completed!"
echo "Report location: $PROJECT_DIR/target/gatling/"
echo "========================================"
