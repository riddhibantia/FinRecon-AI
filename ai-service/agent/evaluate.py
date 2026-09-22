"""P13 agent evaluation: fixed set, offline transport, measured task success.

Nothing here talks to a live backend. Each case replays a recorded P4 payload
through httpx.MockTransport, so the score is reproducible and cannot be
inflated by a running system. Optional case snapshots are replayed only as
raw dicts through ToolRegistry's snapshot_loader; ToolRegistry validates them
as Pydantic Snapshot, so evaluate.py never imports classifier.schema.
"""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import httpx

from agent import InvestigationRequest, Investigator
from agent.tools import CaseTransport, ToolRegistry
from rag import build_retriever

CASES_PATH = Path(__file__).resolve().parents[1] / "evaluation" / "agent_cases.json"
ARTIFACT_PATH = CASES_PATH.with_name("p13_agent_metrics.json")


def load_cases(path: Path = CASES_PATH) -> list[dict]:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _recorded_transport(case: dict) -> httpx.MockTransport:
    payload = case["case"]
    exception_id = payload["exceptionId"]

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith(f"/api/cases/{exception_id}"):
            return httpx.Response(200, json=payload)
        if request.url.path.endswith("/api/cases"):
            return httpx.Response(200, json=[])
        return httpx.Response(404, json={"error": "NOT_FOUND"})

    return httpx.MockTransport(handler)


def _recorded_transports(cases: list[dict]) -> dict[str, httpx.MockTransport]:
    return {case["id"]: _recorded_transport(case) for case in cases}


def _score_case(case: dict, result: dict) -> tuple[bool, list[str]]:
    expect = case["expect"]
    codes: list[str] = []
    if result.get("status") != expect["status"]:
        codes.append(f"status:{result.get('status')}!={expect['status']}")
    if result.get("human_approval_required") is not True:
        codes.append("human_approval_required:not_true")
    # Top-level status is HUMAN_REVIEW or MANUAL_REVIEW only (schema line 70);
    # a draft is checked at result["draft"]["status"], never at top level.
    expected_draft = (expect.get("draft") or {}).get("status")
    actual_draft = (result.get("draft") or {}).get("status")
    if expected_draft is not None and actual_draft != expected_draft:
        codes.append(f"draft:{actual_draft}!={expected_draft}")
    documents = {citation["document"] for citation in result.get("citations", [])}
    for required in expect.get("must_cite_documents", []):
        if required not in documents:
            codes.append(f"citation_missing:{required}")
    summary = result.get("summary") or ""
    for forbidden in expect.get("summary_must_not_contain", []):
        if forbidden in summary:
            codes.append(f"summary_contains:{forbidden}")
    return (not codes), codes


def evaluate(cases: list[dict], transports: dict[str, httpx.MockTransport]) -> dict:
    scored = []
    for case in cases:
        # A missing transport key must raise: no factory, no default, no
        # fallback, so no code path can construct a non-mock client. The
        # base_url is a dummy host — the pinned mock transport answers every
        # request, so nothing can leave the process.
        client = httpx.Client(base_url="http://cases", transport=transports[case["id"]])
        try:
            tools = ToolRegistry(
                CaseTransport(client),
                build_retriever(database_url=""),
                snapshot_loader=(lambda _, c=case: c["snapshot"]) if case.get("snapshot") else None)
            investigator = Investigator(tools)
            try:
                result = investigator.investigate(
                    InvestigationRequest(exceptionId=case["case"]["exceptionId"],
                                           as_of=date.today()))
                ok, codes = _score_case(case, result)
                scored.append({"id": case["id"], "success": ok, "codes": codes,
                               "status": result.get("status"),
                               "draft_status": (result.get("draft") or {}).get("status"),
                               "confidence": result.get("confidence"),
                               "citations": [c["document"] for c in result.get("citations", [])]})
            except Exception as exc:
                # A harness or transport failure is a failed case, never a
                # crashed run: the score sheet must record it honestly.
                scored.append({"id": case["id"], "success": False,
                               "codes": [f"error:{type(exc).__name__}"],
                               "status": None, "draft_status": None,
                               "confidence": None, "citations": []})
        finally:
            client.close()
    passed = sum(1 for row in scored if row["success"])
    manual = sum(1 for row in scored if row["status"] == "HUMAN_REVIEW")
    cited = sum(1 for row in scored if row["citations"])
    total = len(scored) or 1
    return {
        "evaluated_at": date.today().isoformat(),
        "cases": scored,
        "task_success_rate": round(passed / total, 4),
        "citation_correctness": round(cited / total, 4),
        "manual_review_rate": round(manual / total, 4),
        "codes": [code for row in scored for code in row["codes"]],
    }


def main() -> None:
    cases = load_cases()
    result = evaluate(cases, _recorded_transports(cases))
    ARTIFACT_PATH.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "cases"}, indent=2))


if __name__ == "__main__":
    main()
