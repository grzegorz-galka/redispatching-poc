#!/bin/bash

##############################################################################
# Run Stress Test
#
# This test gradually increases load to find system limits:
# - Phase 1: Ramp to 500 users
# - Phase 2: Hold 500 users
# - Phase 3: Ramp to 1000 users
# - Phase 4: Hold 1000 users
# - Phase 5: Ramp to 1500 users
# - Phase 6: Hold 1500 users
#
# Usage:
#   ./run-stress.sh [baseUrl] [maxUsers]
#
# Examples:
#   ./run-stress.sh
#   ./run-stress.sh http://localhost:8080 2000
##############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

BASE_URL="${1:-http://localhost:8080}"
MAX_USERS="${2:-1500}"

echo "========================================"
echo "Stress Test"
echo "========================================"
echo "Base URL: $BASE_URL"
echo "Max Users: $MAX_USERS"
echo "Duration: ~13 minutes"
echo "========================================"
echo ""
echo "WARNING: This test will push the system"
echo "beyond normal capacity. Monitor system"
echo "resources closely."
echo ""
read -p "Press Enter to continue..."

cd "$PROJECT_DIR"

mvn gatling:test \
  -Dgatling.simulationClass=pl.tso.redispatch.loadtest.scenarios.StressTestScenario \
  -DbaseUrl="$BASE_URL" \
  -DstressMaxUsers="$MAX_USERS"

echo ""
echo "========================================"
echo "Test completed!"
echo "Report location: $PROJECT_DIR/target/gatling/"
echo "========================================"
