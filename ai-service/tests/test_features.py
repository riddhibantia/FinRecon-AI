from copy import deepcopy

import pytest


def case_payload():
    return {
        "category": "FEE_VARIANCE",
        "mismatchType": "FEE_VARIANCE",
        "amountDifference": "0.25",
        "sources": [
            {"sourceType": "payment_gateway", "recordId": "p1", "summary": "private"},
            {"sourceType": "ledger", "recordId": "l1", "summary": "private"},
            {"sourceType": "settlement", "recordId": "s1", "summary": "private"},
        ],
        "evidence": [{"fieldName": "fee_amount", "expectedValue": "1.00", "observedValue": "1.25"}],
    }


def test_case_features_capture_observed_counts_and_variance():
    from classifier.features import extract_case_features

    payload = case_payload()
    payload["sources"].append({"sourceType": "settlement", "recordId": "s2"})
    assert extract_case_features(payload) == {
        "payment_count": 1,
        "ledger_count": 1,
        "settlement_count": 2,
        "amount_difference": 0.25,
        "absolute_amount_difference": 0.25,
    }


def test_features_ignore_labels_identifiers_and_selected_evidence():
    from classifier.features import extract_case_features

    payload = case_payload()
    original = deepcopy(payload)
    expected = extract_case_features(payload)
    assert payload == original
    payload.update(category="UNKNOWN_EXCEPTION", mismatchType="UNKNOWN_EXCEPTION",
                   evidence=[], caseActions=[{"notes": "label leak"}], severity="HIGH")
    for source in payload["sources"]:
        source["recordId"] = "different"
        source["summary"] = "different"
    payload["sources"].reverse()
    assert extract_case_features(payload) == expected
    assert extract_case_features(payload) == extract_case_features(payload)


@pytest.mark.parametrize("value", [None, "NaN", "Infinity", "not-money", True])
def test_rejects_invalid_variance(value):
    from classifier.features import extract_case_features

    payload = case_payload()
    payload["amountDifference"] = value
    with pytest.raises(ValueError, match="amountDifference"):
        extract_case_features(payload)


def test_absent_sources_are_not_silently_treated_as_missing_settlement():
    from classifier.features import extract_case_features

    with pytest.raises(ValueError, match="sources"):
        extract_case_features({"amountDifference": "0"})


def test_unknown_source_type_is_rejected():
    from classifier.features import extract_case_features

    payload = case_payload()
    payload["sources"].append({"sourceType": "invented"})
    with pytest.raises(ValueError, match="sourceType"):
        extract_case_features(payload)
