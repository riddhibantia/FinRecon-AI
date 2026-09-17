"""Synthetic, offline P8 acceptance scenarios; no persisted classifier required."""
from datetime import date
from decimal import Decimal

import httpx
import pytest
from fastapi.testclient import TestClient

from agent import Investigator, InvestigationRequest
from agent.tools import CaseTransport, ToolError, ToolRegistry
from agent.verification import SourceVerifier, verify_citations, verify_evidence
from classifier.dataset import scenario_families
from classifier.schema import Snapshot
from rag import build_retriever


CASE_ID = "11111111-1111-4111-8111-111111111111"


def case_data():
    return {"exceptionId": CASE_ID, "category": "AMOUNT_MISMATCH", "mismatchType": "AMOUNT_MISMATCH",
            "sources": [{"sourceType": "payment_gateway", "recordId": "p1", "summary": "synthetic payment"},
                        {"sourceType": "ledger", "recordId": "l1", "summary": "synthetic ledger"},
                        {"sourceType": "settlement", "recordId": "s1", "summary": "synthetic settlement"}],
            "evidence": [{"sourceType": "ledger", "sourceRecordId": "l1", "fieldName": "gross_amount",
                          "expectedValue": "100.00", "observedValue": "99.00"}],
            "caseActions": [{"actionType": "ASSIGN", "actorId": "analyst"}], "auditTrail": []}


class FakePredictor:
    def __init__(self):
        self.snapshots = []

    def predict(self, snapshot):
        assert isinstance(snapshot, Snapshot)
        self.snapshots.append(snapshot)
        return {"category": "AMOUNT_MISMATCH", "confidence": 0.9}


@pytest.fixture
def bundle():
    case = case_data()
    calls = []

    def handle(request):
        calls.append((request.method, request.url.path))
        if request.url.path == "/api/cases":
            assert request.url.params["category"] == "AMOUNT_MISMATCH"
            return httpx.Response(200, json=[{"exceptionId": "other", "category": "AMOUNT_MISMATCH"},
                                             {"exceptionId": CASE_ID, "category": "AMOUNT_MISMATCH"}])
        return httpx.Response(200, json=case)

    client = httpx.Client(base_url="http://cases", transport=httpx.MockTransport(handle))
    retriever = build_retriever(database_url="")
    snapshot = next(scenario_families(groups=1, seed=17))["snapshot"]
    tools = ToolRegistry(CaseTransport(client), retriever, snapshot_loader=lambda _: snapshot)
    yield tools, case, calls
    client.close()


@pytest.mark.parametrize("name,source", [("get_payment", "payment_gateway"),
                                         ("get_ledger_entries", "ledger"),
                                         ("get_settlement_records", "settlement")])
def test_record_tools_return_only_case_sources(bundle, name, source):
    tools, _, _ = bundle
    result = tools.invoke(name, exception_id=CASE_ID)
    assert result["records"][0]["sourceType"] == source
    assert "amount" not in result["records"][0]
    with pytest.raises(ToolError):
        tools.invoke(name, exception_id="../../resolve")


def test_variance_decimal_and_explicit_tolerance(bundle):
    tools, _, _ = bundle
    evidence = tools.invoke("get_ledger_entries", exception_id=CASE_ID)["evidence"][0]
    result = tools.invoke("calculate_variance", evidence=evidence, tolerance="1.00")
    assert result["difference"] == "-1.00"
    assert result["within_tolerance"] is True
    assert tools.invoke("calculate_variance", evidence=evidence, tolerance="0.99")["within_tolerance"] is False
    assert Decimal(result["difference"]) == Decimal("99.00") - Decimal("100.00")
    with pytest.raises(ToolError):
        tools.invoke("calculate_variance", evidence=evidence, tolerance="NaN")
    with pytest.raises(ToolError):
        tools.invoke("calculate_variance", evidence={**evidence, "observed": "absent"}, tolerance="0.01")


@pytest.mark.parametrize("name", ["search_policy", "get_fee_rule", "get_fx_reference"])
def test_policy_tools_cite_source_without_computing_rules(bundle, name):
    tools, _, _ = bundle
    result = tools.invoke(name, query="fee variance settlement ledger", as_of=date(2026, 6, 1))
    assert result["hits"]
    assert SourceVerifier().verify(result["hits"][0])
    if name != "search_policy":
        assert result["rule_verified"] is False
        assert "rate" not in result
    with pytest.raises(ToolError):
        tools.invoke(name, query="", as_of=date(2026, 6, 1))


def test_similar_and_history_tools(bundle):
    tools, _, _ = bundle
    assert tools.invoke("find_similar_exceptions", exception_id=CASE_ID, category="AMOUNT_MISMATCH")["cases"] == [
        {"exceptionId": "other", "category": "AMOUNT_MISMATCH"}]
    assert tools.invoke("get_case_history", exception_id=CASE_ID)["caseActions"][0]["actionType"] == "ASSIGN"
    with pytest.raises(ToolError):
        tools.invoke("find_similar_exceptions", exception_id=CASE_ID, category="")
    with pytest.raises(ToolError):
        tools.invoke("get_case_history", exception_id="bad")


def test_draft_and_unsupported_feedback_never_write_transport(bundle):
    tools, _, calls = bundle
    draft = tools.invoke("create_resolution_draft", exception_id=CASE_ID,
                         action="Review source records with analyst", citations=[])
    assert draft["human_approval_required"] is True
    assert draft["status"] == "DRAFT"
    feedback = tools.invoke("record_analyst_feedback", exception_id=CASE_ID, actor_id="analyst", notes="Reviewed")
    assert feedback["status_code"] == 501
    assert calls == []
    with pytest.raises(ToolError):
        tools.invoke("create_resolution_draft", exception_id=CASE_ID, action="", citations=[])
    with pytest.raises(ToolError):
        tools.invoke("record_analyst_feedback", exception_id=CASE_ID, actor_id="", notes="Reviewed")


def test_feedback_only_uses_explicit_backend_adapter(bundle):
    tools, _, _ = bundle
    received = []
    tools.transport.feedback_sender = lambda **payload: received.append(payload) or {"status_code": 201}
    assert tools.invoke("record_analyst_feedback", exception_id=CASE_ID, actor_id="analyst", notes="Reviewed")["status_code"] == 201
    assert received[0]["notes"] == "Reviewed"


def test_workflow_output_and_classifier_boundary(bundle):
    tools, _, calls = bundle
    predictor = FakePredictor()
    result = Investigator(tools, predictor).investigate(InvestigationRequest(exceptionId=CASE_ID, as_of=date(2026, 6, 1)))
    assert result["status"] == "HUMAN_REVIEW"
    assert result["root_cause"] == "AMOUNT_MISMATCH"
    assert result["confidence"] == 0.9
    assert predictor.snapshots
    assert {"exceptionId", "root_cause", "summary", "recommended_action", "confidence", "evidence", "citations"} <= result.keys()
    assert result["evidence"][0] == {"source_type": "ledger", "source_id": "l1", "field": "gross_amount", "expected": "100.00", "observed": "99.00"}
    assert {"document", "version", "section", "page", "excerpt", "score"} == result["citations"][0].keys()
    assert result["draft"]["human_approval_required"]
    assert all(method == "GET" for method, _ in calls)


@pytest.mark.parametrize("mode", ["missing", "conflict", "invalid"])
def test_evidence_failure_forces_manual_review(bundle, mode):
    tools, case, _ = bundle
    if mode == "missing":
        case["evidence"] = []
    elif mode == "conflict":
        case["evidence"].append({**case["evidence"][0], "observedValue": "98.00"})
    else:
        case["evidence"] = "invalid backend result"
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID))
    assert result["status"] == "MANUAL_REVIEW"
    assert result["recommended_action"] is None
    assert result["confidence"] is None
    assert result["draft"] is None
    assert result["reasons"]
    if mode != "conflict":
        assert result["evidence"] == []


def test_citation_verification_rejects_fabrication_and_mismatched_metadata(bundle):
    tools, _, _ = bundle
    hit = tools.invoke("search_policy", query="amount mismatch ledger", as_of=date(2026, 6, 1))["hits"][0]
    for changed in ({"excerpt": "Invented authorization to resolve automatically"}, {"version": "fake"}, {"page": 999}):
        verified, errors = verify_citations([{**hit, **changed}], [hit], SourceVerifier())
        assert verified == []
        assert errors


def test_evidence_verification_rejects_untraced_value():
    original = {"source_type": "ledger", "source_id": "l1", "field": "gross_amount", "expected": "100.00", "observed": "99.00"}
    verified, errors = verify_evidence([{**original, "observed": "999.00"}], [original])
    assert verified == []
    assert errors


def test_forged_draft_citation_forces_manual_review(bundle):
    tools, _, _ = bundle
    original = tools.registry["create_resolution_draft"]

    def tamper(**kwargs):
        result = original(**kwargs)
        result["citations"][0]["excerpt"] = "Made up policy"
        return result

    tools.registry["create_resolution_draft"] = tamper
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID, as_of=date(2026, 6, 1)))
    assert result["status"] == "MANUAL_REVIEW"
    assert all(citation["excerpt"] != "Made up policy" for citation in result["citations"])
    assert result["recommended_action"] is None


def test_summary_generator_cannot_add_financial_claims(bundle):
    tools, _, _ = bundle
    result = Investigator(tools, FakePredictor(), summary_generator=lambda _: ["Payment was 9000 USD"]).investigate(
        InvestigationRequest(exceptionId=CASE_ID, as_of=date(2026, 6, 1)))
    assert result["status"] == "MANUAL_REVIEW"
    assert "9000" not in result["summary"]


def test_missing_snapshot_does_not_reconstruct_classifier_input(bundle):
    tools, _, _ = bundle
    tools.snapshot_loader = None
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID, as_of=date(2026, 6, 1)))
    assert result["confidence"] is None
    assert result["confidence_reason"]


def test_api_gracefully_reports_missing_artifact(tmp_path):
    from app import create_app
    with TestClient(create_app(model_dir=tmp_path)) as client:
        response = client.post("/investigate", json={"exceptionId": CASE_ID})
        assert response.status_code == 503
        assert client.get("/health").json()["status"] == "UP"


def test_retriever_fabrication_fails_source_check_even_when_retrieved(bundle):
    tools, _, _ = bundle
    hit = tools.invoke("search_policy", query="amount mismatch ledger", as_of=date(2026, 6, 1))["hits"][0]
    forged = {**hit, "excerpt": "Unstored policy authorizes a refund"}
    verified, errors = verify_citations([forged], [forged], SourceVerifier())
    assert verified == []
    assert errors


def test_invalid_record_tool_result_cannot_reach_draft(bundle):
    tools, _, _ = bundle
    tools.registry["get_payment"] = lambda **_: {"records": "not a list", "evidence": []}
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID))
    assert result["status"] == "MANUAL_REVIEW"
    assert result["draft"] is None


def test_classifier_disagreement_does_not_replace_financial_truth(bundle):
    tools, _, _ = bundle

    class DisagreeingPredictor:
        def predict(self, snapshot):
            return {"category": "FX_VARIANCE", "confidence": 0.99}

    result = Investigator(tools, DisagreeingPredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID))
    assert result["root_cause"] == "AMOUNT_MISMATCH"
    assert result["status"] == "MANUAL_REVIEW"
    assert result["confidence"] is None


def test_api_injected_investigator_uses_real_graph_without_artifacts(bundle, tmp_path):
    from app import create_app
    tools, _, calls = bundle
    investigator = Investigator(tools, FakePredictor())
    with TestClient(create_app(model_dir=tmp_path, investigator=investigator)) as client:
        response = client.post("/investigate", json={"exceptionId": CASE_ID, "as_of": "2026-06-01"})
        assert response.status_code == 200
        assert response.json()["status"] == "HUMAN_REVIEW"
        assert client.post("/investigate", json={"exceptionId": "bad"}).status_code == 422
    assert all(method == "GET" for method, _ in calls)


def test_backend_http_error_is_controlled_manual_review(bundle):
    tools, _, _ = bundle

    def unavailable(request):
        return httpx.Response(503)

    with httpx.Client(base_url="http://cases", transport=httpx.MockTransport(unavailable)) as client:
        tools.transport = CaseTransport(client)
        result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID))
    assert result["status"] == "MANUAL_REVIEW"
    assert result["evidence"] == []
    assert result["confidence"] is None


def test_tolerance_is_not_invented(bundle):
    tools, _, _ = bundle
    evidence = tools.invoke("get_ledger_entries", exception_id=CASE_ID)["evidence"][0]
    result = tools.invoke("calculate_variance", evidence=evidence)
    assert result["difference"] == "-1.00"
    assert result["within_tolerance"] is None
    assert result["tolerance"] is None


def test_fee_and_fx_policy_are_not_executable_rules(bundle):
    tools, case, _ = bundle
    for category, field, expected, observed in [("FEE_VARIANCE", "fee_amount", "1.00", "2.00"),
                                                 ("FX_VARIANCE", "currency", "INR", "USD")]:
        case["category"] = case["mismatchType"] = category
        case["evidence"] = [{"sourceType": "settlement", "sourceRecordId": "s1", "fieldName": field,
                             "expectedValue": expected, "observedValue": observed}]
        tools.transport.queue = lambda category: []
        result = Investigator(tools).investigate(InvestigationRequest(exceptionId=CASE_ID, as_of=date(2026, 6, 1)))
        assert result["status"] == "MANUAL_REVIEW"
        assert result["recommended_action"] is None
        assert any("store" in reason for reason in result["reasons"])


def test_no_effective_policy_requires_manual_review(bundle):
    tools, _, _ = bundle
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID, as_of=date(2025, 1, 1)))
    assert result["status"] == "MANUAL_REVIEW"
    assert result["citations"] == []
    assert result["recommended_action"] is None
    assert result["confidence"] is None


def test_invalid_search_tool_result_is_controlled(bundle):
    tools, _, _ = bundle
    tools.registry["search_policy"] = lambda **_: {"hits": None}
    result = Investigator(tools, FakePredictor()).investigate(InvestigationRequest(exceptionId=CASE_ID))
    assert result["status"] == "MANUAL_REVIEW"
    assert result["citations"] == []


def test_decimal_difference_keeps_fraction_at_large_magnitude(bundle):
    tools, _, _ = bundle
    row = {"source_type": "ledger", "source_id": "l1", "field": "gross_amount",
           "expected": "999999999999999999.000000000001", "observed": "999999999999999999.000000000003"}
    result = tools.invoke("calculate_variance", evidence=row, tolerance="0.000000000001")
    assert result["difference"] == "0.000000000002"
    assert result["within_tolerance"] is False
