Design for the production-grade Spring Batch Settlement Job, where each step
is completely independent and communicates only through persisted business
state in the database. The goal is maximum correctness and restartability
for a financial settlement system.

Requirements:

1. Spring Batch is only responsible for orchestration.
    - Job execution
    - Step sequencing
    - Restartability
    - Retry
    - Execution metadata

2. Business data must NEVER be stored in Spring Batch ExecutionContext.
   Spring Batch metadata should only track job/step execution status.

3. Each step must persist its result into application tables so that the
   next step can execute independently, even after a crash.

------------------------------------------------------------
High-level flow
------------------------------------------------------------

```
SettlementCycleScheduler
        │
        ▼
Close current cycle (PROCESSING), open new one (OPEN)
        │
        ▼
Launch settlementJob (cycleId = closed cycle's id)
```

The batch job processes only that one closed cycle.

```
settlementJob
│
├── Step 1: calculateSettlementStep    — Calculate Net Settlement
│
├── Step 2: postPaymentsToLedgerStep   — Post Gross Payments To Ledger
│
├── Step 3: applySettlementStep        — Apply Settlement (+ Post Net Settlement To Ledger)
│
├── Step 4: createOutboxStep           — Publish Settlement Events (outbox rows only)
│
└── settlementCycleCompletionListener  — Finish Cycle (afterJob)
```

Every step is a thin `Tasklet` that delegates all business logic to a
`@Service` (`SettlementService` / `SettlementLedgerService`). Nothing about
cycle id, amounts, or status is ever stored in a Spring Batch
`ExecutionContext` — only in the `settlements`, `payments`,
`bank_reserves`, `central_reserve_account`, `ledger_transaction`/
`ledger_entry`, `settlement_cycles` and `outbox_events` tables.

------------------------------------------------------------
Step 1 — Calculate Settlement
------------------------------------------------------------

Tasklet: `CalculateSettlementTasklet`
Delegates to: `SettlementService.calculateSettlement(cycleId)`

Input: `Payment` rows (the real-time record of every accepted transfer,
tagged with `cycle_id`) — **not** a separate ledger-entries table. Each
`Payment` row already carries both sides of one transfer (`fromBank`
debits it, `toBank` credits it), so no separate DEBIT/CREDIT rows are
needed to compute a net position.

```
WHERE payments.cycle_id = :cycleId
GROUP BY bank, currency
```

Aggregate per bank/currency:

```
totalDebit  = SUM(amount) WHERE fromBank = bank
totalCredit = SUM(amount) WHERE toBank   = bank
netAmount   = totalCredit - totalDebit
```

Persist one `Settlement` row per bank/currency, `status = PENDING`:

```
settlements
-----------
id
cycle_id
bank_id
bank_code
currency
total_credit
total_debit
net_amount
transaction_count
checksum
status               (PENDING, APPLIED)
outbox_created       (false)
reconciliation_status
```

Nothing has been applied yet — this step is purely a calculation.
Restart-safe: if `Settlement` rows already exist for this `cycleId`, the
step is a no-op (never recalculates).

------------------------------------------------------------
Step 2 — Post Payments To Ledger (gross audit trail)
------------------------------------------------------------

Tasklet: `PostPaymentsToLedgerTasklet`
Delegates to: `SettlementLedgerService.postCycleToLedger(cycleId)`

Reads every `PENDING` `Payment` for this cycle and posts it to the
Postgres ledger (`LedgerTransaction` + 2 × `LedgerEntry`, replacing what
TigerBeetle used to do) as one gross, individual transaction each:

```
Debit  fromBank's BankReserve
Credit toBank's   BankReserve
```

This is a **gross, per-payment audit trail** — informational, restart-safe
(already-`POSTED` payments are skipped). It does **not** move any real
balance (see Step 3) — `BankReserve.balance` only ever changes based on the
*net* amount, never per-payment, to avoid double-moving the same money once
per transfer and again per cycle.

------------------------------------------------------------
Step 3 — Apply Settlement
------------------------------------------------------------

Tasklet: `ApplySettlementTasklet`
Delegates to: `SettlementService.applySettlement()`

Reads `Settlement` rows where `status = PENDING` (never recalculates —
relies entirely on Step 1's output). For each row:

1. **Update the bank's reserve account** (the real, authoritative balance
   movement for this cycle):
   ```
   BankReserve.balance          += netAmount
   BankReserve.reservedBalance  -= totalDebit
   ```
2. **Post the net movement to the ledger** — a *second*, separate ledger
   transaction (in addition to Step 2's gross per-payment trail) that is
   the one actually tied 1:1 to the real balance movement above, against a
   single central clearing counterparty, `CentralReserveAccount` (a
   singleton, fixed-id row — the rail's own reserve/suspense account):
   ```
   netAmount < 0 (bank owes):  Debit  BankReserve          / Credit CentralReserveAccount
   netAmount > 0 (bank owed):  Debit  CentralReserveAccount / Credit BankReserve
   ```
   `CentralReserveAccount.balance` moves by `-netAmount` each time. Since
   every cycle's net amounts across banks always sum to zero (one bank's
   debit is always another's credit), its balance is a useful invariant —
   it returns to its prior value once every `Settlement` row for a cycle
   has been applied. Skipped when `netAmount == 0` (nothing moved).
   Keyed by `ledger_transaction.payment_id = Settlement.id` (there's no
   single `Payment` behind a net position, so the generic `payment_id`
   column references the `Settlement` row instead — same convention used
   for `IncomingCredit.id` on the receiver-bank side in the `transaction`
   module).
3. Mark `Settlement.status = APPLIED`, set `appliedAt`.

If this step crashes, Spring Batch restarts here — it re-reads
`Settlement` rows still `PENDING` rather than recalculating anything, and
per-row commits (`REQUIRES_NEW`) mean a failure on one bank/currency never
blocks or rolls back the others already applied in the same run.

------------------------------------------------------------
Step 4 — Create Outbox (Publish Settlement Events)
------------------------------------------------------------

Tasklet: `CreateOutboxTasklet`
Delegates to: `SettlementService.createOutbox()`

Reads `Settlement` rows where `status = APPLIED AND outbox_created = false`
and inserts one `OutboxEvent` row per bank/currency, e.g.:

```json
{
  "cycleId": 101,
  "bankId": 7,
  "bankCode": "AXIS_IN",
  "currency": "INR",
  "netAmount": "-500.000000"
}
```

Then marks `Settlement.outboxCreated = true`.

**Does not publish to Kafka.** Only inserts `OutboxEvent` rows — a
separate, independent `OutboxProcessor` polls and publishes them later,
so this step never needs a distributed transaction spanning the DB and
the broker.

------------------------------------------------------------
Finish Cycle (job listener, not a batch step)
------------------------------------------------------------

`SettlementCycleCompletionListener` (`JobExecutionListener`, registered on
`settlementJob`) reacts to the job's outcome:

```
BatchStatus.COMPLETED  → SettlementCycleService.completeCycle(cycleId)
                            SettlementCycle: PROCESSING → CLOSED
otherwise               → left PROCESSING, logged for manual retry
                          (the whole job re-runs from wherever its
                          Settlement/Payment rows say it left off)
```

Spring Batch itself never creates, rotates or closes cycles — this
listener is the single place that reacts to job success/failure by
closing the cycle. `SettlementCycleScheduler` (separate, `@Scheduled`)
is what opens the *next* cycle and launches the job — see
`SettlementCycleServiceImpl`.

------------------------------------------------------------
Key design principles
------------------------------------------------------------

- Spring Batch orchestrates the workflow only.
- Business state is always persisted in application tables, never in a
  Spring Batch `ExecutionContext`.
- Every step is independently restartable.
- Steps communicate only through database state (`Settlement.status`,
  `Payment.status`, `outboxCreated`, `SettlementCycle.status`).
- Business logic lives in `SettlementService`/`SettlementLedgerService`;
  tasklets remain thin wrappers that delegate to them.
- If any step fails, previously completed steps are not re-executed; the
  failed step resumes using the persisted business state in the database.
- Two independent ledger trails, both restart-safe and neither
  double-moving money: Step 2's gross per-payment audit trail (one entry
  pair per transfer) and Step 3's net settlement trail (one entry pair per
  bank/currency/cycle, the one actually tied to the real `BankReserve`
  balance movement).
