"""
Shared utilities for the load-test scripts.
Uses only Python standard-library modules — no pip install required.
"""

import json
import statistics
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib import request as urllib_request
from urllib.error import URLError
from urllib.request import Request

from config import REQUEST_TIMEOUT, RESULTS_DIR


# ── Request builder ───────────────────────────────────────────────────────────

def build_payment_payload(index: int,
                           source_account_id: int,
                           source_account_number: str,
                           dest_bank_code: str,
                           dest_account_number: str,
                           amount: str,
                           ref_prefix: str) -> dict:
    return {
        "paymentReference":        f"{ref_prefix}-{index:04d}",
        "sourceAccountId":         source_account_id,
        "sourceAccountNumber":     source_account_number,
        "destinationAccountNumber": dest_account_number,
        "destinationBankCode":     dest_bank_code,
        "amount":                  float(amount),
    }


# ── Single request ────────────────────────────────────────────────────────────

def send_payment(index: int, base_url: str, payload: dict) -> dict:
    idempotency_key = str(uuid.uuid4())
    url = f"{base_url}/api/v1/payments"
    started_at = time.monotonic()
    utc_now = datetime.now(timezone.utc).isoformat()

    body_bytes = json.dumps(payload).encode("utf-8")
    req = Request(
        url,
        data=body_bytes,
        headers={
            "Content-Type": "application/json",
            "Idempotency-Key": idempotency_key,
        },
        method="POST",
    )

    try:
        with urllib_request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            elapsed_ms = round((time.monotonic() - started_at) * 1000, 2)
            status_code = resp.status
            raw = resp.read().decode("utf-8")
            try:
                body = json.loads(raw)
            except ValueError:
                body = raw
            return {
                "index": index,
                "idempotency_key": idempotency_key,
                "payment_reference": payload["paymentReference"],
                "sent_at": utc_now,
                "status_code": status_code,
                "success": status_code == 201,
                "elapsed_ms": elapsed_ms,
                "response": body,
            }

    except URLError as exc:
        # urllib raises URLError for both HTTP errors (via HTTPError subclass) and timeouts
        elapsed_ms = round((time.monotonic() - started_at) * 1000, 2)
        # HTTPError carries an HTTP status code
        status_code = getattr(exc, "code", None)
        try:
            err_body = json.loads(exc.read().decode("utf-8")) if hasattr(exc, "read") else str(exc.reason)
        except Exception:
            err_body = str(exc)
        return {
            "index": index,
            "idempotency_key": idempotency_key,
            "payment_reference": payload["paymentReference"],
            "sent_at": utc_now,
            "status_code": status_code,
            "success": False,
            "elapsed_ms": elapsed_ms,
            "error": err_body,
        }

    except Exception as exc:
        elapsed_ms = round((time.monotonic() - started_at) * 1000, 2)
        return {
            "index": index,
            "idempotency_key": idempotency_key,
            "payment_reference": payload["paymentReference"],
            "sent_at": utc_now,
            "status_code": None,
            "success": False,
            "elapsed_ms": elapsed_ms,
            "error": str(exc),
        }


# ── Load-test runner ──────────────────────────────────────────────────────────

def run_load_test(label: str, base_url: str, total: int, concurrency: int,
                   source_account_id: int, source_account_number: str,
                   dest_bank_code: str, dest_account_number: str,
                   amount: str) -> list[dict]:
    ref_prefix = f"PAY-{label[:1].upper()}{label[-1].upper()}-{datetime.now(timezone.utc).strftime('%H%M%S')}"
    payloads = [
        build_payment_payload(i, source_account_id, source_account_number,
                              dest_bank_code, dest_account_number, amount, ref_prefix)
        for i in range(1, total + 1)
    ]

    print(f"\n{'=' * 60}")
    print(f"  {label}  ({total} requests, concurrency={concurrency})")
    print(f"  Target : {base_url}")
    print(f"  Run ID : {ref_prefix}")
    print(f"{'=' * 60}")

    results = []
    wall_start = time.monotonic()

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = {
            pool.submit(send_payment, idx, base_url, payload): idx
            for idx, payload in enumerate(payloads, start=1)
        }
        completed = 0
        for future in as_completed(futures):
            result = future.result()
            results.append(result)
            completed += 1
            status = "✓" if result["success"] else "✗"
            print(
                f"  [{completed:>3}/{total}] {status}  "
                f"ref={result['payment_reference']}  "
                f"{result['elapsed_ms']} ms  "
                f"HTTP {result.get('status_code', 'ERR')}"
            )

    wall_elapsed = round(time.monotonic() - wall_start, 2)
    results.sort(key=lambda r: r["index"])
    print(f"\n  Wall time: {wall_elapsed}s")
    return results


# ── Output writers ────────────────────────────────────────────────────────────

def write_results(results: list[dict], label: str) -> None:
    out_dir = Path(RESULTS_DIR) / label
    out_dir.mkdir(parents=True, exist_ok=True)

    # All individual responses
    responses_file = out_dir / "responses.json"
    with open(responses_file, "w") as f:
        json.dump(results, f, indent=2)

    # Summary
    summary = _build_summary(results, label)
    summary_file = out_dir / "summary.json"
    with open(summary_file, "w") as f:
        json.dump(summary, f, indent=2)

    print(f"\n  Results written to  {responses_file}")
    print(f"  Summary written to  {summary_file}")


def _build_summary(results: list[dict], label: str) -> dict:
    total     = len(results)
    successes = [r for r in results if r["success"]]
    failures  = [r for r in results if not r["success"]]
    latencies = [r["elapsed_ms"] for r in results]

    summary = {
        "label": label,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total_requests": total,
        "successful": len(successes),
        "failed": len(failures),
        "success_rate_pct": round(len(successes) / total * 100, 2) if total else 0,
        "latency_ms": {},
    }

    if latencies:
        sorted_lat = sorted(latencies)
        summary["latency_ms"] = {
            "min":    sorted_lat[0],
            "max":    sorted_lat[-1],
            "mean":   round(statistics.mean(latencies), 2),
            "median": round(statistics.median(latencies), 2),
            "p90":    round(sorted_lat[int(len(sorted_lat) * 0.90)], 2),
            "p95":    round(sorted_lat[int(len(sorted_lat) * 0.95)], 2),
            "p99":    round(sorted_lat[int(len(sorted_lat) * 0.99)], 2),
        }

    if failures:
        summary["failure_breakdown"] = {}
        for r in failures:
            key = r.get("error") or f"HTTP_{r.get('status_code')}"
            summary["failure_breakdown"][key] = summary["failure_breakdown"].get(key, 0) + 1

    return summary


# ── Console printer ───────────────────────────────────────────────────────────

def print_summary(summary: dict) -> None:
    lat = summary.get("latency_ms", {})
    print(f"\n{'─' * 60}")
    print(f"  SUMMARY — {summary['label']}")
    print(f"{'─' * 60}")
    print(f"  Total      : {summary['total_requests']}")
    print(f"  Successful : {summary['successful']}")
    print(f"  Failed     : {summary['failed']}")
    print(f"  Success %  : {summary['success_rate_pct']}%")
    if lat:
        print(f"  Latency (ms)")
        print(f"    min    : {lat['min']}")
        print(f"    mean   : {lat['mean']}")
        print(f"    median : {lat['median']}")
        print(f"    p90    : {lat['p90']}")
        print(f"    p95    : {lat['p95']}")
        print(f"    p99    : {lat['p99']}")
        print(f"    max    : {lat['max']}")
    if "failure_breakdown" in summary:
        print(f"  Failures   : {summary['failure_breakdown']}")
    print(f"{'─' * 60}\n")
