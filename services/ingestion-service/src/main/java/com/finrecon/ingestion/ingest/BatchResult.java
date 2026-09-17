package com.finrecon.ingestion.ingest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Ingestion outcome for one request. accepted + duplicates + rejected
// always sum to the received row count. Duplicates are skipped silently
// by design (idempotent retry); rejected rows carry per-row errors.
// P2 records this in the response only; no persistence of runs yet.
public record BatchResult(
        UUID requestId,
        String sourceType,
        String status,
        int accepted,
        int duplicates,
        int rejected,
        List<RowError> errors,
        OffsetDateTime processedAt) {

    public static BatchResult of(String sourceType, int accepted, int duplicates,
                                 int rejected, List<RowError> errors) {
        String status = rejected == 0 ? "COMPLETED" : "COMPLETED_WITH_REJECTIONS";
        return new BatchResult(UUID.randomUUID(), sourceType, status,
                accepted, duplicates, rejected, List.copyOf(errors), OffsetDateTime.now());
    }
}
