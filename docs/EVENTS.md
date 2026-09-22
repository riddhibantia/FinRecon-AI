# EVENTS — messaging contracts (REMOVED)

**Status: removed.** The P5 async messaging path (Kafka topic
`finrecon.ingest.v1`, Redis dedupe) was deleted during the monolith
consolidation — the target machine has 8 GB RAM and no Docker, so the
broker stack was not viable. Ingestion is synchronous-only: rows are
validated and stored idempotently in the same request (`IngestionService`).

The historical contract is preserved below for reference in the committed
evaluation artifact `data/evaluation/p13_kafka.json`. If a broker-backed
async path is ever needed again, restore from git history (the
`IngestEventPublisher` / `IngestEventConsumer` / `DedupeStore` types).
