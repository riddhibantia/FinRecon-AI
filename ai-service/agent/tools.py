"""Read P4 facts, retrieve P7 policy, calculate Decimal differences, draft only."""
from copy import deepcopy
from datetime import date
from decimal import Decimal, InvalidOperation, localcontext
from uuid import UUID

import httpx
from pydantic import ValidationError

from agent.schema import CaseDetail, Evidence
from classifier.schema import Snapshot


class ToolError(ValueError):
    pass


def text(value, name):
    if not isinstance(value, str) or not value.strip():
        raise ToolError(f"{name} must be a nonempty string")
    return value


def case_id(value):
    try:
        return str(UUID(str(value)))
    except (ValueError, TypeError, AttributeError) as exc:
        raise ToolError("exception_id must be a UUID") from exc


def decimal_value(value):
    if isinstance(value, bool) or not isinstance(value, (str, int, Decimal)):
        raise ToolError("amount and tolerance must be decimal strings or integers")
    try:
        number = Decimal(value)
        if not number.is_finite() or abs(number) >= Decimal("1e18") or number.as_tuple().exponent < -12:
            raise ToolError("decimal must be finite with at most 12 fractional digits and magnitude below 1e18")
        return number
    except InvalidOperation as exc:
        raise ToolError("invalid decimal") from exc


def project_evidence(rows):
    return [Evidence(source_type=row.sourceType, source_id=row.sourceRecordId, field=row.fieldName,
                     expected=row.expectedValue, observed=row.observedValue).model_dump()
            for row in rows if row.fieldName]


class CaseTransport:
    """The graph has no resolve/escalate operation. An optional feedback adapter is operator-owned."""
    def __init__(self, client: httpx.Client, feedback_sender=None):
        self.client = client
        self.feedback_sender = feedback_sender

    def get_case(self, exception_id):
        response = self.client.get(f"/api/cases/{case_id(exception_id)}")
        response.raise_for_status()
        return response.json()

    def queue(self, category):
        response = self.client.get("/api/cases", params={"category": text(category, "category")})
        response.raise_for_status()
        return response.json()

    def feedback(self, **payload):
        if self.feedback_sender is None:
            return {"status_code": 501, "status": "NOT_IMPLEMENTED",
                    "reason": "P4 has no analyst feedback endpoint; nothing was persisted"}
        return self.feedback_sender(**payload)


class ToolRegistry:
    def __init__(self, transport, retriever, snapshot_loader=None):
        self.transport = transport
        self.retriever = retriever
        self.snapshot_loader = snapshot_loader
        names = ("get_case", "get_classifier_snapshot", "get_payment", "get_ledger_entries",
                 "get_settlement_records", "calculate_variance", "get_fee_rule", "get_fx_reference",
                 "find_similar_exceptions", "search_policy", "get_case_history", "create_resolution_draft",
                 "record_analyst_feedback")
        self.registry = {name: getattr(self, name) for name in names}

    def invoke(self, name, **kwargs):
        if name not in self.registry:
            raise ToolError(f"unknown tool: {name}")
        try:
            result = self.registry[name](**kwargs)
            if not isinstance(result, dict):
                raise ToolError(f"{name} returned a non-object result")
            return deepcopy(result)
        except (ValidationError, httpx.HTTPError, ValueError, TypeError, KeyError, AttributeError) as exc:
            raise ToolError(f"{name}: invalid input or unavailable/invalid tool result") from exc

    def get_case(self, exception_id):
        identity = case_id(exception_id)
        detail = CaseDetail.model_validate(self.transport.get_case(identity))
        if str(detail.exceptionId) != identity:
            raise ToolError("backend returned a different case")
        return detail.model_dump(mode="json")

    def get_classifier_snapshot(self, exception_id):
        identity = case_id(exception_id)
        if self.snapshot_loader is None:
            return {"status": "UNAVAILABLE", "reason": "P4 display sources cannot reconstruct a P6 source snapshot"}
        snapshot = Snapshot.model_validate(self.snapshot_loader(identity))
        return {"status": "AVAILABLE", "snapshot": snapshot.model_dump(mode="json")}

    def _records(self, exception_id, source_type, case=None):
        identity = case_id(exception_id)
        detail = CaseDetail.model_validate(case if case is not None else self.get_case(identity))
        if str(detail.exceptionId) != identity:
            raise ToolError("case does not match request")
        return {"records": [source.model_dump() for source in detail.sources if source.sourceType == source_type],
                "evidence": project_evidence([row for row in detail.evidence if row.sourceType == source_type])}

    def get_payment(self, exception_id, case=None):
        return self._records(exception_id, "payment_gateway", case)

    def get_ledger_entries(self, exception_id, case=None):
        return self._records(exception_id, "ledger", case)

    def get_settlement_records(self, exception_id, case=None):
        return self._records(exception_id, "settlement", case)

    def calculate_variance(self, evidence, tolerance=None):
        row = Evidence.model_validate(evidence)
        expected, observed = decimal_value(row.expected), decimal_value(row.observed)
        threshold = decimal_value(tolerance) if tolerance is not None else None
        if threshold is not None and threshold < 0:
            raise ToolError("tolerance cannot be negative")
        with localcontext() as context:
            context.prec = 42
            difference = observed - expected
            return {"difference": format(difference, "f"), "absolute_difference": format(abs(difference), "f"),
                    "tolerance": format(threshold, "f") if threshold is not None else None,
                    "within_tolerance": abs(difference) <= threshold if threshold is not None else None,
                    "evidence": [row.model_dump()]}

    def search_policy(self, query, as_of=None):
        text(query, "query")
        if as_of is not None and not isinstance(as_of, date):
            raise ToolError("as_of must be a date")
        result = self.retriever.search(query, k=3, as_of=as_of).to_dict()
        if (not isinstance(result, dict) or not isinstance(result.get("hits"), list)
                or result.get("status") not in {"EVIDENCE_FOUND", "INSUFFICIENT_EVIDENCE"}
                or any(not isinstance(hit, dict) for hit in result["hits"])):
            raise ToolError("invalid policy result")
        return result

    def get_fee_rule(self, query, as_of=None):
        return {**self.search_policy(query, as_of), "rule_verified": False,
                "reason": "Policy text only; no executable fee rule store is wired"}

    def get_fx_reference(self, query, as_of=None):
        return {**self.search_policy(query, as_of), "rule_verified": False,
                "reason": "Policy text only; no dated FX rate store is wired"}

    def find_similar_exceptions(self, exception_id, category):
        identity = case_id(exception_id)
        text(category, "category")
        cases = self.transport.queue(category)
        if not isinstance(cases, list) or any(not isinstance(row, dict) or not isinstance(row.get("exceptionId"), str)
                                              or not isinstance(row.get("category"), str) for row in cases):
            raise ToolError("invalid case queue")
        return {"cases": [row for row in cases if row["category"] == category and row["exceptionId"] != identity],
                "method": "category_filter"}

    def get_case_history(self, exception_id, case=None):
        identity = case_id(exception_id)
        detail = CaseDetail.model_validate(case if case is not None else self.get_case(identity))
        if str(detail.exceptionId) != identity:
            raise ToolError("case does not match request")
        return {"caseActions": detail.caseActions, "auditTrail": detail.auditTrail}

    def create_resolution_draft(self, exception_id, action, citations):
        identity = case_id(exception_id)
        text(action, "action")
        if not isinstance(citations, list) or any(not isinstance(row, dict) for row in citations):
            raise ToolError("citations must be objects")
        return {"exceptionId": identity, "status": "DRAFT", "action": action,
                "citations": deepcopy(citations), "human_approval_required": True,
                "notice": "Human approval required. No resolution or escalation has been submitted."}

    def record_analyst_feedback(self, exception_id, actor_id, notes):
        return self.transport.feedback(exception_id=case_id(exception_id), actor_id=text(actor_id, "actor_id"),
                                       notes=text(notes, "notes"))
