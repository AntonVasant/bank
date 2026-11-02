// ──────────────────────────────────────────────────────────────────────────────
// Shared configuration for the k6 correctness + performance test suite.
// Mirrors ../config.py (the existing Python load test) so both suites stay
// consistent with the two running bank instances' application-bankA.yaml /
// application-bankB.yaml.
// ──────────────────────────────────────────────────────────────────────────────

// ── Service URLs (server.port in application-bankA.yaml / application-bankB.yaml) ──
export const BANKA_BASE_URL = __ENV.BANKA_BASE_URL || 'http://localhost:8080';
export const BANKB_BASE_URL = __ENV.BANKB_BASE_URL || 'http://localhost:8081';

// ── Bank codes (bank.code in the respective yaml) ────────────────────────────
export const BANKA_CODE = __ENV.BANKA_CODE || 'AXIS_IN';
export const BANKB_CODE = __ENV.BANKB_CODE || 'HDFC_IN';

// ── Payment amount per transaction (fixed, so expected totals are exact) ─────
export const AMOUNT_PER_TX = Number(__ENV.AMOUNT_PER_TX || '10');

// ── Sustained-rate load — no sleeps; k6 fires requests at a fixed rate
// regardless of response latency (open-model load, like a real TPS target). ─
// Default (500) is the confirmed clean sustainable ceiling for this stack —
// 0 failures, 0 dropped iterations. Above ~600-700 TPS it starts queueing;
// above ~800 TPS it hard-fails (see PERFORMANCE.md).
// e.g. k6 run -e TARGET_TPS=2000 -e LOAD_TEST_DURATION_SECONDS=30 02-load-test.js
export const TARGET_TPS = Number(__ENV.TARGET_TPS || '700');
export const LOAD_TEST_DURATION_SECONDS = Number(__ENV.LOAD_TEST_DURATION_SECONDS || '10');
// Split evenly across the two directions.
export const RATE_PER_DIRECTION = TARGET_TPS / 2;

// VU pool k6 is allowed to draw from to sustain RATE_PER_DIRECTION even while
// requests are in flight — must be >= rate × expected request latency (in
// seconds) per direction, with headroom. maxVUs is a ceiling; k6 only spins
// up as many as it actually needs (up to it) to hit the target rate.
export const PRE_ALLOCATED_VUS_PER_DIRECTION = Number(__ENV.PRE_ALLOCATED_VUS_PER_DIRECTION || '200');
export const MAX_VUS_PER_DIRECTION = Number(__ENV.MAX_VUS_PER_DIRECTION || '2000');

// ── Initial funding — must comfortably cover everything this account will
// ever send, with headroom for both directions being in flight concurrently.
// ── Account pairs — traffic is spread round-robin across many independent
// account pairs (not one shared account) so throughput isn't artificially
// capped by single-row lock contention on one account's balance in Postgres.
// e.g. k6 run -e ACCOUNT_PAIRS=100 01-setup.js
export const ACCOUNT_PAIRS = Number(__ENV.ACCOUNT_PAIRS || '50');

// ── Initial funding per account — must comfortably cover everything that
// account will ever send, with headroom for both directions being in flight
// concurrently. (heldBalance locks funds the instant a hold is placed, before
// settlement.) Requests are split evenly across ACCOUNT_PAIRS, so each
// account only needs to cover its own (roughly even) share of the traffic.
const EXPECTED_REQUESTS_PER_DIRECTION = Math.ceil(RATE_PER_DIRECTION * LOAD_TEST_DURATION_SECONDS);
const PLANNED_OUTFLOW_PER_ACCOUNT = Math.ceil(EXPECTED_REQUESTS_PER_DIRECTION / ACCOUNT_PAIRS) * AMOUNT_PER_TX;
export const INITIAL_FUNDING = Number(__ENV.INITIAL_FUNDING || String(PLANNED_OUTFLOW_PER_ACCOUNT * 5 + 10000));

// ── Verification polling (03-verify.js) ──────────────────────────────────────
export const VERIFY_POLL_INTERVAL_SECONDS = Number(__ENV.VERIFY_POLL_INTERVAL_SECONDS || '3');
export const VERIFY_MAX_WAIT_SECONDS = Number(__ENV.VERIFY_MAX_WAIT_SECONDS || '120');
export const VERIFY_STABLE_POLLS_REQUIRED = Number(__ENV.VERIFY_STABLE_POLLS_REQUIRED || '3');

// ── Shared state file — written by 01-setup.js, read by 02/03 ────────────────
export const STATE_FILE = './results/account_state.json';
