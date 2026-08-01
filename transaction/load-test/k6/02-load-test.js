// ──────────────────────────────────────────────────────────────────────────────
// Stage 2 — Load test.
//
// Fires interbank payments Bank A → Bank B and Bank B → Bank A concurrently
// at a fixed, sustained rate (TARGET_TPS, split evenly per direction) for
// LOAD_TEST_DURATION_SECONDS — an open-model load (k6 `constant-arrival-rate`):
// requests are fired on schedule regardless of response latency, no sleeps,
// so this measures whether the system can actually sustain that TPS.
// Requires 01-setup.js to have already run (reads results/account_state.json).
//
// Only the accept-the-hold latency (Moment 1) is measured here — settlement
// finishes asynchronously afterwards; 03-verify.js waits for it and checks
// the final balances.
//
// Run standalone (after 01-setup.js):
//   k6 run 02-load-test.js
//   k6 run -e TARGET_TPS=4000 -e LOAD_TEST_DURATION_SECONDS=30 02-load-test.js
// ──────────────────────────────────────────────────────────────────────────────

import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
    AMOUNT_PER_TX,
    RATE_PER_DIRECTION,
    LOAD_TEST_DURATION_SECONDS,
    PRE_ALLOCATED_VUS_PER_DIRECTION,
    MAX_VUS_PER_DIRECTION,
    STATE_FILE,
} from './config.js';
import { sendPayment, buildInterBankPaymentPayload } from './lib.js';

// Read once, at init time, in every VU — must exist (produced by 01-setup.js).
const state = JSON.parse(open(STATE_FILE));
const pairs = state.pairs;

function randomPair() {
    return pairs[Math.floor(Math.random() * pairs.length)];
}

// Cumulative-sum Counters (Counter.add(value) sums, not just counts) — the
// exact total successfully-transferred amount per direction, read back in
// handleSummary() below to record ground truth for 03-verify.js.
const amountAToB = new Counter('amount_a_to_b_succeeded');
const amountBToA = new Counter('amount_b_to_a_succeeded');
const countAToB = new Counter('count_a_to_b_succeeded');
const countBToA = new Counter('count_b_to_a_succeeded');
const failuresAToB = new Counter('count_a_to_b_failed');
const failuresBToA = new Counter('count_b_to_a_failed');

export const options = {
    scenarios: {
        aToB: {
            executor: 'constant-arrival-rate',
            exec: 'sendAToB',
            rate: RATE_PER_DIRECTION,
            timeUnit: '1s',
            duration: `${LOAD_TEST_DURATION_SECONDS}s`,
            preAllocatedVUs: PRE_ALLOCATED_VUS_PER_DIRECTION,
            maxVUs: MAX_VUS_PER_DIRECTION,
        },
        bToA: {
            executor: 'constant-arrival-rate',
            exec: 'sendBToA',
            rate: RATE_PER_DIRECTION,
            timeUnit: '1s',
            duration: `${LOAD_TEST_DURATION_SECONDS}s`,
            preAllocatedVUs: PRE_ALLOCATED_VUS_PER_DIRECTION,
            maxVUs: MAX_VUS_PER_DIRECTION,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        // If k6 has to drop scheduled iterations because the VU pool
        // (maxVUs) couldn't keep up, that's the target rate not being met —
        // fail loudly instead of silently reporting a smaller effective TPS.
        dropped_iterations: ['count<=0'],
    },
};

export function sendAToB() {
    const pair = randomPair();
    const payload = buildInterBankPaymentPayload(
        `K6-AB-${state.runId}`,
        `${__VU}-${__ITER}`,
        pair.bankA.accountNumber,
        state.bankB.bankCode,
        pair.bankB.accountNumber,
        AMOUNT_PER_TX
    );
    const res = sendPayment(state.bankA.baseUrl, payload);
    const ok = check(res, { 'A→B payment accepted (201)': (r) => r.status === 201 });
    if (ok) {
        amountAToB.add(AMOUNT_PER_TX);
        countAToB.add(1);
    } else {
        failuresAToB.add(1);
        console.error(`A→B payment failed: HTTP ${res.status} ${res.body}`);
    }
}

export function sendBToA() {
    const pair = randomPair();
    const payload = buildInterBankPaymentPayload(
        `K6-BA-${state.runId}`,
        `${__VU}-${__ITER}`,
        pair.bankB.accountNumber,
        state.bankA.bankCode,
        pair.bankA.accountNumber,
        AMOUNT_PER_TX
    );
    const res = sendPayment(state.bankB.baseUrl, payload);
    const ok = check(res, { 'B→A payment accepted (201)': (r) => r.status === 201 });
    if (ok) {
        amountBToA.add(AMOUNT_PER_TX);
        countBToA.add(1);
    } else {
        failuresBToA.add(1);
        console.error(`B→A payment failed: HTTP ${res.status} ${res.body}`);
    }
}

export function handleSummary(data) {
    const metric = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);

    const totalSucceeded = metric('count_a_to_b_succeeded') + metric('count_b_to_a_succeeded');
    const droppedIterations = metric('dropped_iterations');
    const achievedTps = totalSucceeded / LOAD_TEST_DURATION_SECONDS;

    const load = {
        amountPerTx: AMOUNT_PER_TX,
        targetTps: RATE_PER_DIRECTION * 2,
        durationSeconds: LOAD_TEST_DURATION_SECONDS,
        achievedTps,
        droppedIterations,
        amountAToBSucceeded: metric('amount_a_to_b_succeeded'),
        amountBToASucceeded: metric('amount_b_to_a_succeeded'),
        countAToBSucceeded: metric('count_a_to_b_succeeded'),
        countBToASucceeded: metric('count_b_to_a_succeeded'),
        countAToBFailed: metric('count_a_to_b_failed'),
        countBToAFailed: metric('count_b_to_a_failed'),
        httpReqDurationP95: data.metrics.http_req_duration
            ? data.metrics.http_req_duration.values['p(95)']
            : null,
    };

    const updatedState = Object.assign({}, state, { load });

    console.log(`\n  A→B: ${load.countAToBSucceeded} succeeded / ${load.countAToBFailed} failed, total ${load.amountAToBSucceeded}`);
    console.log(`  B→A: ${load.countBToASucceeded} succeeded / ${load.countBToAFailed} failed, total ${load.amountBToASucceeded}`);
    console.log(`  Target TPS: ${load.targetTps} over ${load.durationSeconds}s — achieved ~${achievedTps.toFixed(1)} TPS`
        + (droppedIterations > 0 ? `, ⚠️  ${droppedIterations} iterations DROPPED (couldn't sustain target rate — raise MAX_VUS_PER_DIRECTION)` : ''));

    return {
        './results/account_state.json': JSON.stringify(updatedState, null, 2),
        './results/k6-load-summary.json': JSON.stringify(data, null, 2),
    };
}
