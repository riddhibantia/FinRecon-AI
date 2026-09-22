"""P13 live end-to-end check: 5+ scenarios through the real stack.

Skipped unless FINRECON_E2E=1, because it needs PostgreSQL and the four
running services. Run:

    $env:FINRECON_E2E=1; python -m pytest tests/e2e -q; Remove-Item Env:FINRECON_E2E
"""
import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[2]

pytestmark = pytest.mark.skipif(
    os.environ.get("FINRECON_E2E") != "1",
    reason="live stack required; set FINRECON_E2E=1")


def test_demo_scenarios_pass_against_live_stack():
    scenarios = json.loads((REPO / "data" / "demo" / "scenarios.json").read_text(
        encoding="utf-8"))
    assert len(scenarios) >= 5
    completed = subprocess.run([sys.executable, str(REPO / "scripts" / "demo.py")],
                               capture_output=True, text=True, cwd=REPO)
    assert completed.returncode == 0, completed.stdout + completed.stderr
    assert completed.stdout.count("PASS") >= len(scenarios)
