package com.finrecon.ingestion.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// P5 dedupe tests: first claim wins, repeats lose, keys are independent.
// No Spring, no Redis.
class InMemoryDedupeStoreTest {

    @Test
    void firstClaimWinsRepeatLoses() {
        InMemoryDedupeStore store = new InMemoryDedupeStore();
        assertTrue(store.tryClaim("evt-1"));
        assertFalse(store.tryClaim("evt-1"));
    }

    @Test
    void keysAreIndependent() {
        InMemoryDedupeStore store = new InMemoryDedupeStore();
        assertTrue(store.tryClaim("evt-1"));
        assertTrue(store.tryClaim("evt-2"));
    }
}
