"""Train LR first, benchmark XGBoost on the same frozen grouped split."""
import argparse
from collections import Counter
import importlib.metadata
import json
from pathlib import Path
import platform

import joblib
import numpy as np
from sklearn.feature_extraction import DictVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, f1_score, log_loss
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.utils.class_weight import compute_sample_weight
from xgboost import XGBClassifier

from classifier.dataset import digest, json_bytes
from classifier.features import extract_source_features
from classifier.schema import CLASSES, FEATURE_VERSION, Snapshot


def load_dataset(path):
    path = Path(path)
    manifest = json.loads(path.with_suffix(".manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != "p6-dataset-v1" or manifest.get("dataset_sha256") != digest(path):
        raise ValueError("dataset manifest or content hash mismatch")
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not rows or len(rows) != manifest["rows"]:
        raise ValueError("dataset row count mismatch")
    for row in rows:
        if row.get("label") not in CLASSES or not isinstance(row.get("group_id"), str) or not row["group_id"]:
            raise ValueError("invalid label or payment family")
        Snapshot.model_validate(row["snapshot"])
    return rows, manifest


def split_rows(rows):
    parts = {name: [] for name in ("train", "validation", "test")}
    group_splits, snapshot_splits = {}, {}
    for row in rows:
        split = row["split"]
        if split not in parts:
            raise ValueError("invalid split")
        for key, index in ((row["group_id"], group_splits), (json_bytes(row["snapshot"]), snapshot_splits)):
            if index.setdefault(key, split) != split:
                raise ValueError("related payment or identical snapshot crosses split boundary")
        parts[split].append(row)
    for name, part in parts.items():
        if set(row["label"] for row in part) != set(CLASSES):
            raise ValueError(f"{name} must contain every established taxonomy class")
    return parts


def features_and_labels(rows):
    return ([extract_source_features(row["snapshot"]) for row in rows],
            np.array([CLASSES.index(row["label"]) for row in rows]))


def score(model, features, labels):
    probabilities = model.predict_proba(features)
    predictions = probabilities.argmax(axis=1)
    report = classification_report(labels, predictions, labels=list(range(len(CLASSES))), target_names=CLASSES, output_dict=True, zero_division=0)
    return {
        "macro_f1": float(f1_score(labels, predictions, labels=list(range(len(CLASSES))), average="macro", zero_division=0)),
        "per_class": {category: report[category] for category in CLASSES},
        "confusion_matrix": confusion_matrix(labels, predictions, labels=list(range(len(CLASSES)))).tolist(),
        "confusion_order": list(CLASSES), "log_loss": float(log_loss(labels, probabilities, labels=list(range(len(CLASSES))))),
        "multiclass_brier": float(np.mean(np.sum((probabilities - np.eye(len(CLASSES))[labels]) ** 2, axis=1))),
        "rows": len(labels),
    }


def train(dataset, output):
    rows, lineage = load_dataset(dataset)
    parts = split_rows(rows)
    matrices = {name: features_and_labels(part) for name, part in parts.items()}
    train_x, train_y = matrices["train"]
    seed = lineage["seed"]
    candidates = {
        "logistic_regression": Pipeline([
            ("vectorizer", DictVectorizer(sparse=False, sort=True)),
            ("scaler", StandardScaler()),
            ("classifier", LogisticRegression(class_weight="balanced", max_iter=4000, C=1.0, random_state=seed)),
        ]),
        "xgboost": Pipeline([
            ("vectorizer", DictVectorizer(sparse=False, sort=True)),
            ("classifier", XGBClassifier(objective="multi:softprob", num_class=len(CLASSES), n_estimators=180,
                max_depth=4, learning_rate=0.08, subsample=1.0, colsample_bytree=1.0,
                tree_method="hist", n_jobs=1, random_state=seed, eval_metric="mlogloss")),
        ]),
    }
    validation = {}
    for name, model in candidates.items():
        fit_params = {} if name == "logistic_regression" else {"classifier__sample_weight": compute_sample_weight("balanced", train_y)}
        model.fit(train_x, train_y, **fit_params)
        validation[name] = score(model, *matrices["validation"])
    # LR wins ties; heldout labels never choose model or tune parameters.
    selected = max(candidates, key=lambda name: validation[name]["macro_f1"])
    heldout = {name: score(model, *matrices["test"]) for name, model in candidates.items()}
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    dependencies = {name: importlib.metadata.version(name) for name in ("scikit-learn", "xgboost", "numpy", "joblib", "pydantic")}
    specification = {
        "feature_version": FEATURE_VERSION, "dataset_sha256": lineage["dataset_sha256"],
        "seed": seed, "dependencies": dependencies,
        "features_sha256": digest(Path(__file__).with_name("features.py")), "training_sha256": digest(__file__),
    }
    import hashlib
    version = "p6-" + hashlib.sha256(json_bytes(specification)).hexdigest()[:16]
    bundles = {}
    for name, model in candidates.items():
        filename = f"{name}.joblib"
        joblib.dump(model, output / filename)
        bundles[name] = {"file": filename, "sha256": digest(output / filename)}
    manifest = {
        "format": "p6-model-v1", "model_version": version, "selected_model": selected,
        "classes": list(CLASSES), "feature_version": FEATURE_VERSION, "models": bundles,
        "training": specification, "python": platform.python_version(), "dataset_lineage": lineage,
        "selection": "highest validation macro F1, LR wins ties; no heldout selection",
        "supported_window_days": lineage["window_days"],
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    report = {
        "model_version": version, "selected_model": selected, "classes": list(CLASSES),
        "dataset_lineage": lineage, "validation": validation, "heldout": heldout,
        "splits": {name: {"rows": len(part), "groups": len({row["group_id"] for row in part}), "classes": dict(Counter(row["label"] for row in part))} for name, part in parts.items()},
        "leakage_checks": {"payment_groups_disjoint": True, "identical_snapshots_disjoint": True, "preprocessing_fit": "train only", "label_fields_in_features": False},
        "confidence_note": "Raw model probabilities, not calibrated operational certainty. Brier and log loss are synthetic heldout diagnostics.",
    }
    (output / "training_metrics.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=Path("artifacts/p6/dataset.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("artifacts/p6/models"))
    args = parser.parse_args()
    print(json.dumps(train(args.dataset, args.output), indent=2))
