// ──────────────────────────────────────────────────────────────────────────────
// Shared helpers for the k6 correctness + performance test suite.
// No external jslib dependencies — everything here works fully offline.
// ──────────────────────────────────────────────────────────────────────────────

import http from 'k6/http';
import { check } from 'k6';

/** Simple (non-cryptographic) UUID v4 — good enough for idempotency keys / unique references. */
export function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** POST /api/v1/users — creates a fresh test user. Returns the parsed UserResponse body. */
export function createUser(baseUrl, email, fullName) {
    const res = http.post(`${baseUrl}/api/v1/users`, JSON.stringify({ email, fullName }), {
        headers: JSON_HEADERS,
        tags: { name: 'createUser' },
    });
    check(res, { 'createUser: 201 Created': (r) => r.status === 201 });
    if (res.status !== 201) {
        throw new Error(`createUser failed (${res.status}): ${res.body}`);
    }
    return res.json();
}

/** POST /api/v1/accounts — creates a fresh account for {@code userId}. Returns the parsed AccountResponse body (includes {@code id}). */
export function createAccount(baseUrl, userId, accountNumber) {
    const res = http.post(`${baseUrl}/api/v1/accounts`, JSON.stringify({ userId, accountNumber }), {
        headers: JSON_HEADERS,
        tags: { name: 'createAccount' },
    });
    check(res, { 'createAccount: 201 Created': (r) => r.status === 201 });
    if (res.status !== 201) {
        throw new Error(`createAccount failed (${res.status}): ${res.body}`);
    }
    return res.json();
}

/** POST /internal/admin/accounts/{accountId}/credit — test-only direct top-up, no business validation. */
export function creditAccount(baseUrl, accountId, amount) {
    const res = http.post(
        `${baseUrl}/internal/admin/accounts/${accountId}/credit`,
        JSON.stringify({ amount }),
        { headers: JSON_HEADERS, tags: { name: 'adminCredit' } }
    );
    check(res, { 'adminCredit: 200 OK': (r) => r.status === 200 });
    if (res.status !== 200) {
        throw new Error(`creditAccount failed (${res.status}): ${res.body}`);
    }
    return res.json();
}

/** GET /api/v1/accounts/{accountId}/balance — returns the parsed BalanceResponse body ({accountNumber, balance, currency}). */
export function getBalance(baseUrl, accountId) {
    const res = http.get(`${baseUrl}/api/v1/accounts/${accountId}/balance`, {
        tags: { name: 'getBalance' },
    });
    check(res, { 'getBalance: 200 OK': (r) => r.status === 200 });
    if (res.status !== 200) {
        throw new Error(`getBalance failed (${res.status}): ${res.body}`);
    }
    return res.json();
}

/**
 * POST /api/v1/payments — fires one (intra- or inter-bank) payment.
 * Requires a unique Idempotency-Key per logical attempt (a fresh one is
 * generated here every call, i.e. every call is a brand-new payment attempt).
 */
export function sendPayment(baseUrl, payload) {
    return http.post(`${baseUrl}/api/v1/payments`, JSON.stringify(payload), {
        headers: { ...JSON_HEADERS, 'Idempotency-Key': uuidv4() },
        tags: { name: 'createPayment' },
    });
}

export function buildInterBankPaymentPayload(refPrefix, index, sourceAccountNumber, destinationBankCode, destinationAccountNumber, amount) {
    return {
        paymentReference: `${refPrefix}-${index}`,
        sourceAccountNumber: sourceAccountNumber,
        destinationBankCode: destinationBankCode,
        destinationAccountNumber: destinationAccountNumber,
        amount: amount,
    };
}
