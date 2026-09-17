# P7 — cited policy retrieval

P7 retrieves verbatim policy excerpts. It does not generate answers, change cases, execute adjustments or establish financial truth. The eight Markdown documents under `ai-service/data/policies/` are synthetic: reconciliation SOP, settlement timing, merchant fees, FX references, merchant agreement, exception procedures, escalation matrix and data dictionary. Their demonstration rates and thresholds are not real merchant terms or implemented database fee rules.

## Architecture

`Markdown → strict metadata parser → section/page chunks → LangChain Embeddings → memory or PostgreSQL/pgvector → cited excerpts`

- Required front matter: document ID, title, version, effective-from/to dates and `synthetic: true`. Dates use ISO format; an open-ended effective date uses literal `null`. Missing, duplicate or malformed metadata fails ingestion instead of inventing a citation.
- Explicit `<!-- page: N -->` markers describe synthetic logical pages, not PDF pagination. Markdown headings identify sections. Each chunk stays within its section and page; excerpt bytes after newline normalization remain an exact source substring.
- Maximum 180 whitespace-delimited tokens, with 30-token overlap only within a section. This is a token-ish word window, not a model tokenizer. Short sections produce one chunk. Location includes section, logical page, source line range and token offsets.
- Stable UUIDv5 policy IDs derive from document ID plus version. Chunk IDs also include location and excerpt. Source files, embedding/chunker versions and ingestion code have SHA-256 hashes in the ingestion manifest. Hashes provide lineage, not protection against an operator replacing both content and hashes.
- `LocalHashEmbeddings` implements LangChain's `Embeddings` interface from `langchain-core`. It uses SHA-256 signed token counts in 1,536 dimensions and L2 normalization. Title and section join the excerpt for embedding; citations return only source text. Python's randomized `hash()` is not used.
- No pretrained offline embedding weights are supplied by this repository. The explicit deterministic fallback avoids downloading weights or calling an API at runtime. It is lexical hashing, **not learned semantic understanding**. Synonyms, negation, multilingual queries and hash collisions can fail. There is no reranker, BM25 blend, LLM, embedding service, model training or network access on the offline path.
- Retrieval ranks exact cosine similarity, default top-k 3 (allowed 1–20). Similarity below 0.16 is discarded. This fixed engineering threshold is not a calibrated confidence probability. A nonempty result means `EVIDENCE_FOUND`, not a safe recommendation. Empty/stop-word-only and weak queries return `INSUFFICIENT_EVIDENCE`. Irrelevant queries with overlapping vocabulary can still pass the threshold.
- Effective dates are inclusive. Pass the payment's event date as `as_of`; omitting it uses today's date. No merchant filter is inferred from prose: the caller must check agreement scope and conflicting versions before recommending an action.

## Install and run

From `ai-service` with Python 3.12:

```text
python -m pip install -r requirements.txt
python -m rag --offline ingest
python -m rag --offline search "What is the standard domestic processing fee?" --as-of 2026-06-01
python -m rag.evaluate
python -m pytest tests/test_rag.py -v
```

Added packages: `langchain-core==0.3.83`, `psycopg[binary]==3.2.10`. NumPy was already required by P6. PostgreSQL's vector extension comes from V4, not a new Python package. No external API key is required.

On this Windows workstation, from the repository root, the equivalent isolated uv commands are:

```text
cd ai-service
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m rag --offline ingest
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m rag --offline search "What is the standard domestic processing fee?" --as-of 2026-06-01
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m rag.evaluate
py -3.14 -m uv run --python 3.12 --no-project --with-requirements requirements.txt python -m pytest tests/test_rag.py -v
```

`--policy-dir PATH` selects a different corpus; it precedes the `ingest`/`search` subcommand. Offline ingestion builds the vectors and writes `ai-service/artifacts/p7/ingestion.json`; vectors do not persist across processes. Offline search rebuilds the small corpus in memory. The manifest says `persistent: false` rather than implying a durable index.

## PostgreSQL path

Apply the existing V1–V4 migrations using the documented database setup, then set `DATABASE_URL` to a psycopg-compatible `postgresql://user:password@host:5432/database` connection string. It is not a JDBC URL or SQLAlchemy `postgresql+psycopg://` URL. Credentials are read from the environment and never written into the manifest.

```text
python -m rag ingest
python -m rag search "duplicate settlement investigation" --as-of 2026-06-01
```

When `DATABASE_URL` is present, ingestion writes `policies` and `policy_chunks.embedding` using psycopg and parameterized SQL. Each policy is upserted; only P7-owned chunks for the ingested policy IDs are replaced in one transaction. Repeated ingestion is idempotent. Other writers' chunks are not removed. Removed documents are not implicitly deleted from the database; expire their policy dates through the database ownership workflow. Distinct document IDs reusing the same title/version fail the existing database uniqueness constraint rather than silently merging identities.

PostgreSQL retrieval uses pgvector `<=>` cosine distance and filters by embedding version, writer and effective dates. NULL embeddings and other embedding spaces are excluded. Exact search is intentional for this small corpus; no approximate index is added. Run ingestion explicitly before PG search: startup never mutates the database. A database connection/schema failure propagates; the service must not silently switch to stale local evidence. `--offline` always forces memory even when the environment has a database URL.

Live storage validation passed on a disposable PostgreSQL 16.2 server with pgvector 0.6.2 supplied by `pgserver==0.1.4` (validation tooling only, not a runtime dependency). The existing `PostgresStore` ingested 8 policies and 24 non-NULL embeddings. Repeating ingestion retained exactly 8 policies/24 chunks. The fixed 12-question evaluation measured Recall@3 = 1.0 and MRR@3 = 1.0; citations verified and all three unrelated/stop-word queries abstained. Full measured output: `ai-service/evaluation/p7_postgres_metrics.json`. The scratch server was stopped after validation.

Migration limitation: the packaged server lacks `pgcrypto`. V1 therefore failed unchanged. For this storage experiment only, its `CREATE EXTENSION IF NOT EXISTS pgcrypto` statement was omitted from the SQL in memory; PostgreSQL 16's built-in `gen_random_uuid()` supplied UUID generation. V1–V4 table definitions were otherwise applied unchanged. Repository migrations were not edited. This proves the RAG PostgreSQL storage/retrieval path on the scratch schema, **not** unmodified migration installation, production permissions, Docker deployment, or full backend integration.

## P8 API

```python
from datetime import date
from rag import build_retriever

retriever = build_retriever(database_url="")  # explicit offline; None honors DATABASE_URL
result = retriever.search(
    "What is the standard domestic processing fee?",
    k=3,
    as_of=date(2026, 6, 1),
)
search_policy_output = result.to_dict()
```

`build_retriever(policy_dir=DEFAULT_POLICY_DIR, database_url=None) -> PolicyRetriever` builds once for reuse. `PolicyRetriever.search(query: str, k: int = 3, as_of: date | None = None) -> SearchResult` is the P8 `search_policy` tool boundary; no HTTP route or agent code is added in P7. Queries are limited to 4,000 characters. Invalid limits raise `ValueError`.

Result keys: `query`, `as_of`, `status`, `embedding_version`, `hits`. Each hit contains `chunk_id`, `policy_id` (database UUID), `document_id` (human-readable ID), `title`, `version`, `section`, `page`, `source_uri`, `effective_from`, `effective_to`, `line_start`, `line_end`, `source_sha256`, `synthetic`, `excerpt`, `score`. The excerpt is verbatim, not model-written text. `source_uri` is a local file URI; it identifies the ingestion source, not a deployed public download endpoint. Moving source files requires reingestion to refresh citations.

P8 must treat document text as evidence, not executable instructions. It must retain citation IDs and exact excerpts, verify that retrieved terms apply to the payment, and return manual review when evidence conflicts or is insufficient. P3 still compares observed fees and currency equality only; P7 does not add a contracted-fee calculator or historical FX reference store.

## Evaluation

`python -m rag.evaluate` always uses the offline store, even when `DATABASE_URL` is set. It saves measured results to `ai-service/evaluation/p7_metrics.json`, including question/source/code hashes, dependency versions, per-question returned passages and scores. The fixed corpus and 12 question/expected-passage pairs were authored together; this is a reproducible synthetic retrieval check, not a heldout benchmark or real-world accuracy estimate.

A relevant hit must contain **both** the expected document ID and the expected literal passage. Recall@K is the fraction of questions with that hit in the returned top K. MRR@K averages the reciprocal first relevant rank, with zero for a miss. Three unrelated/stop-word queries separately check abstention; every returned citation is checked against the source corpus. The evaluation date is fixed at 2026-06-01.

Measured on the in-memory backend: **8 documents, 24 chunks, 12 questions, Recall@3 = 1.0, MRR@3 = 1.0**. Every expected document and passage ranked first. All returned citations matched the source corpus. All three unrelated/stop-word queries returned zero chunks and `INSUFFICIENT_EVIDENCE`. Main ran the evaluation and the RAG test file: **11 tests passed**. These perfect scores reflect a small, jointly authored synthetic corpus and question set; they are not evidence of real policy-search accuracy.

Tests cover long-section boundaries and overlap, metadata retention, deterministic embeddings, fixed-question passage relevance, abstention, inclusive effective dates, invalid inputs and source-exact citations. They make no calls to an external model or live database.
