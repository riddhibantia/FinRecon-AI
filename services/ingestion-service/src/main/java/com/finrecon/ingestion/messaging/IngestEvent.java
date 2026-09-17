package com.finrecon.ingestion.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

// P5 canonical ingest event. One envelope for all three source types;
// sourceType is PAYMENT_GATEWAY, INTERNAL_LEDGER, or SETTLEMENT_SYSTEM and
// payload is the matching raw ingest DTO as JSON. eventId drives
// consumer-side deduplication; requestId is the end-to-end correlation ID
// (also sent as the X-Request-Id / Kafka record header).
public record IngestEvent(
        UUID eventId,
        String sourceType,
        UUID requestId,
        JsonNode payload,
        OffsetDateTime occurredAt) {

    public static IngestEvent of(String sourceType, UUID requestId, JsonNode payload) {
        return new IngestEvent(UUID.randomUUID(), sourceType, requestId,
                payload, OffsetDateTime.now());
    }
}
