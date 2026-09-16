"""P0 contract test: ai-service health endpoint answers UP."""

from fastapi.testclient import TestClient

from app import app

client = TestClient(app)


def test_health_returns_up():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "ai-service"}
