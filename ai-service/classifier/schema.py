"""Validated projections of V1 source rows, not P4 display summaries."""
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Annotated

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, field_validator


def money(value):
    if isinstance(value, bool) or not isinstance(value, (str, int, float, Decimal)):
        raise ValueError("money must be a finite nonnegative NUMERIC(18,2)")
    try:
        number = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError("invalid money") from exc
    if not number.is_finite() or not 0 <= number < Decimal("10000000000000000"):
        raise ValueError("money outside NUMERIC(18,2)")
    if number != number.quantize(Decimal("0.01")):
        raise ValueError("money must have at most two decimal places")
    return number


Money = Annotated[Decimal, BeforeValidator(money)]
Currency = Annotated[str, Field(strict=True, pattern=r"^[A-Z]{3}$")]
Status = Annotated[str, Field(strict=True, min_length=1, max_length=128)]


class Source(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    @field_validator("event_time", "posted_at", check_fields=False)
    @classmethod
    def timezone_required(cls, value: datetime):
        if value.utcoffset() is None:
            raise ValueError("timestamp must have an explicit timezone offset")
        return value

    @field_validator("status", "posting_status", "settlement_status", check_fields=False)
    @classmethod
    def normalize_status(cls, value):
        if not value.strip():
            raise ValueError("status must not be blank")
        return value.strip().upper()


class Payment(Source):
    amount: Money
    currency: Currency
    status: Status
    event_time: datetime


class Ledger(Source):
    gross_amount: Money
    fee_amount: Money
    net_amount: Money
    currency: Currency
    posting_status: Status
    posted_at: datetime


class Settlement(Source):
    settled_amount: Money
    fee_amount: Money
    currency: Currency
    settlement_status: Status
    settlement_date: date


class Context(Source):
    settlement_window_days: Annotated[int, Field(strict=True, ge=0, le=365)]


class Snapshot(Source):
    payment: Payment
    ledgers: Annotated[list[Ledger], Field(max_length=1000)]
    settlements: Annotated[list[Settlement], Field(max_length=1000)]
    context: Context


CLASSES = tuple(sorted((
    "MISSING_SETTLEMENT", "AMOUNT_MISMATCH", "FEE_VARIANCE", "FX_VARIANCE",
    "DUPLICATE_SETTLEMENT", "PARTIAL_SETTLEMENT", "STATUS_MISMATCH",
    "LATE_SETTLEMENT", "UNKNOWN_EXCEPTION",
)))
FEATURE_VERSION = "p6-source-v1"
