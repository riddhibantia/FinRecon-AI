"""Advisory classification and investigation; financial truth remains in P3."""
from contextlib import asynccontextmanager
import logging
import os
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
import httpx

from agent import InvestigationRequest, Investigator
from agent.tools import CaseTransport, ToolRegistry
from rag import build_retriever

from classifier.prediction import ModelUnavailable, Predictor
from classifier.schema import Snapshot

logger = logging.getLogger(__name__)


def create_app(model_dir=None, investigator=None) -> FastAPI:
    directory = Path(model_dir or os.environ.get("FINRECON_MODEL_DIR", Path(__file__).parent / "artifacts/p6/models"))

    @asynccontextmanager
    async def lifespan(application):
        try:
            application.state.predictor = Predictor.load(directory)
        except ModelUnavailable:
            application.state.predictor = None
            logger.warning("Classifier unavailable; install a compatible local model artifact and restart")
        yield

    application = FastAPI(title="finrecon-ai-service", version="0.8.0", lifespan=lifespan)

    @application.get("/health", response_model=None)
    def health() -> dict[str, Any]:
        return {"status": "UP", "service": "ai-service"}

    @application.post("/classify", response_model=None)
    def classify(snapshot: Snapshot) -> dict[str, Any]:
        predictor = getattr(application.state, "predictor", None)
        if predictor is None:
            raise HTTPException(status_code=503, detail="Trained classifier unavailable")
        try:
            return predictor.predict(snapshot)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail="Trained classifier unavailable") from exc

    @application.post("/investigate", response_model=None)
    def investigate(request: InvestigationRequest) -> dict[str, Any]:
        if investigator is not None:
            return investigator.investigate(request)
        predictor = getattr(application.state, "predictor", None)
        if predictor is None:
            raise HTTPException(status_code=503, detail="Trained classifier unavailable")
        base_url = os.environ.get("FINRECON_API_URL")
        if not base_url:
            raise HTTPException(status_code=503, detail="Case API is not configured")
        try:
            # P4 exposes display records, not P6 snapshots; confidence stays null
            # until an operator wires a source snapshot adapter through Investigator.
            limits = httpx.Limits(max_connections=20, max_keepalive_connections=5)
            with httpx.Client(base_url=base_url, timeout=10.0, limits=limits) as client:
                tools = ToolRegistry(CaseTransport(client), build_retriever(database_url=""))
                return Investigator(tools, predictor).investigate(request)
        except (OSError, ValueError, httpx.HTTPError, KeyError, TypeError) as exc:
            raise HTTPException(status_code=503, detail="Investigator dependencies unavailable") from exc

    return application


app = create_app()
