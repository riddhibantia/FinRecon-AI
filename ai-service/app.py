"""FinRecon AI - ai-service P0 skeleton.

FastAPI with a health endpoint only.
No ML. No RAG. No LangGraph. Those arrive in P6-P8.
"""

from fastapi import FastAPI

app = FastAPI(title="finrecon-ai-service", version="0.0.0-p0")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "ai-service"}
