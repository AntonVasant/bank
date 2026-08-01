#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run_all.sh — run both load-test directions and produce a combined report
# No pip install needed — uses Python stdlib only.
#
# Usage:
#   cd load-test
#   ./run_all.sh [--parallel]        # --parallel runs both directions at once
#
# Prerequisite: Python 3.9+  (brew install python)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PYTHON=$(command -v python3 || command -v python)
if [ -z "$PYTHON" ]; then
  echo "ERROR: python3 not found. Install with: brew install python" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PARALLEL=false
for arg in "$@"; do
  [[ "$arg" == "--parallel" ]] && PARALLEL=true
done

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        Transaction Service — Bi-directional Load Test        ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  Mode     : $([ "$PARALLEL" = true ] && echo "PARALLEL" || echo "SEQUENTIAL")"
echo "  Started  : $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

EXIT_A=0
EXIT_B=0

if [ "$PARALLEL" = true ]; then
  # Run both directions simultaneously
  "$PYTHON" banka_to_bankb.py &
  PID_A=$!
  "$PYTHON" bankb_to_banka.py &
  PID_B=$!

  wait "$PID_A" || EXIT_A=$?
  wait "$PID_B" || EXIT_B=$?
else
  # Sequential: BankA → BankB first, then BankB → BankA
  "$PYTHON" banka_to_bankb.py || EXIT_A=$?
  "$PYTHON" bankb_to_banka.py || EXIT_B=$?
fi

# ── Combined summary ──────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                     COMBINED RESULTS                        ║"
echo "╚══════════════════════════════════════════════════════════════╝"

\"$PYTHON\" - <<EOF
import json, pathlib, sys

def load_summary(path):
    p = pathlib.Path(path)
    if not p.exists():
        return None
    return json.loads(p.read_text())

a = load_summary("results/banka_to_bankb/summary.json")
b = load_summary("results/bankb_to_banka/summary.json")

for s in (a, b):
    if s is None:
        continue
    lat = s.get("latency_ms", {})
    print(f"\n  ► {s['label']}")
    print(f"    Requests   : {s['total_requests']}")
    print(f"    Successful : {s['successful']}  ({s['success_rate_pct']}%)")
    print(f"    Failed     : {s['failed']}")
    if lat:
        print(f"    Latency ms : mean={lat['mean']}  p95={lat['p95']}  max={lat['max']}")
    if "failure_breakdown" in s:
        print(f"    Errors     : {s['failure_breakdown']}")

total_req = (a["total_requests"] if a else 0) + (b["total_requests"] if b else 0)
total_ok  = (a["successful"]     if a else 0) + (b["successful"]     if b else 0)
total_err = (a["failed"]         if a else 0) + (b["failed"]         if b else 0)

print(f"\n  ─────────────────────────────────────────")
print(f"  TOTAL  requests={total_req}  ok={total_ok}  failed={total_err}")
print(f"  ─────────────────────────────────────────\n")
EOF

echo "  Finished : $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

# Exit non-zero if either direction had failures
OVERALL=$(( EXIT_A + EXIT_B ))
exit $OVERALL
