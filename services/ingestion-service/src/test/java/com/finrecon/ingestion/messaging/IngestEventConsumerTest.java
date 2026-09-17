package com.finrecon.ingestion.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrecon.ingestion.domain.LedgerEntryRepository;
import com.finrecon.ingestion.domain.PaymentRepository;
import com.finrecon.ingestion.domain.SettlementRepository;
import com.finrecon.ingestion.ingest.IngestionService;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;

// P5 consumer tests without a broker: consume() is a plain method, so the
// full dedupe-then-replay path runs on H2. Also proves the messaging beans
// stay out of the default context (service boots with no Kafka/Redis).
@SpringBootTest
class IngestEventConsumerTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgers;

    @Autowired
    private SettlementRepository settlements;

    private IngestEventConsumer consumer;

    @BeforeEach
    void setup() {
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();
        consumer = new IngestEventConsumer(ingestion, new InMemoryDedupeStore(), mapper);
    }

    private String eventBody(String sourceType, Object payload) throws Exception {
        IngestEvent event = IngestEvent.of(sourceType, UUID.randomUUID(),
                mapper.valueToTree(payload));
        return mapper.writeValueAsString(event);
    }

    private PaymentIngestRequest payment(String txn) {
        return new PaymentIngestRequest(txn, "C1", "M1", "100.00", "INR",
                "SUCCESS", "2026-09-01T10:00:00+05:30");
    }

    @Test
    void replayPersistsThroughTheIdempotentStorePath() throws Exception {
        consumer.consume(eventBody("PAYMENT_GATEWAY", payment("TXN-ASYNC-1")));
        assertTrue(payments.findByExternalTxnId("TXN-ASYNC-1").isPresent());
    }

    @Test
    void redeliveryIsDeduplicated() throws Exception {
        String body = eventBody("PAYMENT_GATEWAY", payment("TXN-ASYNC-2"));
        consumer.consume(body);
        consumer.consume(body); // same eventId: skipped, not re-stored
        assertEquals(1, payments.count());
    }

    @Test
    void storeLevelIdempotencyCatchesNewEventForSameRow() throws Exception {
        // Different eventIds, same canonical row: P2 idempotency holds.
        consumer.consume(eventBody("PAYMENT_GATEWAY", payment("TXN-ASYNC-3")));
        consumer.consume(eventBody("PAYMENT_GATEWAY", payment("TXN-ASYNC-3")));
        assertEquals(1, payments.count());
    }

    @Test
    void validationFailuresAreTerminalNotRetried() throws Exception {
        PaymentIngestRequest bad = new PaymentIngestRequest("", "C1", "M1",
                "-1", "xx", "SUCCESS", "tomorrow");
        var result = ingestion.ingestPayments(List.of(bad));
        assertEquals(1, result.rejected());
        // Same through the consumer path: no throw (would ack, never retry).
        consumer.consume(eventBody("PAYMENT_GATEWAY", bad));
        assertEquals(0, payments.count());
    }

    @Test
    void unknownSourceTypeFailsFastForRetryOrDlt() throws Exception {
        String body = eventBody("MYSTERY_SOURCE", payment("TXN-ASYNC-9"));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(body));
    }

    @Test
    void messagingBeansStayOutWithoutBrokers() {
        assertTrue(context.getBeansOfType(IngestEventPublisher.class).isEmpty());
        assertTrue(context.getBeansOfType(IngestEventConsumer.class).isEmpty());
        assertTrue(context.getBeansOfType(RedisDedupeStore.class).isEmpty());
        // The safe default is present instead.
        assertEquals(1, context.getBeansOfType(InMemoryDedupeStore.class).size());
    }
}
