#!/usr/bin/env bash
# Fetches all @Timed metrics from the transaction service actuator and writes
# them to metrics-report.json in the same directory as this script.

BASE_URL="${1:-http://localhost:8080}"
OUTPUT="$(dirname "$0")/metrics-report.json"

METRICS=(
  "payment.load_and_validate_source"
  "payment.execute_intra_bank"
  "payment.mark_cb_acknowledged"
  "payment.prepare_inter_bank"
  "payment.rehold_for_new_cycle"
  "payment.complete_inter_bank"
  "payment.refund_inter_bank"
  "payment.get_payment"
  "account.create_account"
  "account.get_account"
  "account.get_balance"
  "account.admin_credit"
  "ledger.hold_funds"
  "ledger.post_hold"
  "ledger.void_hold"
  "ledger.credit_account"
  "batch.create_batch"
  "batch.get_batch_status"
  "batch.get_batch_payments"
  "credit.find_and_validate_beneficiary"
  "credit.record_received"
  "credit.mark_accepted"
  "credit.apply_credit"
  "credit.mark_failed"
  "reconciliation.reconcile"
)

echo "Fetching metrics from $BASE_URL ..."

results="["
first=true

for metric in "${METRICS[@]}"; do
  response=$(curl -sf "$BASE_URL/actuator/metrics/$metric")
  status=$?

  if [ $status -ne 0 ]; then
    entry="{\"metric\":\"$metric\",\"error\":\"not available (no data yet or service down)\"}"
  else
    count=$(echo "$response"    | grep -o '"value":[0-9.]*' | awk -F: 'NR==1{print $2}')
    total=$(echo "$response"    | grep -o '"value":[0-9.]*' | awk -F: 'NR==2{print $2}')
    max=$(echo "$response"      | grep -o '"value":[0-9.]*' | awk -F: 'NR==3{print $2}')
    mean=""
    if [[ -n "$count" && -n "$total" && "$count" != "0" ]]; then
      mean=$(awk "BEGIN{printf \"%.6f\", $total / $count}")
    fi
    entry="{\"metric\":\"$metric\",\"count\":${count:-0},\"total_seconds\":${total:-0},\"max_seconds\":${max:-0},\"mean_seconds\":\"${mean:-n/a}\"}"
  fi

  if $first; then
    first=false
  else
    results="$results,"
  fi
  results="$results$entry"
  echo "  $metric — count=${count:-?} mean=${mean:-?}s max=${max:-?}s"
done

results="$results]"

echo "$results" | python3 -m json.tool > "$OUTPUT" 2>/dev/null || echo "$results" > "$OUTPUT"

echo ""
echo "Saved to $OUTPUT"
