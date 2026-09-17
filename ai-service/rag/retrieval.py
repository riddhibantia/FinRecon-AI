"""P8 entry point: retrieve verbatim policy evidence, never generate an answer."""
from dataclasses import dataclass, asdict
from datetime import date
import math
import os
from pathlib import Path

from rag.documents import DEFAULT_POLICY_DIR, chunk_policy, load_corpus
from rag.embeddings import LocalHashEmbeddings, EMBEDDING_VERSION
from rag.store import MemoryStore, PostgresStore

DEFAULT_K = 3
MIN_SCORE = 0.16


@dataclass(frozen=True)
class Citation:
    chunk_id: str
    policy_id: str
    document_id: str
    title: str
    version: str
    section: str
    page: int
    source_uri: str
    effective_from: str
    effective_to: str | None
    line_start: int
    line_end: int
    source_sha256: str
    synthetic: bool
    excerpt: str
    score: float

    def to_dict(self):
        return asdict(self)


@dataclass(frozen=True)
class SearchResult:
    query: str
    as_of: str
    status: str
    embedding_version: str
    hits: list[Citation]

    def to_dict(self):
        return asdict(self)


class PolicyRetriever:
    def __init__(self, store, embeddings=None, min_score=MIN_SCORE):
        if not math.isfinite(min_score) or not 0 < min_score <= 1:
            raise ValueError("min_score must be finite and in (0, 1]")
        self.store = store
        self.embeddings = embeddings or LocalHashEmbeddings()
        self.min_score = min_score

    def search(self, query: str, k: int = DEFAULT_K, as_of: date | None = None) -> SearchResult:
        if not isinstance(k, int) or isinstance(k, bool) or not 1 <= k <= 20:
            raise ValueError("k must be an integer between 1 and 20")
        if not isinstance(query, str) or len(query) > 4000:
            raise ValueError("query must be a string of at most 4000 characters")
        as_of = as_of or date.today()
        vector = self.embeddings.embed_query(query)
        candidates = self.store.search(vector, k, as_of) if any(vector) else []
        hits = []
        for chunk, score in candidates:
            if not math.isfinite(score) or score < self.min_score:
                continue
            metadata = chunk.metadata
            hits.append(Citation(chunk.chunk_id, chunk.policy_id, metadata["document_id"],
                                  metadata["title"], metadata["version"], metadata["section"],
                                  metadata["page"], metadata["source_uri"], metadata["effective_from"],
                                  metadata["effective_to"], metadata["line_start"], metadata["line_end"],
                                  metadata["source_sha256"], metadata["synthetic"], chunk.text, score))
        return SearchResult(query, as_of.isoformat(), "EVIDENCE_FOUND" if hits else "INSUFFICIENT_EVIDENCE",
                            EMBEDDING_VERSION, hits)


def build_retriever(policy_dir: Path = DEFAULT_POLICY_DIR, database_url: str | None = None) -> PolicyRetriever:
    """None reads DATABASE_URL; explicit empty string forces offline memory.

    PG search never ingests implicitly. Run `python -m rag ingest` first.
    """
    url = os.environ.get("DATABASE_URL", "") if database_url is None else database_url
    embeddings = LocalHashEmbeddings()
    if url:
        return PolicyRetriever(PostgresStore(url), embeddings)
    policies = load_corpus(policy_dir)
    chunks = [chunk for policy in policies for chunk in chunk_policy(policy)]
    return PolicyRetriever(MemoryStore(chunks, embeddings), embeddings)
