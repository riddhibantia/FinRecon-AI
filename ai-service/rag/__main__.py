"""Ingest synthetic policies or retrieve cited passages without an LLM."""
import argparse
from datetime import date
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path

from rag.documents import DEFAULT_POLICY_DIR, CHUNK_SIZE, OVERLAP, CHUNKER_VERSION, load_corpus, chunk_policy
from rag.embeddings import LocalHashEmbeddings, EMBEDDING_VERSION
from rag.retrieval import build_retriever
from rag.store import MemoryStore, PostgresStore


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy-dir", type=Path, default=DEFAULT_POLICY_DIR)
    parser.add_argument("--offline", action="store_true", help="Force memory backend even when DATABASE_URL is set")
    commands = parser.add_subparsers(dest="command", required=True)
    ingest = commands.add_parser("ingest")
    ingest.add_argument("--manifest", type=Path,
                        default=Path(__file__).resolve().parents[1] / "artifacts" / "p7" / "ingestion.json")
    search = commands.add_parser("search")
    search.add_argument("query")
    search.add_argument("--k", type=int, default=3)
    search.add_argument("--as-of", type=date.fromisoformat)
    args = parser.parse_args()
    url = "" if args.offline else os.environ.get("DATABASE_URL", "")
    if args.command == "search":
        result = build_retriever(args.policy_dir, database_url=url).search(args.query, k=args.k, as_of=args.as_of)
        print(json.dumps(result.to_dict(), indent=2))
        return
    policies = load_corpus(args.policy_dir)
    chunks = [chunk for policy in policies for chunk in chunk_policy(policy)]
    embeddings = LocalHashEmbeddings()
    if url:
        PostgresStore(url).ingest(policies, chunks, embeddings)
    else:
        MemoryStore(chunks, embeddings)
    report = {
        "backend": "postgres" if url else "memory",
        "persistent": bool(url), "documents": len(policies), "chunks": len(chunks),
        "embedding_version": EMBEDDING_VERSION, "chunker_version": CHUNKER_VERSION,
        "chunk_size": CHUNK_SIZE, "overlap": OVERLAP,
        "sources": [{"document_id": p.document_id, "policy_id": p.policy_id, "version": p.version,
                     "source_uri": p.source_uri, "sha256": p.source_sha256} for p in policies],
        "code_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in sorted(Path(__file__).parent.glob("*.py"))},
        "libraries": {name: importlib.metadata.version(name) for name in ("langchain-core", "numpy", "psycopg")},
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(args.manifest), **report}, indent=2))


if __name__ == "__main__":
    main()
