# EVENTS — P5 messaging contracts

One canonical topic carries ingest events; the consumer replays them
through the same idempotent P2 store path, so the async path reproduces
synchronous results without duplicate effects.

## Topic

`finrecon.ingest.v1` — keyed by `external_txn_id` (one payment's rows stay
ordered). Value: `IngestEvent` JSON envelope:

```json
{
  "eventId": "uuid (dedupe key)",
  "sourceType": "PAYMENT_GATEWAY | INTERNAL_LEDGER | SETTLEMENT_SYSTEM",
  "requestId": "uuid (end-to-end correlation ID)",
  "payload": { "externalTxnId": "TXN-1", "...": "raw ingest DTO" },
  "occurredAt": "2026-09-01T10:00:00+05:30"
}
```

Record header `X-Request-Id` repeats the correlation ID (master #10).

## Delivery and idempotency

- Producers assume at-least-once delivery: every accepted sync row is
  published best-effort (a broker outage never fails the sync path).
- Consumers deduplicate by `eventId` first (`DedupeStore.tryClaim`), then
  replay through `IngestionService`, whose P2 idempotency (unique
  `external_txn_id`, exact-duplicate match) makes replays safe.
- Dedupe store: Redis (`SETNX` + 24h TTL) where brokers exist,
  in-memory TTL map otherwise. Keys expire; the store stays bounded.

## Retries and dead letters

- Unexpected failures retry 3x with 1s/2s/4s backoff (`@RetryableTopic`).
- Validation rejections are terminal: they ack, never retry.
- Unparseable bodies and exhausted retries land on `finrecon.ingest.v1-dlt`
  for manual review (`@DltHandler` logs them).

## Enablement

All messaging beans are conditional and OFF by default, so services boot
with no Kafka/Redis present:

- `finrecon.messaging.enabled=true` + `KAFKA_BOOTSTRAP_SERVERS` — publisher,
  consumer, retry/DLT wiring.
- `finrecon.redis.enabled=true` + `REDIS_HOST/REDIS_PORT` — Redis dedupe
  instead of in-memory. Also set `management.health.redis.enabled=true`
  so the actuator reflects the Redis you now depend on.

`docker compose up kafka redis` (see `docker-compose.yml`) provides both
locally where Docker exists.
