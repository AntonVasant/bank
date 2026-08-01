// ──────────────────────────────────────────────────────────────────────────────
// Stage 3 — Verify correctness.
//
// Waits for the asynchronous interbank settlement triggered by 02-load-test.js
// to finish (polls both test accounts' balances until they stop moving), then
// asserts the final balances match what they mathematically should be, given
// the initial balances (01-setup.js) and the exact amounts that actually
// succeeded (02-load-test.js) — not what was merely attempted.
//
// Run standalone (after 01-setup.js and 02-load-test.js):
//   k6 run 03-verify.js
// ──────────────────────────────────────────────────────────────────────────────

import { check, sleep } from 'k6';
import {
    VERIFY_POLL_INTERVAL_SECONDS,
    VERIFY_MAX_WAIT_SECONDS,
    VERIFY_STABLE_POLLS_REQUIRED,
    STATE_FILE,
} from './config.js';
import { getBalance } from './lib.js';

const state = JSON.parse(open(STATE_FILE));

export const options = {
    scenarios: {
        verify: { executor: 'shared-iterations', vus: 1, iterations: 1 },
    },
    thresholds: {
        // Any failed check (a real correctness mismatch) fails the whole run.
        checks: ['rate==1'],
    },
};

// All the real work happens in setup() (runs once, in the shared main
// context, before any VU iterations) — see 01-setup.js for why.
export function setup() {
    if (!state.load) {
        throw new Error('results/account_state.json has no "load" section — run 02-load-test.js first');
    }

    console.log(`\n=== Verifying settlement for run ${state.runId} ===`);

    const expectedBankA = round4(
        state.bankA.initialBalance - state.load.amountAToBSucceeded + state.load.amountBToASucceeded
    );
    const expectedBankB = round4(
        state.bankB.initialBalance - state.load.amountBToASucceeded + state.load.amountAToBSucceeded
    );

    console.log(`  Bank A expected final balance: ${expectedBankA} (initial ${state.bankA.initialBalance}, `
        + `-${state.load.amountAToBSucceeded} sent, +${state.load.amountBToASucceeded} received)`);
    console.log(`  Bank B expected final balance: ${expectedBankB} (initial ${state.bankB.initialBalance}, `
        + `-${state.load.amountBToASucceeded} sent, +${state.load.amountAToBSucceeded} received)`);

    const settled = waitForSettlement(expectedBankA, expectedBankB);

    const finalA = getBalance(state.bankA.baseUrl, state.bankA.accountId);
    const finalB = getBalance(state.bankB.baseUrl, state.bankB.accountId);
    const actualBankA = Number(finalA.balance);
    const actualBankB = Number(finalB.balance);

    const matchedA = check(actualBankA, {
        'Bank A final balance matches expected net position': (v) => almostEqual(v, expectedBankA),
    });
    const matchedB = check(actualBankB, {
        'Bank B final balance matches expected net position': (v) => almostEqual(v, expectedBankB),
    });
    check(settled, { 'balances stabilized before the timeout': (v) => v === true });

    const report = {
        runId: state.runId,
        settledBeforeTimeout: settled,
        bankA: { expected: expectedBankA, actual: actualBankA, matched: matchedA },
        bankB: { expected: expectedBankB, actual: actualBankB, matched: matchedB },
        load: state.load,
        checkedAt: new Date().toISOString(),
    };

    if (matchedA && matchedB && settled) {
        console.log('\n  \u2705 CORRECTNESS CHECK PASSED \u2014 final balances match the expected net position.\n');
    } else {
        console.error('\n  \u274c CORRECTNESS CHECK FAILED \u2014 see results/verification-report.json for details.\n');
    }

    return report;
}

export default function () {
    // All the work happens in setup(); nothing per-iteration.
}

// `data.setup_data` is the exact object returned by setup() — unlike module-
// level variables assigned in teardown(), this reliably survives into
// handleSummary() (see 01-setup.js for why).
export function handleSummary(data) {
    if (!data.setup_data) {
        console.error('setup() did not complete successfully; not writing verification-report.json');
        return {};
    }
    return {
        './results/verification-report.json': JSON.stringify(data.setup_data, null, 2),
    };
}

/** Polls both balances until they stop changing for a few consecutive polls, they match expected, or the timeout is reached. */
function waitForSettlement(expectedBankA, expectedBankB) {
    const deadline = Date.now() + VERIFY_MAX_WAIT_SECONDS * 1000;
    let lastA = null;
    let lastB = null;
    let stableCount = 0;

    while (Date.now() < deadline) {
        const balA = Number(getBalance(state.bankA.baseUrl, state.bankA.accountId).balance);
        const balB = Number(getBalance(state.bankB.baseUrl, state.bankB.accountId).balance);

        if (almostEqual(balA, expectedBankA) && almostEqual(balB, expectedBankB)) {
            return true;
        }

        if (lastA !== null && almostEqual(balA, lastA) && almostEqual(balB, lastB)) {
            stableCount++;
        } else {
            stableCount = 0;
        }
        lastA = balA;
        lastB = balB;

        if (stableCount >= VERIFY_STABLE_POLLS_REQUIRED) {
            // Balances have stopped moving but don't match expected — settled
            // in a wrong state; stop polling early, let the caller report it.
            return false;
        }

        sleep(VERIFY_POLL_INTERVAL_SECONDS);
    }

    return false;
}

function almostEqual(a, b) {
    return Math.abs(a - b) < 0.0001;
}

function round4(n) {
    return Math.round(n * 10000) / 10000;
}
