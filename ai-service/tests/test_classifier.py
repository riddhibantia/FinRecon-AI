from copy import deepcopy
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from classifier.schema import Snapshot
from classifier.features import extract_source_features
from classifier.dataset import generate_dataset, scenario_families
from classifier.training import train, load_dataset, split_rows
from classifier.prediction import Predictor, ModelUnavailable


def sample():
    return next(scenario_families(groups=1, seed=17))["snapshot"]


def test_source_features_capture_actual_differences_without_mutating():
    payload = sample()
    payload["ledgers"][0]["gross_amount"] = "110.00"
    payload["payment"]["amount"] = "100.00"
    payload["settlements"] = []
    original = deepcopy(payload)
    features = extract_source_features(Snapshot.model_validate(payload))
    assert features["gross_delta_min"] == 10.0
    assert features["settlement_count"] == 0
    assert features["settled_total"] == 0.0
    assert payload == original


def test_source_order_and_status_whitespace_do_not_change_features():
    payload = sample()
    payload["settlements"] *= 2
    expected = extract_source_features(Snapshot.model_validate(payload))
    payload["settlements"].reverse()
    payload["payment"]["status"] = "  " + payload["payment"]["status"].lower() + "  "
    assert extract_source_features(Snapshot.model_validate(payload)) == expected


@pytest.mark.parametrize("bad", [True, "NaN", "Infinity", "1.001", "-1", "10000000000000000"])
def test_invalid_money_is_rejected(bad):
    payload = sample()
    payload["payment"]["amount"] = bad
    with pytest.raises(ValidationError):
        Snapshot.model_validate(payload)


def test_missing_sources_labels_and_naive_time_are_rejected():
    for change in ({"category": "FEE_VARIANCE"}, {"ledgers": None}):
        with pytest.raises(ValidationError):
            Snapshot.model_validate(sample() | change)
    payload = sample()
    payload["payment"]["event_time"] = "2026-09-01T10:00:00"
    with pytest.raises(ValidationError):
        Snapshot.model_validate(payload)


@pytest.fixture(scope="module")
def trained(tmp_path_factory):
    root = tmp_path_factory.mktemp("classifier")
    dataset = root / "dataset.jsonl"
    generate_dataset(dataset, groups=40, seed=17)
    report = train(dataset, root / "models")
    return dataset, root / "models", report


def test_group_split_has_no_related_payment_leakage(trained):
    rows, _ = load_dataset(trained[0])
    parts = split_rows(rows)
    groups = [{row["group_id"] for row in part} for part in parts.values()]
    assert not groups[0] & groups[1]
    assert not groups[0] & groups[2]
    assert not groups[1] & groups[2]
    assert sum(map(len, parts.values())) == len(rows)
    assert all(set(row["label"] for row in part) == set(trained[2]["classes"]) for part in parts.values())


def test_saved_prediction_and_probabilities_are_repeatable(trained):
    predictor = Predictor.load(trained[1])
    rows, _ = load_dataset(trained[0])
    payload = Snapshot.model_validate(rows[0]["snapshot"])
    first = predictor.predict(payload)
    assert Predictor.load(trained[1]).predict(payload) == first
    assert first["status"] == "ADVISORY_ONLY"
    assert set(first["probabilities"]) == set(trained[2]["classes"])
    assert sum(first["probabilities"].values()) == pytest.approx(1.0)
    assert first["confidence"] == max(first["probabilities"].values())


def test_known_heldout_missing_and_duplicate_examples(trained):
    predictor = Predictor.load(trained[1])
    rows, _ = load_dataset(trained[0])
    test = split_rows(rows)["test"]
    for category in ("MISSING_SETTLEMENT", "DUPLICATE_SETTLEMENT"):
        example = next(row for row in test if row["label"] == category)
        assert predictor.predict(Snapshot.model_validate(example["snapshot"]))["category"] == category


def test_unseen_source_status_is_supported_by_fitted_preprocessing(trained):
    predictor = Predictor.load(trained[1])
    payload = sample()
    payload["payment"]["status"] = "NEW_LIFECYCLE_STATE"
    result = predictor.predict(Snapshot.model_validate(payload))
    assert all(np.isfinite(list(result["probabilities"].values())))
    assert sum(result["probabilities"].values()) == pytest.approx(1)


def test_api_unavailable_invalid_and_prediction(trained, tmp_path):
    from app import create_app

    with TestClient(create_app(tmp_path / "absent")) as client:
        assert client.get("/health").json() == {"status": "UP", "service": "ai-service"}
        assert client.post("/classify", json=sample()).status_code == 503
        assert client.post("/classify", json={}).status_code == 422
    with TestClient(create_app(trained[1])) as client:
        response = client.post("/classify", json=sample())
        assert response.status_code == 200
        assert response.json()["status"] == "ADVISORY_ONLY"
        assert response.json()["modelVersion"]


def test_untrusted_or_incomplete_model_bundle_is_unavailable(tmp_path):
    with pytest.raises(ModelUnavailable):
        Predictor.load(tmp_path)
    (tmp_path / "manifest.json").write_text("{}", encoding="utf-8")
    with pytest.raises(ModelUnavailable):
        Predictor.load(tmp_path)
