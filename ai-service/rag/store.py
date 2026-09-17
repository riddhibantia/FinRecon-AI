"""Cosine search over one embedding version; PostgreSQL uses existing V4 tables."""
from datetime import date
import json

import numpy as np

from rag.documents import Chunk
from rag.embeddings import EMBEDDING_VERSION, embedding_text


def active(metadata, as_of):
    return (metadata["effective_from"] <= as_of.isoformat()
            and (metadata["effective_to"] is None or metadata["effective_to"] >= as_of.isoformat()))


class MemoryStore:
    def __init__(self, chunks, embeddings):
        self.chunks = list(chunks)
        self.vectors = np.asarray(embeddings.embed_documents([embedding_text(c) for c in self.chunks]), dtype=float)

    def search(self, vector, k, as_of):
        if not self.chunks:
            return []
        scores = self.vectors @ np.asarray(vector)
        candidates = [(chunk, float(score)) for chunk, score in zip(self.chunks, scores)
                      if active(chunk.metadata, as_of)]
        return sorted(candidates, key=lambda pair: (-pair[1], pair[0].chunk_id))[:k]


class PostgresStore:
    """Exact pgvector cosine search; no schema creation or hidden fallback."""

    def __init__(self, database_url):
        self.database_url = database_url

    def connect(self):
        import psycopg
        return psycopg.connect(self.database_url)

    def ingest(self, policies, chunks, embeddings):
        from psycopg.types.json import Jsonb
        vectors = embeddings.embed_documents([embedding_text(c) for c in chunks])
        with self.connect() as conn:
            with conn.cursor() as cursor:
                for policy in policies:
                    cursor.execute(
                        """INSERT INTO policies (policy_id,title,version,effective_from,effective_to,source_uri)
                        VALUES (%s,%s,%s,%s,%s,%s)
                        ON CONFLICT (policy_id) DO UPDATE SET title=EXCLUDED.title,
                        version=EXCLUDED.version,effective_from=EXCLUDED.effective_from,
                        effective_to=EXCLUDED.effective_to,source_uri=EXCLUDED.source_uri""",
                        (policy.policy_id, policy.title, policy.version, policy.effective_from,
                         policy.effective_to, policy.source_uri))
                    # Only replace chunks owned by this ingestion format, not other writers.
                    cursor.execute("DELETE FROM policy_chunks WHERE policy_id=%s AND metadata->>'writer'=%s",
                                   (policy.policy_id, "finrecon-rag-p7"))
                for chunk, vector in zip(chunks, vectors):
                    metadata = {**chunk.metadata, "embedding_version": EMBEDDING_VERSION, "writer": "finrecon-rag-p7"}
                    cursor.execute(
                        "INSERT INTO policy_chunks (chunk_id,policy_id,chunk_text,metadata,embedding) VALUES (%s,%s,%s,%s,%s::vector)",
                        (chunk.chunk_id, chunk.policy_id, chunk.text, Jsonb(metadata), json.dumps(vector)))
        return len(chunks)

    def search(self, vector, k, as_of: date):
        with self.connect() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    """SELECT c.chunk_id,c.policy_id,c.chunk_text,c.metadata,
                              1 - (c.embedding <=> %s::vector) AS score
                       FROM policy_chunks c JOIN policies p ON p.policy_id=c.policy_id
                       WHERE c.embedding IS NOT NULL
                         AND c.metadata->>'embedding_version'=%s
                         AND c.metadata->>'writer'=%s
                         AND p.effective_from <= %s
                         AND (p.effective_to IS NULL OR p.effective_to >= %s)
                       ORDER BY c.embedding <=> %s::vector,c.chunk_id LIMIT %s""",
                    (json.dumps(vector), EMBEDDING_VERSION, "finrecon-rag-p7", as_of, as_of,
                     json.dumps(vector), k))
                return [(Chunk(str(row[0]), str(row[1]), row[2], row[3]), float(row[4]))
                        for row in cursor.fetchall()]
