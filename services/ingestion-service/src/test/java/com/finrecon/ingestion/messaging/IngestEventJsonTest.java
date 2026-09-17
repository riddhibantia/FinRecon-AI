package com.finrecon.ingestion.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;

// P5 contract test: the event envelope round-trips through JSON with its
// correlation IDs intact. No Spring, no broker.
class IngestEventJsonTest {

    private final ObjectMapper mapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void envelopeRoundTrip() throws Exception {
        PaymentIngestRequest payload = new PaymentIngestRequest("TXN-EVT-1",
                "C1", "M1", "10.00", "INR", "SUCCESS", "2026-09-01T10:00:00+05:30");
        UUID requestId = UUID.randomUUID();
        IngestEvent event = IngestEvent.of("PAYMENT_GATEWAY", requestId,
                mapper.valueToTree(payload));

        IngestEvent back = mapper.readValue(mapper.writeValueAsString(event), IngestEvent.class);

        assertEquals(event.eventId(), back.eventId());
        assertEquals("PAYMENT_GATEWAY", back.sourceType());
        assertEquals(requestId, back.requestId());
        JsonNode node = back.payload();
        assertEquals("TXN-EVT-1", node.get("externalTxnId").asText());
        assertTrue(back.occurredAt() != null);
    }
}
