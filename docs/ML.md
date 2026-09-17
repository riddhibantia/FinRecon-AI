# P6 — advisory exception classification

Python 3.12, scikit-learn Logistic Regression and XGBoost. Financial truth and labels come from the unchanged P3 `ReconciliationEngine.reconcile`; predictions do not change cases or source records. No fraud classifier or LLM.

## Reproduce

From `ai-service`, using its Python environment:

```text
python -m pip install -r requirements.txt
python -m classifier.dataset --groups 200 --seed 17
python -m classifier.training
python -m pytest tests -v
python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Dataset generation requires Java 21, javac, and the existing Spring bootJar at `services/reconciliation-service/build/libs/reconciliation-service.jar`. Build it from the repository root with `gradle :services:reconciliation-service:bootJar` if absent. The Python-owned `P3LabelBridge.java` invokes the actual compiled engine; no Python copy of reconciliation rules assigns labels.

Artifacts are generated under ignored `ai-service/artifacts/p6/`. Model manifests record dataset, engine source, bootJar, bridge, generator, feature and training hashes; library versions; seed; rule version; classes; and supported settlement windows. Load only operator-controlled joblib files: checksums detect corruption, not a malicious operator replacing both artifact and manifest. Retrain after feature or dependency changes. `FINRECON_MODEL_DIR` selects a model directory; startup never trains.

## Dataset and split

Measured seed-17 dataset: 200 independent synthetic payment families, 3,000 discrepant examples, 200 matching examples excluded. Each family includes isolated and combined faults. All variants of a payment remain in one partition: 140 families / 2,100 rows training; 30 / 450 validation; 30 / 450 heldout test. Preprocessing fits training data only. Validation macro F1 chooses the model, with Logistic Regression winning ties. Test labels never select a model. Identical snapshots crossing partitions are rejected.

Labels use all nine existing categories: AMOUNT_MISMATCH, DUPLICATE_SETTLEMENT, FEE_VARIANCE, FX_VARIANCE, LATE_SETTLEMENT, MISSING_SETTLEMENT, PARTIAL_SETTLEMENT, STATUS_MISMATCH, UNKNOWN_EXCEPTION. Labels are P3 outputs, not independent analyst corrections. These measurements demonstrate learning on synthetic engine scenarios, not operational utility beyond P3, real financial accuracy, or unseen-fault generalization.

## Input and features

`POST /classify` consumes a structured `Snapshot`: `payment`, `ledgers`, `settlements`, `context`. Snake-case source fields match canonical P1 records; see `classifier/schema.py` for the exact validated schema. Money is finite, nonnegative, at most two decimal places within NUMERIC(18,2); timestamps require timezone information. Source lists must be explicit. Empty lists represent absence, not missing request fields. The window must match the training context (2 days for current P3).

Features: source counts; gateway status/currency; normalized status sets; currency and status disagreements; gross/fee/net/settled aggregates; signed, absolute and relative deltas; posting lag; settlement date lag and lateness. Settlement dates retain day precision. No merchant identities, labels, mismatch type, rule version, notes, action history or category-selected evidence fields enter training. DictVectorizer and StandardScaler provide deterministic LR preprocessing; XGBoost uses the same feature extraction. LR uses balanced class weights; XGBoost uses balanced sample weights.

The separate P4 `extract_case_features` adapter only reads counts and amountDifference. P4 display summaries are not parsed. P4 selects evidence fields based on category, so those fields would leak the target. The rich predictor therefore requires canonical source snapshots, not an unmodified CaseDetail response. No backend endpoints or migrations changed.

Contract boundary: the model is trained on P3 MISMATCHED outcomes only and assumes exception evidence. Freshly P3-labeled heldout rows match the saved dataset exactly (0/450 mismatches), so the measured heldout metrics reflect the live engine, not stale labels. On out-of-contract MATCHED inputs the raw model answers AMOUNT_MISMATCH/LATE_SETTLEMENT; matched payments are therefore filtered upstream by P3 before `/classify`, never by this service.

Response fields: `category`, `confidence`, `probabilities` (all nine classes), `modelVersion`, `status: ADVISORY_ONLY`. Invalid requests return 422; absent/incompatible models return 503. Probabilities are raw model outputs, not calibrated financial certainty. Existing `GET /health` remains unchanged.

## Actual measured results

Full machine-readable report: `ai-service/evaluation/p6_metrics.json`.

| Model | Validation macro F1 | Heldout macro F1 | Heldout log loss | Multiclass Brier |
|---|---:|---:|---:|---:|
| Logistic Regression (selected) | 1.0 | 1.0 | 0.008936 | 0.000427 |
| XGBoost | 1.0 | 1.0 | 0.002408 | 0.00000910 |

All nine heldout classes had precision/recall 1.0 on these deliberately simple synthetic cases. The confusion matrix is diagonal, with class support in the report's order: 90, 60, 60, 60, 30, 60, 30, 30, 30. Perfect scores on this generator are not evidence of real-world accuracy; both partitions share mutation templates. Independent labeled operational cases and out-of-template evaluation are needed before deployment claims.

Tests cover numeric validation, missing sources, order invariance, target exclusion, grouped split rejection, actual engine-generated training, artifact persistence, repeatable probabilities, known heldout cases, unknown statuses, API errors, and incompatible artifact rejection. No live PostgreSQL or Kafka validation is claimed.
