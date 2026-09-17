package com.finrecon.ingestion.messaging;

// P5 consumer-side deduplication contract. tryClaim returns true the first
// time a key is seen and false on repeats. Redis backs it where a broker
// deployment exists; the in-memory version serves tests and single-node
// runs. Keys expire so the store stays bounded.
public interface DedupeStore {

    boolean tryClaim(String key);
}
