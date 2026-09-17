"""Features from P4 CaseDetail; never consume label-selected evidence."""

from decimal import Decimal, InvalidOperation
import math
from collections.abc import Mapping


SOURCE_COUNTS = {
    "payment_gateway": "payment_count",
    "ledger": "ledger_count",
    "settlement": "settlement_count",
}


def extract_case_features(payload: Mapping) -> dict[str, float | int]:
    """Return observable counts and variance without parsing display summaries.

    P4 chooses evidence fields from the deterministic category. Including those
    field names would leak the target. IDs, lifecycle, severity and notes are
    likewise deliberately excluded. Missing data is an error, not a zero.
    """
    if not isinstance(payload, Mapping):
        raise ValueError("case must be an object")
    sources = payload.get("sources")
    if not isinstance(sources, list):
        raise ValueError("sources must be an explicit list")
    features = dict.fromkeys(SOURCE_COUNTS.values(), 0)
    for source in sources:
        if not isinstance(source, Mapping):
            raise ValueError("sourceType must identify a source object")
        kind = source.get("sourceType")
        if not isinstance(kind, str) or kind not in SOURCE_COUNTS:
            raise ValueError("sourceType is not a canonical source")
        features[SOURCE_COUNTS[kind]] += 1
    value = payload.get("amountDifference")
    if value is None or isinstance(value, bool):
        raise ValueError("amountDifference must be finite numeric evidence")
    try:
        amount = Decimal(str(value))
        numeric = float(amount)
    except (InvalidOperation, ValueError, OverflowError) as exc:
        raise ValueError("amountDifference must be finite numeric evidence") from exc
    if not math.isfinite(numeric):
        raise ValueError("amountDifference must be finite numeric evidence")
    features["amount_difference"] = numeric
    features["absolute_amount_difference"] = abs(numeric)
    return features


def extract_source_features(snapshot) -> dict[str, float | str]:
    """Symmetric source comparisons; no outcomes, selected evidence or identifiers.

    Decimal arithmetic is retained until the ML feature boundary. Absent rows
    have explicit counts and zero aggregates, never invented source records.
    Settlement dates have day precision; do not invent intraday settlement times.
    """
    from classifier.schema import Snapshot

    if not isinstance(snapshot, Snapshot):
        snapshot = Snapshot.model_validate(snapshot)
    payment = snapshot.payment
    ledgers = snapshot.ledgers
    settlements = snapshot.settlements
    zero = Decimal(0)
    amount_scale = max(payment.amount, Decimal("0.01"))
    features = {
        "payment_amount": float(payment.amount),
        "ledger_count": len(ledgers),
        "settlement_count": len(settlements),
        "gateway_status": payment.status,
        "gateway_currency": payment.currency,
        "window_days": snapshot.context.settlement_window_days,
        "ledger_statuses": "|".join(sorted({row.posting_status for row in ledgers})),
        "settlement_statuses": "|".join(sorted({row.settlement_status for row in settlements})),
        "ledger_currency_disagreements": sum(row.currency != payment.currency for row in ledgers),
        "settlement_currency_disagreements": sum(row.currency != payment.currency for row in settlements),
        "ledger_status_disagreements": sum(row.posting_status != payment.status for row in ledgers),
        "settlement_status_disagreements": sum(row.settlement_status != payment.status for row in settlements),
        "settled_total": float(sum((row.settled_amount for row in settlements), zero)),
    }
    series = {
        "gross": [row.gross_amount for row in ledgers],
        "ledger_fee": [row.fee_amount for row in ledgers],
        "net": [row.net_amount for row in ledgers],
        "settled": [row.settled_amount for row in settlements],
        "settlement_fee": [row.fee_amount for row in settlements],
        "gross_delta": [row.gross_amount - payment.amount for row in ledgers],
        "fee_delta": [s.fee_amount - l.fee_amount for s in settlements for l in ledgers],
        "net_delta": [s.settled_amount - l.net_amount for s in settlements for l in ledgers],
        "posting_lag_hours": [(row.posted_at - payment.event_time).total_seconds() / 3600 for row in ledgers],
        "settlement_lag_days": [(row.settlement_date - payment.event_time.date()).days for row in settlements],
        "lateness_days": [(row.settlement_date - payment.event_time.date()).days
                          - snapshot.context.settlement_window_days for row in settlements],
    }
    for name, values in series.items():
        features[name + "_min"] = float(min(values, default=0))
        features[name + "_max"] = float(max(values, default=0))
        features[name + "_mean"] = float(sum(values) / len(values)) if values else 0.0
    for name in ("gross_delta", "fee_delta", "net_delta"):
        values = series[name]
        features[name + "_absolute_max"] = float(max((abs(value) for value in values), default=0))
        features[name + "_relative_min"] = float(min(values, default=zero) / amount_scale)
        features[name + "_relative_max"] = float(max(values, default=zero) / amount_scale)
        features[name + "_nonzero_count"] = sum(value != 0 for value in values)
    return features
