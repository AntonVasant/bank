#!/usr/bin/env python3
"""
Load test: Bank B  →  Bank A  (inter-bank payments)

Sends TOTAL_REQUESTS concurrent payment requests from Bank B's HTTP API
to transfer money into a Bank A account.  Each request gets a unique
idempotency key so retries are safe.

Usage:
    python bankb_to_banka.py

Output:
    results/bankb_to_banka/responses.json  – every individual response
    results/bankb_to_banka/summary.json    – aggregated stats
"""

import sys

from common import print_summary, run_load_test, write_results
from config import (
    AMOUNT_PER_TX,
    BANKA_CODE,
    BANKA_DEST_ACCOUNT_NUMBER,
    BANKB_BASE_URL,
    BANKB_SOURCE_ACCOUNT_ID,
    BANKB_SOURCE_ACCOUNT_NUMBER,
    CONCURRENCY,
    TOTAL_REQUESTS,
)

LABEL = "bankb_to_banka"


def main() -> int:
    results = run_load_test(
        label=LABEL,
        base_url=BANKB_BASE_URL,
        total=TOTAL_REQUESTS,
        concurrency=CONCURRENCY,
        source_account_id=BANKB_SOURCE_ACCOUNT_ID,
        source_account_number=BANKB_SOURCE_ACCOUNT_NUMBER,
        dest_bank_code=BANKA_CODE,
        dest_account_number=BANKA_DEST_ACCOUNT_NUMBER,
        amount=AMOUNT_PER_TX,
    )

    write_results(results, LABEL)

    from common import _build_summary
    summary = _build_summary(results, LABEL)
    print_summary(summary)

    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
