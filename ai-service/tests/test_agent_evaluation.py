"""P13: the agent evaluation set is fixed, offline, and scored honestly."""
import httpx

from agent.evaluate import CASES_PATH, evaluate, load_cases


def test_case_file_is_fixed_and_covers_both_outcomes():
    cases = load_cases(CASES_PATH)
    assert len(cases) >= 6
    statuses = {case["expect"]["status"] for case in cases}
    assert "HUMAN_REVIEW" in statuses
    # Top-level status is HUMAN_REVIEW or MANUAL_REVIEW only (schema line 70);
    # DRAFT lives at expect.draft.status, so both outcomes are covered there.
    assert "DRAFT" in {(case["expect"].get("draft") or {}).get("status") for case in cases}
    for case in cases:
        assert case["id"]
        assert case["expect"]["human_approval_required"] is True


def test_evaluate_scores_a_recorded_backend_without_network():
    cases = load_cases(CASES_PATH)

    def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError(f"network is forbidden: {request.url}")

    result = evaluate(
        cases,
        transports={case["id"]: httpx.MockTransport(handler) for case in cases})
    assert set(result) >= {"cases", "task_success_rate", "citation_correctness",
                           "manual_review_rate", "codes", "evaluated_at"}
    assert 0.0 <= result["task_success_rate"] <= 1.0
    assert len(result["cases"]) == len(cases)
