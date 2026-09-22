"""Scripted LangGraph investigator: tools supply facts; humans own resolution."""
from copy import deepcopy
import math
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from agent.schema import CaseDetail, InvestigationRequest, InvestigationResult
from agent.tools import ToolError, project_evidence
from agent.verification import SourceVerifier, verify_citations, verify_evidence
from classifier.prediction import ModelUnavailable
from classifier.schema import Snapshot
from pydantic import ValidationError


class State(TypedDict, total=False):
    request: InvestigationRequest
    case: dict
    tool_results: dict
    errors: list[str]
    prediction: dict
    confidence_reason: str | None
    evidence: list[dict]
    hits: list[dict]
    citations: list[dict]
    root_cause: str | None
    action: str | None
    draft: dict | None
    summary: str
    result: dict


CATEGORY_FIELDS = {
    "AMOUNT_MISMATCH": "gross_amount", "FEE_VARIANCE": "fee_amount", "FX_VARIANCE": "currency",
    "PARTIAL_SETTLEMENT": "settled_amount", "MISSING_SETTLEMENT": "settlement",
    "DUPLICATE_SETTLEMENT": "settlement_count", "STATUS_MISMATCH": "status",
    "LATE_SETTLEMENT": "settlement_date",
}
MONEY_FIELDS = {"gross_amount", "fee_amount", "settled_amount"}
ACTION = "Have an analyst compare the cited policy with the source evidence before choosing a resolution."


def scripted_summary(sentences):
    """Injection contract: select/reorder existing grounded sentences, never add prose."""
    return list(sentences)


class Investigator:
    def __init__(self, tools, predictor=None, source_verifier=None, summary_generator=None):
        self.tools = tools
        self.predictor = predictor
        self.source_verifier = source_verifier or SourceVerifier()
        self.summary_generator = summary_generator or scripted_summary
        graph = StateGraph(State)
        nodes = ("classify_exception", "load_evidence", "query_records", "find_similar", "retrieve_policy",
                 "analyze_root_cause", "draft_resolution", "verify_evidence_and_citations",
                 "generate_case_summary", "human_review")
        previous = START
        for name in nodes:
            graph.add_node(name, getattr(self, name))
            graph.add_edge(previous, name)
            previous = name
        graph.add_edge(previous, END)
        self.graph = graph.compile()

    def investigate(self, request):
        request = request if isinstance(request, InvestigationRequest) else InvestigationRequest.model_validate(request)
        return self.graph.invoke({"request": request, "errors": [], "tool_results": {}, "prediction": {},
                                  "confidence_reason": None, "case": {}, "evidence": [], "hits": [],
                                  "citations": [], "root_cause": None, "action": None, "draft": None})["result"]

    def _call(self, state, name, **kwargs):
        result = self.tools.invoke(name, **kwargs)
        state["tool_results"][name] = result
        return result

    def classify_exception(self, state):
        try:
            detail = self._call(state, "get_case", exception_id=state["request"].exceptionId)
            # Validate again at the graph boundary: replacement tools remain untrusted.
            try:
                state["case"] = CaseDetail.model_validate(detail).model_dump(mode="json")
            except ValidationError as exc:
                raise ToolError("invalid case detail") from exc
            if state["case"]["exceptionId"] != str(state["request"].exceptionId):
                raise ToolError("case identity mismatch")
        except (ToolError, OSError, ValueError, TypeError, KeyError, AttributeError) as exc:
            state["case"] = {}
            state["errors"].append(f"Case evidence unavailable: {exc}")
            return state
        try:
            source = self._call(state, "get_classifier_snapshot", exception_id=state["request"].exceptionId)
            if source.get("status") != "AVAILABLE":
                state["confidence_reason"] = source.get("reason", "Classifier snapshot unavailable")
            elif self.predictor is None:
                state["confidence_reason"] = "Trained classifier artifact unavailable"
            else:
                try:
                    snapshot = Snapshot.model_validate(source["snapshot"])
                except ValidationError as exc:
                    raise ToolError("invalid classifier snapshot") from exc
                prediction = self.predictor.predict(snapshot)
                confidence = prediction["confidence"]
                if (isinstance(confidence, bool) or not isinstance(confidence, (int, float))
                        or not math.isfinite(confidence) or not 0 <= confidence <= 1
                        or not isinstance(prediction.get("category"), str)):
                    raise ToolError("invalid classifier probability")
                state["prediction"] = prediction
        except (ToolError, ModelUnavailable, OSError, ValueError, TypeError, KeyError, AttributeError) as exc:
            state["confidence_reason"] = f"Classifier unavailable: {exc}"
        return state

    def load_evidence(self, state):
        if state["case"]:
            try:
                try:
                    state["evidence"] = project_evidence(CaseDetail.model_validate(state["case"]).evidence)
                except ValidationError as exc:
                    raise ToolError("invalid case detail") from exc
                self._call(state, "get_case_history", exception_id=state["request"].exceptionId, case=state["case"])
            except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
                state["errors"].append(f"Invalid evidence or history: {exc}")
        if not state["evidence"]:
            state["errors"].append("Insufficient evidence: no compared source fields are available")
        return state

    def query_records(self, state):
        if not state["case"]:
            return state
        for name in ("get_payment", "get_ledger_entries", "get_settlement_records"):
            try:
                result = self._call(state, name, exception_id=state["request"].exceptionId, case=state["case"])
                if not isinstance(result.get("records"), list) or not isinstance(result.get("evidence"), list):
                    raise ToolError("invalid record projection")
                try:
                    expected_rows = project_evidence(CaseDetail.model_validate(state["case"]).evidence)
                except ValidationError as exc:
                    raise ToolError("invalid case detail") from exc
                _, record_errors = verify_evidence(result["evidence"], expected_rows)
                state["errors"].extend(record_errors)
            except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
                state["errors"].append(str(exc))
        for index, row in enumerate(state["evidence"]):
            if row["field"] in MONEY_FIELDS and row["observed"] != "absent":
                try:
                    result = self.tools.invoke("calculate_variance", evidence=row, tolerance=state["request"].tolerance)
                    state["tool_results"][f"variance_{index}"] = result
                except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
                    state["errors"].append(f"Variance unavailable: {exc}")
        return state

    def find_similar(self, state):
        if state["case"]:
            try:
                self._call(state, "find_similar_exceptions", exception_id=state["request"].exceptionId,
                           category=state["case"]["category"])
            except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
                state["errors"].append(f"Similar cases unavailable: {exc}")
        return state

    def retrieve_policy(self, state):
        if not state["case"]:
            return state
        category = state["case"]["category"]
        query = category.replace("_", " ").lower() + " reconciliation ledger settlement"
        try:
            result = self._call(state, "search_policy", query=query, as_of=state["request"].as_of)
            if not isinstance(result.get("hits"), list) or any(not isinstance(hit, dict) for hit in result["hits"]):
                raise ToolError("Invalid policy hit collection")
            state["hits"] = result["hits"]
            if not state["hits"]:
                state["errors"].append("Insufficient policy evidence")
            if category in {"FEE_VARIANCE", "FX_VARIANCE"}:
                name = "get_fee_rule" if category == "FEE_VARIANCE" else "get_fx_reference"
                policy = self._call(state, name, query=query, as_of=state["request"].as_of)
                state["errors"].append(policy["reason"])
        except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
            state["errors"].append(f"Policy unavailable: {exc}")
        return state

    def analyze_root_cause(self, state):
        if not state["case"]:
            return state
        category = state["case"]["category"]
        state["root_cause"] = state["case"].get("mismatchType") or category
        required = CATEGORY_FIELDS.get(category)
        relevant = [row for row in state["evidence"] if row["field"] == required]
        if required is None or not relevant or any(row["expected"] is None or row["observed"] is None for row in relevant):
            state["errors"].append("Insufficient category-specific evidence for a resolution draft")
        elif not any(row["expected"] != row["observed"] for row in relevant):
            state["errors"].append("Conflicting evidence: category reports a mismatch but compared values agree")
        predicted = state["prediction"].get("category")
        if predicted and predicted != category:
            state["errors"].append("Classifier category conflicts with the deterministic case category")
        state["action"] = ACTION if not state["errors"] else None
        return state

    def draft_resolution(self, state):
        if state["action"]:
            try:
                state["draft"] = self._call(state, "create_resolution_draft", exception_id=state["request"].exceptionId,
                                              action=state["action"], citations=deepcopy(state["hits"]))
            except (ToolError, OSError, ValidationError, ValueError, TypeError, KeyError, AttributeError) as exc:
                state["errors"].append(f"Draft unavailable: {exc}")
        return state

    def verify_evidence_and_citations(self, state):
        try:
            provenance = project_evidence(CaseDetail.model_validate(state["tool_results"]["get_case"]).evidence) if state["case"] else []
        except (ValidationError, ValueError, TypeError, KeyError, AttributeError, OSError) as exc:
            provenance = []
            state["errors"].append(f"Invalid case provenance: {exc}")
        state["evidence"], errors = verify_evidence(state["evidence"], provenance)
        state["errors"].extend(errors)
        draft = state["draft"]
        candidates = state["hits"]
        if draft is not None:
            if (draft.get("action") != ACTION or draft.get("human_approval_required") is not True
                    or draft.get("exceptionId") != str(state["request"].exceptionId)
                    or draft.get("status") != "DRAFT" or not isinstance(draft.get("citations"), list)):
                state["errors"].append("Invalid resolution draft; human approval boundary failed")
                candidates = []
            else:
                candidates = draft["citations"]
        state["citations"], errors = verify_citations(candidates, state["hits"], self.source_verifier, state["request"].as_of)
        state["errors"].extend(errors)
        if not state["citations"]:
            state["errors"].append("No verified policy citations")
        if state["draft"] is not None:
            state["draft"]["citations"] = deepcopy(state["citations"])
        return state

    def generate_case_summary(self, state):
        sentences = []
        if state["root_cause"]:
            sentences.append(f"The reconciliation case reports {state['root_cause']}.")
        for row in state["evidence"]:
            sentences.append(f"{row['source_type']} {row['source_id']}, {row['field']}: expected {row['expected']}; observed {row['observed']}.")
        for citation in state["citations"]:
            sentences.append(f"Policy {citation['document']} version {citation['version']}, {citation['section']} (page {citation['page']}): {citation['excerpt']}")
        if not sentences:
            sentences.append("Insufficient verified evidence to summarize this case.")
        try:
            selected = self.summary_generator(tuple(sentences))
            if (not isinstance(selected, (list, tuple)) or not selected
                    or any(not isinstance(item, str) or item not in sentences for item in selected)):
                raise ValueError("Summary generator introduced an unsupported statement")
            state["summary"] = " ".join(selected)
        except (OSError, ValueError, TypeError) as exc:
            state["errors"].append(str(exc))
            state["summary"] = " ".join(sentences)
        return state

    def human_review(self, state):
        manual = bool(state["errors"])
        confidence = None if manual else state["prediction"].get("confidence")
        state["result"] = InvestigationResult(
            exceptionId=str(state["request"].exceptionId), root_cause=state["root_cause"], summary=state["summary"],
            recommended_action=None if manual else state["action"], confidence=confidence,
            confidence_reason="Evidence requires manual review" if manual else state["confidence_reason"],
            evidence=state["evidence"], citations=state["citations"], status="MANUAL_REVIEW" if manual else "HUMAN_REVIEW",
            reasons=list(dict.fromkeys(state["errors"])), draft=None if manual else state["draft"],
        ).model_dump(mode="json")
        return state
