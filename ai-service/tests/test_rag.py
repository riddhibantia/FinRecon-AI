"""P7 retrieval contracts; synthetic documents, no network or database."""
from dataclasses import replace
from datetime import date
import math

import pytest

from rag.documents import load_corpus, parse_policy, chunk_policy, DEFAULT_POLICY_DIR
from rag.embeddings import LocalHashEmbeddings
from rag.retrieval import build_retriever, PolicyRetriever
from rag.store import MemoryStore
from rag.evaluate import evaluate


@pytest.fixture(scope="module")
def corpus():
    return load_corpus(DEFAULT_POLICY_DIR)


@pytest.fixture(scope="module")
def retriever():
    return build_retriever(database_url="")


def test_chunk_boundaries_preserve_exact_source_and_metadata(tmp_path):
    source = tmp_path / "boundary.md"
    source.write_text("---\ndocument_id: BOUNDARY\ntitle: Boundary Policy\nversion: 1.0\neffective_from: 2026-01-01\neffective_to: 2026-12-31\nsynthetic: true\n---\n# Boundary Policy\n<!-- page: 1 -->\n## First section\n" + " ".join(f"word{i}" for i in range(25)) + "\n<!-- page: 2 -->\n## Second section\nDifferent policy passage.\n", encoding="utf-8")
    policy = parse_policy(source)
    chunks = chunk_policy(policy, chunk_size=12, overlap=3)
    first = [c for c in chunks if c.metadata["section"] == "First section"]
    assert [len(c.text.split()) for c in first] == [12, 12, 7]
    assert first[0].text.split()[-3:] == first[1].text.split()[:3]
    assert chunks[-1].metadata["page"] == 2
    assert all(c.text in source.read_text(encoding="utf-8") for c in chunks)
    assert all(c.metadata["effective_to"] == "2026-12-31" for c in chunks)
    assert len({c.chunk_id for c in chunks}) == len(chunks)
    assert chunks == chunk_policy(policy, chunk_size=12, overlap=3)
    assert first[0].metadata["line_start"] < first[-1].metadata["line_end"] + 1


@pytest.mark.parametrize("size,overlap", [(0, 0), (5, 5), (5, -1)])
def test_invalid_chunk_windows_rejected(corpus, size, overlap):
    with pytest.raises(ValueError):
        chunk_policy(corpus[0], chunk_size=size, overlap=overlap)


def test_embeddings_deterministic_normalized_and_query_compatible():
    a, b = LocalHashEmbeddings(), LocalHashEmbeddings()
    text = "settlement window overdue payment"
    vector = a.embed_query(text)
    assert vector == b.embed_documents([text])[0]
    assert len(vector) == 1536
    assert math.isclose(sum(x * x for x in vector), 1.0)
    assert vector != a.embed_query("foreign exchange reference rate")
    assert a.embed_query("the and of") == [0.0] * 1536


def test_fixed_questions_find_expected_document_and_passage(retriever):
    report = evaluate(retriever)
    assert report["recall_at_k"] >= 0.8
    assert report["mrr_at_k"] >= 0.65
    assert report["irrelevant_queries_returned_no_evidence"] is True
    assert report["citations_verified"] is True


def test_irrelevant_query_is_not_confident(retriever):
    result = retriever.search("How do astronomers measure quasar redshift?")
    assert result.status == "INSUFFICIENT_EVIDENCE"
    assert result.hits == []
    assert retriever.search("  ").hits == []


def test_citations_are_verbatim_and_resolve_to_source(retriever, corpus):
    policies = {p.policy_id: p for p in corpus}
    result = retriever.search("duplicate settlement investigation procedure")
    assert result.hits
    for hit in result.hits:
        citation = hit.to_dict()
        assert citation["excerpt"] in policies[hit.policy_id].content
        for key in ("policy_id", "document_id", "title", "version", "section", "page", "source_uri", "chunk_id", "effective_from", "line_start", "line_end"):
            assert citation[key] is not None
        assert citation["synthetic"] is True


def test_date_filter_excludes_policy_outside_effective_window(corpus):
    policy = replace(corpus[0], effective_from=date(2025, 1, 1), effective_to=date(2025, 12, 31))
    chunks = chunk_policy(policy)
    embeddings = LocalHashEmbeddings()
    store = MemoryStore(chunks, embeddings)
    search = PolicyRetriever(store, embeddings)
    assert search.search(policy.title, as_of=date(2025, 12, 31)).hits
    assert search.search(policy.title, as_of=date(2026, 1, 1)).hits == []
    assert search.search(policy.title, as_of=date(2024, 12, 31)).hits == []


def test_malformed_policy_is_rejected_instead_of_inventing_metadata(tmp_path):
    source = tmp_path / "broken.md"
    source.write_text("# No document metadata\nA policy body", encoding="utf-8")
    with pytest.raises(ValueError):
        parse_policy(source)


def test_invalid_search_limits_rejected(retriever):
    with pytest.raises(ValueError):
        retriever.search("settlement", k=0)
    with pytest.raises(ValueError):
        retriever.search("settlement", k=21)
