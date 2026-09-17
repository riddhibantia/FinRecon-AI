"""Reject citations not in the trusted P7 corpus and evidence not in tool outputs."""
from datetime import date

from pydantic import ValidationError

from agent.schema import Citation, Evidence
from rag.documents import DEFAULT_POLICY_DIR, chunk_policy, load_corpus


class SourceVerifier:
    """Resolve only operator-configured corpus files, never a URI supplied by a hit."""
    def __init__(self, policy_dir=DEFAULT_POLICY_DIR):
        self.policy_dir = policy_dir

    def verify(self, hit):
        try:
            for policy in load_corpus(self.policy_dir):
                if policy.document_id != hit.get("document_id") or policy.version != hit.get("version"):
                    continue
                for chunk in chunk_policy(policy):
                    if chunk.chunk_id != hit.get("chunk_id") or chunk.text != hit.get("excerpt"):
                        continue
                    return all(hit.get(key) == chunk.metadata[key] for key in (
                        "document_id", "title", "version", "section", "page", "source_uri", "source_sha256",
                        "line_start", "line_end", "effective_from", "effective_to"))
        except (OSError, ValueError, TypeError, KeyError):
            return False
        return False


def verify_citations(candidates, retrieved, verifier, as_of=None):
    verified, errors = [], []
    for hit in candidates:
        try:
            citation = Citation(document=hit["document_id"], version=hit["version"], section=hit["section"],
                                page=hit["page"], excerpt=hit["excerpt"], score=hit["score"]).model_dump()
            valid_date = as_of is None or (date.fromisoformat(hit["effective_from"]) <= as_of
                and (hit.get("effective_to") is None or as_of <= date.fromisoformat(hit["effective_to"])))
            if hit not in retrieved or not valid_date or not verifier.verify(hit):
                raise ValueError("unverified citation")
            if citation not in verified:
                verified.append(citation)
        except (ValidationError, ValueError, KeyError, TypeError, AttributeError):
            errors.append("Unverifiable citation dropped: excerpt, metadata, effective date, or source did not match")
    return verified, errors


def verify_evidence(candidates, tool_evidence):
    verified, errors, seen = [], [], {}
    for candidate in candidates:
        try:
            row = Evidence.model_validate(candidate).model_dump()
            if row not in tool_evidence:
                raise ValueError("untraced evidence")
            key = (row["source_type"], row["source_id"], row["field"])
            values = (row["expected"], row["observed"])
            if key in seen and seen[key] != values:
                errors.append("Conflicting tool evidence for the same source record and field")
            seen[key] = values
            if row not in verified:
                verified.append(row)
        except (ValidationError, ValueError, TypeError):
            errors.append("Evidence dropped: field or value does not trace to a tool result")
    return verified, errors
