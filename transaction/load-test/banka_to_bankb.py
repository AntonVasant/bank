#!/usr/bin/env python3
"""
Load test: Bank A  →  Bank B  (inter-bank payments)

Sends TOTAL_REQUESTS concurrent payment requests from Bank A's HTTP API
to transfer money into a Bank B account.  Each request gets a unique
idempotency key so retries are safe.

Usage:
    python banka_to_bankb.py

Output:
    results/banka_to_bankb/responses.json  – every individual response
    results/banka_to_bankb/summary.json    – aggregated stats
"""

import sys

from common import print_summary, run_load_test, write_results
from config import (
    AMOUNT_PER_TX,
    BANKA_BASE_URL,
    BANKA_SOURCE_ACCOUNT_ID,
    BANKA_SOURCE_ACCOUNT_NUMBER,
    BANKB_CODE,
    BANKB_DEST_ACCOUNT_NUMBER,
    CONCURRENCY,
    TOTAL_REQUESTS,
)

LABEL = "banka_to_bankb"


def main() -> int:
    results = run_load_test(
        label=LABEL,
        base_url=BANKA_BASE_URL,
        total=TOTAL_REQUESTS,
        concurrency=CONCURRENCY,
        source_account_id=BANKA_SOURCE_ACCOUNT_ID,
        source_account_number=BANKA_SOURCE_ACCOUNT_NUMBER,
        dest_bank_code=BANKB_CODE,
        dest_account_number=BANKB_DEST_ACCOUNT_NUMBER,
        amount=AMOUNT_PER_TX,
    )

    write_results(results, LABEL)

    from common import _build_summary
    summary = _build_summary(results, LABEL)
    print_summary(summary)

    # Exit 1 if any request failed
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
