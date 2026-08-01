// ──────────────────────────────────────────────────────────────────────────────
// Stage 1 — Setup.
//
// Creates ACCOUNT_PAIRS independent fresh account pairs (one in Bank A + one
// in Bank B each), funds them all generously via the internal admin credit
// endpoint (no business validation, test-only), records their starting
// balances, and writes everything needed by the later stages to
// results/account_state.json.
//
// Using many independent pairs (instead of one shared account) means
// 02-load-test.js's traffic can spread across them — a single account pair
// hammered at high TPS would otherwise bottleneck on Postgres row-level
// locking for that one account's balance, capping throughput far below what
// the system can actually sustain in aggregate.
//
// Run standalone:
//   k6 run 01-setup.js
//   k6 run -e ACCOUNT_PAIRS=100 01-setup.js
// ──────────────────────────────────────────────────────────────────────────────

import {
    BANKA_BASE_URL,
    BANKB_BASE_URL,
    BANKA_CODE,
    BANKB_CODE,
    INITIAL_FUNDING,
    ACCOUNT_PAIRS,
} from './config.js';
import { createUser, createAccount, creditAccount, getBalance } from './lib.js';

export const options = {
    scenarios: {
        setupOnly: { executor: 'shared-iterations', vus: 1, iterations: 1 },
    },
};

export function setup() {
    const runId = Date.now();

    console.log(`\n=== Setting up ${ACCOUNT_PAIRS} account pair(s) (run ${runId}) ===`);

    const pairs = [];
    for (let i = 0; i < ACCOUNT_PAIRS; i++) {
        const bankAUser = createUser(BANKA_BASE_URL, `k6-bankA-${runId}-${i}@example.test`, `K6 Load Test (Bank A) ${runId}-${i}`);
        const bankAAccountNumber = `K6-A-${runId}-${i}`;
        const bankAAccount = createAccount(BANKA_BASE_URL, bankAUser.id, bankAAccountNumber);
        creditAccount(BANKA_BASE_URL, bankAAccount.id, INITIAL_FUNDING);
        const bankABalance = getBalance(BANKA_BASE_URL, bankAAccount.id);

        const bankBUser = createUser(BANKB_BASE_URL, `k6-bankB-${runId}-${i}@example.test`, `K6 Load Test (Bank B) ${runId}-${i}`);
        const bankBAccountNumber = `K6-B-${runId}-${i}`;
        const bankBAccount = createAccount(BANKB_BASE_URL, bankBUser.id, bankBAccountNumber);
        creditAccount(BANKB_BASE_URL, bankBAccount.id, INITIAL_FUNDING);
        const bankBBalance = getBalance(BANKB_BASE_URL, bankBAccount.id);

        pairs.push({
            bankA: {
                userId: bankAUser.id,
                accountId: bankAAccount.id,
                accountNumber: bankAAccountNumber,
                initialBalance: Number(bankABalance.balance),
                currency: bankABalance.currency,
            },
            bankB: {
                userId: bankBUser.id,
                accountId: bankBAccount.id,
                accountNumber: bankBAccountNumber,
                initialBalance: Number(bankBBalance.balance),
                currency: bankBBalance.currency,
            },
        });
    }

    console.log(`  Created and funded ${pairs.length} pair(s), each side funded to ${INITIAL_FUNDING}`);
    console.log('=== Setup complete ===\n');

    return {
        runId,
        bankA: { baseUrl: BANKA_BASE_URL, bankCode: BANKA_CODE },
        bankB: { baseUrl: BANKB_BASE_URL, bankCode: BANKB_CODE },
        pairs,
    };
}

export default function () {
    // All the work happens in setup(); nothing per-iteration.
}

export function teardown(data) {
    console.log(`\n  Wrote account state to results/account_state.json (run ${data.runId})`);
}

// `data.setup_data` is the exact object returned by setup() — unlike module-
// level variables assigned in teardown(), this reliably survives into
// handleSummary() since k6 passes it through explicitly.
export function handleSummary(data) {
    if (!data.setup_data) {
        console.error('setup() did not complete successfully; not writing account_state.json');
        return {};
    }
    return {
        './results/account_state.json': JSON.stringify(data.setup_data, null, 2),
    };
}
