"""Measure fixed synthetic document+passage retrieval, not real-world accuracy."""
import argparse
from datetime import date
import hashlib
import importlib.metadata
import json
from pathlib import Path

from rag.documents import DEFAULT_POLICY_DIR, CHUNK_SIZE, OVERLAP, CHUNKER_VERSION, load_corpus, chunk_policy
from rag.embeddings import EMBEDDING_VERSION
from rag.retrieval import DEFAULT_K, MIN_SCORE, build_retriever

QUESTION_PATH = Path(__file__).with_name("questions.json")
OUTPUT_PATH = Path(__file__).resolve().parents[1] / "evaluation" / "p7_metrics.json"
EVALUATION_DATE = date(2026, 6, 1)
IRRELEVANT_QUERIES = ["How do astronomers measure quasar redshift?", "What ingredients belong in sourdough bread?", "the and of"]


def evaluate(retriever=None, k=DEFAULT_K, policy_dir=DEFAULT_POLICY_DIR):
    retriever = retriever or build_retriever(policy_dir=policy_dir, database_url="")
    policies = load_corpus(policy_dir)
    corpus = {p.policy_id: p for p in policies}
    questions = json.loads(QUESTION_PATH.read_text(encoding="utf-8"))
    rows = []
    citations_verified = True
    for question in questions:
        result = retriever.search(question["query"], k=k, as_of=EVALUATION_DATE)
        rank = next((index for index, hit in enumerate(result.hits, 1)
                     if hit.document_id == question["document_id"] and question["passage"] in hit.excerpt), None)
        for hit in result.hits:
            policy = corpus.get(hit.policy_id)
            citations_verified = citations_verified and policy is not None
            if policy is not None:
                citations_verified = citations_verified and (
                    hit.excerpt in policy.content and hit.source_sha256 == policy.source_sha256
                    and hit.source_uri == policy.source_uri and hit.version == policy.version
                    and hit.title == policy.title and hit.document_id == policy.document_id
                    and hit.page > 0 and bool(hit.section))
        rows.append({**question, "first_relevant_rank": rank, "status": result.status,
                     "retrieved": [{"document_id": h.document_id, "section": h.section,
                                    "chunk_id": h.chunk_id, "score": h.score, "excerpt": h.excerpt}
                                   for h in result.hits]})
    irrelevant = [{"query": query, "status": (result := retriever.search(query, k=k, as_of=EVALUATION_DATE)).status,
                   "returned_chunks": len(result.hits)} for query in IRRELEVANT_QUERIES]
    code_paths = sorted(Path(__file__).parent.glob("*.py"))
    return {
        "evaluation": "P7 fixed synthetic retrieval set; not an independent operational benchmark",
        "backend": "memory" if retriever.store.__class__.__name__ == "MemoryStore" else "postgres",
        "live_postgres_validated": False,
        "evaluation_date": EVALUATION_DATE.isoformat(), "k": k, "min_score": retriever.min_score,
        "metric": "cosine similarity; relevant requires expected document AND literal expected passage",
        "recall_at_k": sum(row["first_relevant_rank"] is not None for row in rows) / len(rows),
        "mrr_at_k": sum(1 / row["first_relevant_rank"] if row["first_relevant_rank"] else 0 for row in rows) / len(rows),
        "question_count": len(rows), "document_count": len(policies),
        "chunk_count": sum(len(chunk_policy(p)) for p in policies),
        "embedding_version": EMBEDDING_VERSION, "chunker_version": CHUNKER_VERSION,
        "chunk_size": CHUNK_SIZE, "overlap": OVERLAP,
        "questions_sha256": hashlib.sha256(QUESTION_PATH.read_bytes()).hexdigest(),
        "sources": {p.document_id: p.source_sha256 for p in policies},
        "code_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in code_paths},
        "libraries": {name: importlib.metadata.version(name) for name in ("langchain-core", "numpy")},
        "citations_verified": bool(citations_verified),
        "irrelevant_queries_returned_no_evidence": all(row["returned_chunks"] == 0 for row in irrelevant),
        "irrelevant_queries": irrelevant, "questions": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy-dir", type=Path, default=DEFAULT_POLICY_DIR)
    parser.add_argument("--output", type=Path, default=OUTPUT_PATH)
    parser.add_argument("--k", type=int, default=DEFAULT_K)
    args = parser.parse_args()
    report = evaluate(k=args.k, policy_dir=args.policy_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"report": str(args.output), "recall_at_k": report["recall_at_k"],
                      "mrr_at_k": report["mrr_at_k"], "citations_verified": report["citations_verified"],
                      "irrelevant_queries_returned_no_evidence": report["irrelevant_queries_returned_no_evidence"]}, indent=2))


if __name__ == "__main__":
    main()
