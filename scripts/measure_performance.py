"""P13 performance measurement against a running local stack.

Measures only what it observes: HTTP latencies, reconciliation throughput,
and the automatic match rate reported by the run summary. Token/cost per
investigation is recorded as not measured because this configuration calls
no external LLM (the summary generator is scripted).
"""
from __future__ import annotations

import json
import statistics
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

INGEST = "http://localhost:8080"
RECON = "http://localhost:8080"
CASES = "http://localhost:8080"
REPORTS = "http://localhost:8080"
ARTIFACT = Path(__file__).resolve().parents[1] / "data" / "evaluation" / "p13_performance.json"
SAMPLES = 30
THROUGHPUT_PAYMENTS = 500


def call(url: str, method: str = "GET", body: bytes | None = None):
    request = urllib.request.Request(url, method=method, data=body)
    if body is not None:
        request.add_header("content-type", "application/json")
    start = time.perf_counter()
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = response.read().decode("utf-8")
        elapsed = (time.perf_counter() - start) * 1000
        try:
            return response.status, json.loads(payload), elapsed
        except json.JSONDecodeError:
            return response.status, payload, elapsed


def percentiles(samples: list[float]) -> dict:
    ordered = sorted(samples)
    index = max(0, min(len(ordered) - 1, int(round(0.95 * len(ordered))) - 1))
    return {
        "samples": len(ordered),
        "p50_ms": round(statistics.median(ordered), 2),
        "p95_ms": round(ordered[index], 2),
        "max_ms": round(ordered[-1], 2),
    }


def require_stack() -> None:
    """Fail before measuring if any service of the stack is not up.

    An artifact full of timeouts would look measured; it is not. Every
    endpoint is probed once and the first failure names the URL that is down.
    """
    for name, url in (("ingest", INGEST), ("recon", RECON),
                      ("cases", CASES), ("reports", REPORTS)):
        try:
            call(f"{url}/actuator/health")
        except OSError as error:
            raise SystemExit(f"stack unreachable at {url} ({name}) — "
                             f"start the compose stack first: {error}")


def measure_latencies() -> dict:
    samples = {"reconcile_post": [], "case_queue_get": [], "kpis_get": []}
    run_id = None
    for _ in range(SAMPLES):
        _, run, ms = call(f"{RECON}/api/reconcile?sourceSet=ALL", method="POST", body=b"")
        samples["reconcile_post"].append(ms)
        run_id = run["runId"]
        _, _, ms = call(f"{CASES}/api/cases")
        samples["case_queue_get"].append(ms)
        _, _, ms = call(f"{REPORTS}/api/reports/kpis")
        samples["kpis_get"].append(ms)
    if run_id:
        call(f"{CASES}/api/cases/sync", method="POST",
             body=json.dumps({"runId": run_id}).encode())
    return {name: percentiles(values) for name, values in samples.items()}


def measure_throughput() -> dict:
    stamp = uuid.uuid4().hex[:8]
    now = datetime.now(timezone.utc)
    # JSON bodies use the P2 REST contract's camelCase component names
    # (PaymentIngestRequest/LedgerIngestRequest/SettlementIngestRequest).
    # The snake_case spellings are CSV column headers only: sending them as
    # JSON keys binds null and every row is rejected.
    # All three statuses are "SUCCESS" on purpose: the engine's ordering
    # check compares the three status strings (P3 has no lifecycle mapping),
    # so a "SETTLED"/"POSTED" mix would make each triple STATUS_MISMATCH and
    # turn auto_match_rate into a measurement of the fixture, not the engine.
    payments = [{
        "externalTxnId": f"PERF-{stamp}-{i}",
        "customerId": f"C-{i}",
        "merchantId": f"M-{i}",
        "amount": "100.00",
        "currency": "INR",
        "status": "SUCCESS",
        "eventTime": (now - timedelta(hours=1)).isoformat(),
    } for i in range(THROUGHPUT_PAYMENTS)]
    ledger = [{
        "externalTxnId": f"PERF-{stamp}-{i}",
        "grossAmount": "100.00", "feeAmount": "0.00", "netAmount": "100.00",
        "currency": "INR", "postingStatus": "SUCCESS",
        "postedAt": (now - timedelta(minutes=30)).isoformat(),
    } for i in range(THROUGHPUT_PAYMENTS)]
    settlement = [{
        "externalTxnId": f"PERF-{stamp}-{i}",
        "settledAmount": "100.00", "feeAmount": "0.00", "currency": "INR",
        "settlementStatus": "SUCCESS", "settlementDate": now.date().isoformat(),
        "batchId": f"B-PERF-{stamp}-{i}",
    } for i in range(THROUGHPUT_PAYMENTS)]
    start = time.perf_counter()
    status, batch, _ = call(f"{INGEST}/api/ingest/payments", method="POST",
                            body=json.dumps(payments).encode())
    call(f"{INGEST}/api/ingest/ledger-entries", method="POST",
         body=json.dumps(ledger).encode())
    call(f"{INGEST}/api/ingest/settlements", method="POST",
         body=json.dumps(settlement).encode())
    ingest_seconds = time.perf_counter() - start
    start = time.perf_counter()
    _, run, _ = call(f"{RECON}/api/reconcile?sourceSet=ALL", method="POST", body=b"")
    reconcile_seconds = time.perf_counter() - start
    total = run["total"] or 1
    return {
        "ingest_http_status": status,
        "load_mix": "500 matched triples (payment + ledger + settlement per txn)",
        "ingested_accepted": batch.get("accepted"),
        "ingested_duplicates": batch.get("duplicates"),
        "reconciled_records": run["total"],
        "ingest_records_per_second": round(batch.get("accepted", 0) / ingest_seconds, 2),
        "reconcile_records_per_second": round(total / reconcile_seconds, 2),
        "auto_match_rate": round(run["matched"] / total, 4),
        "matched": run["matched"],
        "mismatched": run["mismatched"],
        "rule_version": run["ruleVersion"],
    }


def main() -> int:
    require_stack()
    artifact = {
        "measured_at": datetime.now(timezone.utc).isoformat(),
        "stack": {"ingest": INGEST, "recon": RECON, "cases": CASES, "reports": REPORTS},
        "latency_percentiles": measure_latencies(),
        "throughput": measure_throughput(),
        "not_measured": {
            "investigation_inference_latency_ms": "no external LLM call exists to time",
            "llm_tokens_and_cost": "the summary generator is scripted; no model call is made",
            "kafka_throughput_and_lag": "see data/evaluation/p13_kafka.json (Task 9)",
        },
    }
    ARTIFACT.parent.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(artifact["throughput"], indent=2))
    print(json.dumps({k: v["p95_ms"] for k, v in artifact["latency_percentiles"].items()},
                     indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())

