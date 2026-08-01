# k6 correctness + performance test

Complements the existing Python load test (`../banka_to_bankb.py` /
`../bankb_to_banka.py`) with a [k6](https://k6.io) suite that does two things
in one run:

1. **Performance** — fires thousands of concurrent interbank payments in both
   directions (Bank A → Bank B and Bank B → Bank A) and reports request
   latency / throughput / error rate (k6's standard `http_req_duration`,
   `http_req_failed`, `checks` metrics).
2. **Correctness** — independently computes what each test account's final
   balance *should* be (initial balance ± the exact amounts that actually
   succeeded) and asserts the real balance (via the accounts API) matches it
   once settlement has finished.

## Prerequisites

- k6 installed: `brew install k6` (or see <https://grafana.com/docs/k6/latest/set-up/install-k6/>).
- Both bank instances, the rail, delivery, and their infra (Postgres, Redis,
  Kafka) running locally:
  - Bank A on `http://localhost:8080` (`bank.code=AXIS_IN`, per `application-bankA.yaml`)
  - Bank B on `http://localhost:8081` (`bank.code=HDFC_IN`, per `application-bankB.yaml`)
  - `rail` and `delivery` running (interbank payments won't settle otherwise).

If your ports/bank codes differ from the two `application-bankA.yaml` /
`application-bankB.yaml` files checked into this repo, override them via env
vars (see **Configuration** below) — no file needs editing.

## Running

```bash
cd transaction/load-test/k6
./run_all.sh
```

This runs the three stages in order and stops immediately if any of them
fails (`set -e`):

| Stage | Script | What it does |
|---|---|---|
| 1 | `01-setup.js` | Creates one fresh test user + account in each bank, credits both heavily via the internal admin endpoint, records their starting balances to `results/account_state.json`. |
| 2 | `02-load-test.js` | Fires `ITERATIONS_A_TO_B` / `ITERATIONS_B_TO_A` interbank payments (default 1000 each way), `VUS_PER_DIRECTION` concurrent VUs per direction (default 25). Records the *exact* amount that actually succeeded in each direction (not just what was attempted) into `results/account_state.json`. |
| 3 | `03-verify.js` | Polls both accounts' balances (interbank settlement is asynchronous) until they stop moving or a timeout is hit, computes the expected final balance from stage 1 + 2's numbers, and asserts they match. Writes `results/verification-report.json`. Exits non-zero if anything didn't match. |

Each stage can also be run standalone (e.g. to re-run just the load or just
re-verify) as long as the prior stage(s) already wrote their state:

```bash
k6 run 01-setup.js
k6 run 02-load-test.js
k6 run 03-verify.js
```

## Configuration

All tunables are environment variables (see `config.js` for the full list
and defaults) — pass them with `-e NAME=value` to `k6 run`, or export them
before calling `./run_all.sh`:

| Variable | Default | Meaning |
|---|---|---|
| `BANKA_BASE_URL` | `http://localhost:8080` | Bank A's base URL (`server.port` in `application-bankA.yaml`) |
| `BANKB_BASE_URL` | `http://localhost:8081` | Bank B's base URL (`server.port` in `application-bankB.yaml`) |
| `BANKA_CODE` | `AXIS_IN` | Bank A's `bank.code` |
| `BANKB_CODE` | `HDFC_IN` | Bank B's `bank.code` |
| `AMOUNT_PER_TX` | `10` | Amount per payment (fixed, so expected totals are exact) |
| `ITERATIONS_A_TO_B` | `1000` | Number of A→B payments to attempt |
| `ITERATIONS_B_TO_A` | `1000` | Number of B→A payments to attempt |
| `VUS_PER_DIRECTION` | `25` | Concurrent virtual users per direction |
| `INITIAL_FUNDING` | `3 × planned outflow + 100000` | How much to credit each test account before the load starts |
| `VERIFY_POLL_INTERVAL_SECONDS` | `3` | How often stage 3 re-checks balances |
| `VERIFY_MAX_WAIT_SECONDS` | `120` | Max time stage 3 waits for settlement to finish |
| `VERIFY_STABLE_POLLS_REQUIRED` | `3` | Consecutive unchanged polls before treating balances as "settled" |

Example — a much bigger run:

```bash
ITERATIONS_A_TO_B=10000 ITERATIONS_B_TO_A=10000 VUS_PER_DIRECTION=100 ./run_all.sh
```

## Why a separate test account per run instead of reusing one?

Each `run_all.sh` invocation creates brand-new accounts (`K6-A-<timestamp>` /
`K6-B-<timestamp>`) rather than reusing a fixed one, so runs never interfere
with each other's balances and there's nothing to reset between runs — safe
to run repeatedly, including in CI.

## Why not just check balances immediately after the load phase?

Interbank payments only return once the **hold** is placed (Moment 1) — the
receiver bank's confirmation and this bank's own final debit happen
asynchronously afterwards. Stage 3 polls until the balances stop moving (or
already match the expected result) before asserting, instead of racing that
async settlement.

## Notes

- Only interbank (Bank A ↔ Bank B) traffic is generated — that's the path
  that exercises the rail, the ledger, and the async settlement/reconciliation
  pipeline end-to-end. Intra-bank payments are a much simpler, purely-local
  code path and aren't the focus of this suite.
- If you'd rather point this at pre-existing seeded accounts instead of
  creating fresh ones, tell me their account numbers/IDs/currency for each
  bank and I can add a `--skip-setup` mode that reads them from `config.js`
  instead of stage 1 creating new ones.
