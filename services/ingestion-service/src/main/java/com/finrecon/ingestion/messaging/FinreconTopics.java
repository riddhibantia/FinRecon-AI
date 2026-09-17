package com.finrecon.ingestion.messaging;

// P5 topic and header contracts (see docs/EVENTS.md). Single canonical
// topic keyed by external_txn_id so one payment's rows stay ordered;
// retry and dead-letter topics follow the Spring Kafka suffix convention.
public final class FinreconTopics {

    public static final String INGEST = "finrecon.ingest.v1";
    public static final String RETRY_SUFFIX = "-retry";
    public static final String DLT_SUFFIX = "-dlt";
    public static final String CORRELATION_HEADER = "X-Request-Id";

    private FinreconTopics() {
    }
}
