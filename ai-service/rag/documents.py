"""Strict synthetic Markdown input with verbatim, section-bounded chunks."""
from dataclasses import dataclass
from datetime import date
import hashlib
from pathlib import Path
import re
from uuid import UUID, uuid5

DEFAULT_POLICY_DIR = Path(__file__).resolve().parents[1] / "data" / "policies"
NAMESPACE = UUID("91017a6f-0112-49aa-a89d-bb207c41224f")
CHUNK_SIZE = 180
OVERLAP = 30
CHUNKER_VERSION = "markdown-whitespace-v1"


@dataclass(frozen=True)
class Policy:
    policy_id: str
    document_id: str
    title: str
    version: str
    effective_from: date
    effective_to: date | None
    source_uri: str
    content: str
    source_sha256: str
    body_start: int


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    policy_id: str
    text: str
    metadata: dict


def parse_policy(path: Path) -> Policy:
    path = Path(path)
    raw = path.read_bytes()
    content = raw.decode("utf-8").replace("\r\n", "\n")
    front = re.match(r"\A---\n(.*?)\n---\n", content, flags=re.S)
    if not front:
        raise ValueError(f"{path.name}: required front matter missing")
    fields = {}
    for line in front[1].splitlines():
        key, separator, value = line.partition(":")
        if not separator or key in fields or not value.strip():
            raise ValueError(f"{path.name}: malformed or duplicate metadata")
        fields[key] = value.strip()
    required = {"document_id", "title", "version", "effective_from", "effective_to", "synthetic"}
    if set(fields) != required or fields["synthetic"] != "true":
        raise ValueError(f"{path.name}: expected synthetic policy metadata {sorted(required)}")
    start = date.fromisoformat(fields["effective_from"])
    end = None if fields["effective_to"] == "null" else date.fromisoformat(fields["effective_to"])
    if end is not None and end < start:
        raise ValueError(f"{path.name}: effective_to precedes effective_from")
    policy_id = str(uuid5(NAMESPACE, fields["document_id"] + ":" + fields["version"]))
    return Policy(policy_id, fields["document_id"], fields["title"], fields["version"], start, end,
                  path.resolve().as_uri(), content, hashlib.sha256(raw).hexdigest(), front.end())


def chunk_policy(policy: Policy, chunk_size: int = CHUNK_SIZE, overlap: int = OVERLAP) -> list[Chunk]:
    if chunk_size <= 0 or overlap < 0 or overlap >= chunk_size:
        raise ValueError("chunk_size must be positive and 0 <= overlap < chunk_size")
    chunks = []
    section = None
    page = None
    section_start = policy.body_start
    offset = policy.body_start

    def emit(end):
        text = policy.content[section_start:end]
        tokens = list(re.finditer(r"\S+", text))
        if not tokens:
            return
        if section is None or page is None:
            raise ValueError(f"{policy.document_id}: each passage requires a section and page marker")
        for token_start in range(0, len(tokens), chunk_size - overlap):
            token_end = min(token_start + chunk_size, len(tokens))
            start_offset = section_start + tokens[token_start].start()
            end_offset = section_start + tokens[token_end - 1].end()
            excerpt = policy.content[start_offset:end_offset]
            metadata = {
                "document_id": policy.document_id, "title": policy.title, "version": policy.version,
                "effective_from": policy.effective_from.isoformat(),
                "effective_to": policy.effective_to.isoformat() if policy.effective_to else None,
                "source_uri": policy.source_uri, "source_sha256": policy.source_sha256,
                "section": section, "page": page, "synthetic": True,
                "line_start": policy.content.count("\n", 0, start_offset) + 1,
                "line_end": policy.content.count("\n", 0, end_offset) + 1,
                "token_start": token_start, "token_end": token_end,
                "chunker_version": CHUNKER_VERSION,
            }
            identity = f"{section}:{page}:{start_offset}:{end_offset}:{excerpt}"
            chunks.append(Chunk(str(uuid5(UUID(policy.policy_id), identity)), policy.policy_id, excerpt, metadata))
            if token_end == len(tokens):
                break

    for line in policy.content[policy.body_start:].splitlines(keepends=True):
        heading = re.fullmatch(r"(#{1,6})\s+(.+?)\s*", line.rstrip("\n"))
        page_marker = re.fullmatch(r"<!-- page: ([1-9][0-9]*) -->\s*", line.rstrip("\n"))
        if heading or page_marker:
            emit(offset)
            if heading:
                section = heading[2] if len(heading[1]) > 1 else None
            else:
                page = int(page_marker[1])
            section_start = offset + len(line)
        offset += len(line)
    emit(offset)
    if not chunks:
        raise ValueError(f"{policy.document_id}: no policy passages")
    return chunks


def load_corpus(directory: Path = DEFAULT_POLICY_DIR) -> list[Policy]:
    policies = [parse_policy(path) for path in sorted(Path(directory).glob("*.md"))]
    if not policies:
        raise ValueError(f"No Markdown policies found in {directory}")
    if len({p.policy_id for p in policies}) != len(policies):
        raise ValueError("Duplicate document ID/version in corpus")
    if len({(p.title, p.version) for p in policies}) != len(policies):
        raise ValueError("Duplicate policy title/version in corpus")
    return policies
