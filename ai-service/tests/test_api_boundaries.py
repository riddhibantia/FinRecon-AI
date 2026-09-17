"""P11 AI interaction boundary: the intelligence layer exposes read-only
advisory routes only. No PUT/DELETE/PATCH anywhere, POST limited to
/classify and /investigate, and every investigation contract carries the
human-approval flag. Runs under the OMP uv/Python 3.12 environment."""

from fastapi import FastAPI

import app as app_module
from agent import InvestigationRequest
from agent.schema import InvestigationResult


def _routes(application: FastAPI):
    return [(sorted(route.methods or []), route.path) for route in application.routes]


def test_no_mutating_http_methods():
    application = app_module.create_app()
    for methods, path in _routes(application):
        for method in methods:
            assert method in ("GET", "POST", "HEAD", "OPTIONS"), (method, path)


def test_post_routes_are_advisory_only():
    application = app_module.create_app()
    posts = sorted(path for methods, path in _routes(application) if "POST" in methods)
    assert posts == ["/classify", "/investigate"]


def test_public_contracts_cover_health_and_advisory():
    application = app_module.create_app()
    paths = {path for _, path in _routes(application)}
    assert "/health" in paths
    assert "/classify" in paths
    assert "/investigate" in paths


def test_investigation_request_uses_p4_naming():
    assert "exceptionId" in InvestigationRequest.model_fields
    assert "exception_id" not in InvestigationRequest.model_fields


def test_every_investigation_requires_human_approval():
    assert InvestigationResult.model_fields["human_approval_required"].is_required() is False
    assert InvestigationResult(exceptionId="x", root_cause=None, summary="s",
                               recommended_action=None, confidence=None,
                               confidence_reason=None, evidence=[], citations=[],
                               status="MANUAL_REVIEW", reasons=[],
                               draft=None).human_approval_required is True
