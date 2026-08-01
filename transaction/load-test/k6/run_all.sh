#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Runs the full k6 correctness + performance suite against both running bank
# instances: 01-setup.js -> 02-load-test.js -> 03-verify.js.
#
# Prerequisites: k6 installed (brew install k6), and bank A + bank B + rail +
# delivery + Postgres/Redis/Kafka all up (see ../../README.md / docker-compose.yml
# in each module).
#
# Usage:
#   ./run_all.sh
#   ITERATIONS_A_TO_B=5000 ITERATIONS_B_TO_A=5000 VUS_PER_DIRECTION=50 ./run_all.sh
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p results

# Forward any of these env vars straight through to k6 (see config.js for defaults).
K6_ENV_ARGS=()
for var in BANKA_BASE_URL BANKB_BASE_URL BANKA_CODE BANKB_CODE AMOUNT_PER_TX ACCOUNT_PAIRS \
           TARGET_TPS LOAD_TEST_DURATION_SECONDS PRE_ALLOCATED_VUS_PER_DIRECTION MAX_VUS_PER_DIRECTION \
           INITIAL_FUNDING VERIFY_POLL_INTERVAL_SECONDS VERIFY_MAX_WAIT_SECONDS VERIFY_STABLE_POLLS_REQUIRED; do
    if [ -n "${!var:-}" ]; then
        K6_ENV_ARGS+=(-e "${var}=${!var}")
    fi
done

echo "=================================================================="
echo " Stage 1/3 — Setup (create + fund test accounts)"
echo "=================================================================="
k6 run "${K6_ENV_ARGS[@]}" 01-setup.js

echo
echo "=================================================================="
echo " Stage 2/3 — Load test (fire the interbank payments)"
echo "=================================================================="
k6 run "${K6_ENV_ARGS[@]}" 02-load-test.js

echo
echo "=================================================================="
echo " Stage 3/3 — Verify (wait for settlement, check final balances)"
echo "=================================================================="
k6 run "${K6_ENV_ARGS[@]}" 03-verify.js

echo
echo "All stages passed. See results/ for the full JSON reports."
