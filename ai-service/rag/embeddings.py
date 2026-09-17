"""Offline lexical baseline implementing LangChain's Embeddings interface.

No model weights, downloads, learned semantics, or external service calls.
"""
import hashlib
import math
import re

from langchain_core.embeddings import Embeddings

DIMENSIONS = 1536
EMBEDDING_VERSION = "finrecon-signed-hash-1536-v1"
STOP_WORDS = frozenset("a an and are as at be by can do does for from how i in is it of on or should that the their this to was what when where which who why with".split())


class LocalHashEmbeddings(Embeddings):
    """Unit-normalized signed token counts; SHA256 avoids Python hash randomization."""

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self.embed_query(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        vector = [0.0] * DIMENSIONS
        for token in re.findall(r"[a-z0-9]+", text.lower()):
            if token in STOP_WORDS:
                continue
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % DIMENSIONS
            vector[index] += 1.0 if digest[4] & 1 else -1.0
        norm = math.sqrt(sum(value * value for value in vector))
        return [value / norm for value in vector] if norm else vector


def embedding_text(chunk) -> str:
    return f"{chunk.metadata['title']}\n{chunk.metadata['section']}\n{chunk.text}"
