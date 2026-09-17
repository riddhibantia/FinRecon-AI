"""P8 request, backend projections, and human-review output."""
from datetime import date
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StrictStr


class InvestigationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    exceptionId: UUID
    as_of: date = Field(default_factory=date.today)
    # No invented default tolerance. None means no tolerance judgment.
    tolerance: str | None = None


class BackendEvidence(BaseModel):
    sourceType: StrictStr = Field(min_length=1)
    sourceRecordId: StrictStr = Field(min_length=1)
    fieldName: StrictStr | None
    expectedValue: StrictStr | None
    observedValue: StrictStr | None


class BackendSource(BaseModel):
    sourceType: StrictStr = Field(min_length=1)
    recordId: StrictStr = Field(min_length=1)
    summary: StrictStr | None = None


class CaseDetail(BaseModel):
    exceptionId: UUID
    category: StrictStr
    mismatchType: StrictStr | None = None
    sources: list[BackendSource]
    evidence: list[BackendEvidence]
    caseActions: list[dict]
    auditTrail: list[dict]


class Evidence(BaseModel):
    model_config = ConfigDict(extra="forbid")
    source_type: str = Field(min_length=1)
    source_id: str = Field(min_length=1)
    field: str = Field(min_length=1)
    expected: StrictStr | None
    observed: StrictStr | None


class Citation(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    document: str = Field(min_length=1)
    version: str = Field(min_length=1)
    section: str = Field(min_length=1)
    page: int = Field(ge=1)
    excerpt: str = Field(min_length=1)
    score: float = Field(ge=0, le=1)


class InvestigationResult(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    exceptionId: str
    root_cause: str | None
    summary: str
    recommended_action: str | None
    confidence: float | None = Field(ge=0, le=1)
    confidence_reason: str | None
    evidence: list[Evidence]
    citations: list[Citation]
    status: Literal["HUMAN_REVIEW", "MANUAL_REVIEW"]
    reasons: list[str]
    draft: dict | None
    human_approval_required: Literal[True] = True
