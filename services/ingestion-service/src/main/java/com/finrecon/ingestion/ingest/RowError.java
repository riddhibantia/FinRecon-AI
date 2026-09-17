package com.finrecon.ingestion.ingest;

// One rejected input row: 1-based row number (header = row 1), the field
// at fault, and a deterministic message. Serialized into BatchResult.
public record RowError(int row, String field, String message) {
}
