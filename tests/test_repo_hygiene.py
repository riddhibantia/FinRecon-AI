"""P11 repo hygiene: no secrets or card-like data in tracked files.

Scans `git ls-files` (tracked source only, so .venv/node_modules/.next
never participate). Card candidates must pass the Luhn check, which keeps
float tails and numeric constants out. Run from the repository root:
`python -m pytest tests -q`.
"""

import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

CARD_RUN = re.compile(r"\d{13,19}")
PRIVATE_KEY = re.compile(r"-----BEGIN .*PRIVATE KEY")
PASSWORD_LINE = re.compile(r"^\s*[\w.]*password[\w.]*\s*[:=]\s*(.+?)\s*$",
                            re.IGNORECASE)
LOCAL_DEFAULTS = {"changeme", ""}


def _tracked_text_files():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                         cwd=REPO, check=True).stdout.split()
    texts = []
    for name in out:
        path = REPO / name
        if not path.is_file():
            continue
        try:
            texts.append((name, path.read_text(encoding="utf-8")))
        except UnicodeDecodeError:
            raise AssertionError(f"Non-UTF8 tracked file cannot be scanned: {name}")
    assert texts, "no tracked files found"
    return texts


def _luhn_valid(digits: str) -> bool:
    total = 0
    for i, char in enumerate(reversed(digits)):
        digit = ord(char) - 48
        if i % 2 == 1:
            digit *= 2
            if digit > 9:
                digit -= 9
        total += digit
    return total % 10 == 0


def _card_candidates(text: str):
    # A decimal fraction tail is not a card number: ignore digit runs that
    # touch a dot on either side.
    for match in CARD_RUN.finditer(text):
        start, end = match.span()
        before = text[start - 1] if start > 0 else " "
        after = text[end] if end < len(text) else " "
        if before == "." or after == ".":
            continue
        yield match.group(0)


def test_no_private_keys_tracked():
    offenders = [name for name, text in _tracked_text_files()
                 if PRIVATE_KEY.search(text)]
    assert offenders == [], offenders


def test_no_card_like_numbers_tracked():
    offenders = []
    for name, text in _tracked_text_files():
        bad = [run for run in _card_candidates(text) if _luhn_valid(run)]
        if bad:
            offenders.append((name, bad[:3]))
    assert offenders == [], offenders


def test_no_env_file_tracked():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                         cwd=REPO, check=True).stdout.split()
    assert ".env" not in out
    assert not any(name.endswith("/.env") for name in out)


def test_password_values_are_placeholders_or_local_defaults():
    offenders = []
    for name, text in _tracked_text_files():
        if not name.endswith((".properties", ".yml", ".yaml")):
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            match = PASSWORD_LINE.match(line)
            if not match:
                continue
            value = match.group(1).strip().strip("'\"")
            if "${" in value or value in LOCAL_DEFAULTS:
                continue
            offenders.append(f"{name}:{lineno}:{line.strip()[:80]}")
    assert offenders == [], offenders
