"""P13 reproducible demo: six scenarios across the full pipeline.

Every scenario prints one PASS or FAIL line and any mismatch exits 1.
Rows use unique DEMO-<stamp>-<slug> references, so reruns never collide.

Each scenario posts its OWN payment first: P2 resolves ledger and settlement
rows by external_txn_id and rejects a row whose payment is missing
("Unknown external_txn_id"), so a shared or pre-seeded payment would never
work and would also let the six scenarios contaminate each other. JSON bodies
use the P2 REST contract's camelCase component names; the snake_case
spellings are CSV column headers only.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

INGEST = "http://localhost:8080"
RECON = "http://localhost:8080"
CASES = "http://localhost:8080"
AI = "http://localhost:8000"
SCENARIOS = Path(__file__).resolve().parents[1] / "data" / "demo" / "scenarios.json"


def call(url: str, method: str = "GET", body: bytes | None = None):
    request = urllib.request.Request(url, method=method, data=body)
    if body is not None:
        request.add_header("content-type", "application/json")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def slug(name: str) -> str:
    return "".join(ch if ch.isalnum() else "-" for ch in name.lower()).strip("-")


def payment_row(scenario: dict, txn: str) -> dict:
    """The gateway row for this scenario's own transaction.

    The payment carries the event_time the engine needs for its
    settlement-window check, which is what makes the late-settlement scenario
    reproducible.
    """
    payment = scenario["payment"]
    event = datetime.now(timezone.utc) - timedelta(hours=payment["event_hours_ago"])
    return {"externalTxnId": txn, "customerId": "C-DEMO", "merchantId": "M-DEMO",
            "amount": payment["amount"], "currency": payment["currency"],
            "status": payment["status"], "eventTime": event.isoformat()}


def ledger_row(scenario: dict, txn: str) -> dict:
    ledger = scenario["ledger"]
    return {"externalTxnId": txn, "grossAmount": ledger["gross"],
            "feeAmount": ledger["fee"], "netAmount": ledger["net"],
            "currency": "INR", "postingStatus": ledger["status"],
            "postedAt": datetime.now(timezone.utc).isoformat()}


def settlement_row(scenario: dict, txn: str, batch_suffix: str = "") -> dict:
    """One settlement row; `batch_suffix` makes a second row a distinct batch.

    P2 stores only one row of an exact duplicate (same amount, same date,
    same batch_id), so the duplicate case has to differ the batch id to get
    two settlements in front of the engine.
    """
    settlement = scenario["settlement"]
    settled_on = datetime.now(timezone.utc).date() + timedelta(days=settlement["day_offset"])
    return {"externalTxnId": txn, "settledAmount": settlement["settled"],
            "feeAmount": settlement["fee"], "currency": "INR",
            "settlementStatus": settlement["status"],
            "settlementDate": settled_on.isoformat(),
            "batchId": f"B-{txn}{batch_suffix}"}


def run_scenario(scenario: dict, txn: str) -> tuple[dict, list[str], str]:
    failures: list[str] = []
    # Payment first: P2 resolves the ledger and settlement rows to it by
    # external_txn_id, and a row whose payment is missing is rejected with
    # "Unknown external_txn_id" rather than queued for later. The accepted
    # check catches contract drift (a renamed JSON field, say) immediately
    # instead of surfacing later as a confusing "expected 1 result row".
    batch = call(f"{INGEST}/api/ingest/payments", "POST",
                 json.dumps([payment_row(scenario, txn)]).encode())
    if batch.get("accepted") != 1:
        failures.append(f"payment not accepted: {batch}")
    call(f"{INGEST}/api/ingest/ledger-entries", "POST",
         json.dumps([ledger_row(scenario, txn)]).encode())
    if scenario["settlement"]:
        rows = [settlement_row(scenario, txn)]
        if scenario["settlement"].get("duplicate"):
            # Two DISTINCT batches. P2 drops an exact duplicate (same amount,
            # same date, same batch_id) at ingest, and the engine raises
            # DUPLICATE_SETTLEMENT only when more than one settlement is
            # stored — so two identical rows would persist once and the
            # scenario could never reproduce. The engine never compares batch
            # ids, so the second batch is both legal and invisible to it.
            rows.append(settlement_row(scenario, txn, "-DUP"))
        call(f"{INGEST}/api/ingest/settlements", "POST", json.dumps(rows).encode())
    run = call(f"{RECON}/api/reconcile?sourceSet=ALL", "POST", b"")
    results = call(f"{RECON}/api/reconcile/runs/{run['runId']}/results")
    mine = [r for r in results if r["externalTxnId"] == txn]
    if len(mine) != 1:
        return {}, [f"expected 1 result row, found {len(mine)}"], "FAIL"
    result = mine[0]
    expect = scenario["expect"]
    if result["matchStatus"] != expect["match_status"]:
        failures.append(f"matchStatus={result['matchStatus']}")
    if expect.get("mismatch_type") and result["mismatchType"] != expect["mismatch_type"]:
        failures.append(f"mismatchType={result['mismatchType']}")
    synced = call(f"{CASES}/api/cases/sync", "POST",
                  json.dumps({"runId": run["runId"]}).encode())
    # Sync opens cases for every mismatched row in the run, so its `opened`
    # count cannot vouch for THIS row. The queue scan is row-scoped: match
    # on the result id this scenario produced.
    mine_cases = [c for c in call(f"{CASES}/api/cases")
                  if c["resultId"] == result["resultId"]]
    case_id = None
    if expect["case"]:
        if not mine_cases:
            failures.append("no case synced for mismatched result")
        else:
            case_id = mine_cases[0]["exceptionId"]
    elif mine_cases:
        failures.append("matched result opened a case")
    detail = f"run={run['runId'][:8]} matched={run['matched']} mismatched={run['mismatched']}"
    verdict = "PASS" if not failures else "FAIL"
    if expect["case"] and case_id:
        try:
            finish_case(case_id)
        except (urllib.error.HTTPError, urllib.error.URLError, KeyError, AssertionError) as error:
            failures.append(f"case follow-up failed: {error}")
            verdict = "FAIL"
    return result, failures, f"{verdict} | {scenario['name']} | {detail} | synced={synced}"


def finish_case(case_id: str, analyst: str = "demo-analyst") -> None:
    """Run the analyst follow-up: investigate, correct, assign, resolve."""
    investigation = call(f"{AI}/investigate", "POST",
                         json.dumps({"exceptionId": case_id}).encode())
    assert investigation.get("human_approval_required") is True
    call(f"{CASES}/api/cases/{case_id}/feedback", "POST", json.dumps({
        "analystId": analyst, "originalValue": investigation.get("root_cause", ""),
        "correctedValue": "CONFIRMED", "reason": "demo verification"}).encode())
    call(f"{CASES}/api/cases/{case_id}/assign", "POST", json.dumps({
        "assignedTo": analyst, "actorId": analyst}).encode())
    call(f"{CASES}/api/cases/{case_id}/resolve", "POST", json.dumps({
        "actionType": "DEMO_RESOLVED", "actorType": "ANALYST",
        "actorId": analyst, "notes": "reproducible demo completed"}).encode())


def main() -> int:
    scenarios = json.loads(SCENARIOS.read_text(encoding="utf-8"))
    assert len(scenarios) >= 5, "P13 requires 5+ representative scenarios"
    stamp = uuid.uuid4().hex[:8]
    failed = 0
    for scenario in scenarios:
        txn = f"DEMO-{stamp}-{slug(scenario['name'])}"
        # One scenario's failure is reported, never fatal: a stack problem in
        # scenario 3 must not hide the verdict for scenarios 4-6.
        try:
            _, failures, line = run_scenario(scenario, txn)
        except (urllib.error.HTTPError, urllib.error.URLError, KeyError, ValueError) as error:
            failures, line = [f"scenario aborted: {error}"], f"FAIL | {scenario['name']}"
        if failures:
            failed += 1
            line += " | " + "; ".join(failures)
        print(line)
    print(f"demo: {len(scenarios) - failed}/{len(scenarios)} scenarios passed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
