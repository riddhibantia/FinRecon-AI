"""Load only locally trusted training artifacts. No training in inference."""
import importlib.metadata
import json
import logging
from pathlib import Path
from typing import Any

import joblib
import numpy as np

from classifier.dataset import digest
from classifier.features import extract_source_features
from classifier.schema import CLASSES, FEATURE_VERSION, Snapshot

logger = logging.getLogger(__name__)


class ModelUnavailable(RuntimeError):
    pass


class Predictor:
    def __init__(self, model: Any, manifest: dict[str, Any]) -> None:
        self.model = model
        self.manifest = manifest

    @classmethod
    def load(cls, directory: str | Path) -> "Predictor":
        directory = Path(directory).resolve()
        try:
            manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
            try:
                if not isinstance(manifest, dict):
                    raise ValueError("incompatible artifact contract")
                for key in ("format", "feature_version", "classes", "model_version",
                            "training", "models", "selected_model"):
                    if key not in manifest:
                        raise ValueError("incompatible artifact contract")
                if (manifest["format"] != "p6-model-v1" or manifest["feature_version"] != FEATURE_VERSION
                        or manifest["classes"] != list(CLASSES) or not manifest["model_version"]):
                    raise ValueError("incompatible artifact contract")
                training = manifest["training"]
                if not isinstance(training, dict) or "dependencies" not in training or "features_sha256" not in training:
                    raise ValueError("incompatible artifact contract")
                for name, version in training["dependencies"].items():
                    if importlib.metadata.version(name) != version:
                        raise ValueError(f"artifact dependency mismatch: {name}")
                if training["features_sha256"] != digest(Path(__file__).with_name("features.py")):
                    raise ValueError("feature implementation changed; retrain before serving")
                models = manifest["models"]
                selected = manifest["selected_model"]
                if not isinstance(models, dict) or selected not in models:
                    raise ValueError("incompatible artifact contract")
                spec = models[selected]
                if not isinstance(spec, dict) or "file" not in spec or "sha256" not in spec:
                    raise ValueError("artifact path or checksum mismatch")
                path = (directory / spec["file"]).resolve()
                if path.parent != directory or digest(path) != spec["sha256"]:
                    raise ValueError("artifact path or checksum mismatch")
                # joblib is executable serialization: directory must be operator-controlled.
                model = joblib.load(path)
                if list(model.classes_) != list(range(len(CLASSES))):
                    raise ValueError("model classes differ from manifest")
                return cls(model, manifest)
            except (KeyError, TypeError, AttributeError) as exc:
                raise ValueError("incompatible artifact contract") from exc
        except (OSError, ValueError, RuntimeError) as exc:
            logger.warning("Classifier artifact load failed (%s): %s", type(exc).__name__, exc)
            raise ModelUnavailable("trained classifier artifact is unavailable or incompatible") from exc

    def predict(self, snapshot: Snapshot) -> dict[str, Any]:
        if snapshot.context.settlement_window_days not in self.manifest["supported_window_days"]:
            raise ValueError("settlement window is outside this model's training context")
        probabilities = np.asarray(self.model.predict_proba([extract_source_features(snapshot)])[0], dtype=float)
        if (probabilities.shape != (len(CLASSES),) or not np.isfinite(probabilities).all()
                or (probabilities < 0).any() or not np.isclose(probabilities.sum(), 1, atol=1e-5)):
            raise ModelUnavailable("classifier returned invalid probabilities")
        probabilities /= probabilities.sum()
        winner = int(probabilities.argmax())
        return {
            "category": CLASSES[winner], "confidence": float(probabilities[winner]),
            "probabilities": {name: float(value) for name, value in zip(CLASSES, probabilities, strict=True)},
            "modelVersion": self.manifest["model_version"], "status": "ADVISORY_ONLY",
        }
