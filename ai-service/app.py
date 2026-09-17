"""P6 advisory exception classification; financial truth remains in P3."""
from contextlib import asynccontextmanager
import logging
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException

from classifier.prediction import ModelUnavailable, Predictor
from classifier.schema import Snapshot

logger = logging.getLogger(__name__)


def create_app(model_dir=None):
    directory = Path(model_dir or os.environ.get("FINRECON_MODEL_DIR", Path(__file__).parent / "artifacts/p6/models"))

    @asynccontextmanager
    async def lifespan(application):
        try:
            application.state.predictor = Predictor.load(directory)
        except ModelUnavailable:
            application.state.predictor = None
            logger.warning("Classifier unavailable; install a compatible local model artifact and restart")
        yield

    application = FastAPI(title="finrecon-ai-service", version="0.6.0", lifespan=lifespan)

    @application.get("/health")
    def health() -> dict:
        return {"status": "UP", "service": "ai-service"}

    @application.post("/classify")
    def classify(snapshot: Snapshot) -> dict:
        predictor = getattr(application.state, "predictor", None)
        if predictor is None:
            raise HTTPException(status_code=503, detail="Trained classifier unavailable")
        try:
            return predictor.predict(snapshot)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail="Trained classifier unavailable") from exc

    return application


app = create_app()
