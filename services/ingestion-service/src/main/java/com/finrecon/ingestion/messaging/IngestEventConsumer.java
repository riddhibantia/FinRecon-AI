package com.finrecon.ingestion.messaging;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrecon.ingestion.ingest.IngestionService;
import com.finrecon.ingestion.ingest.LedgerIngestRequest;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;
import com.finrecon.ingestion.ingest.SettlementIngestRequest;

// P5 consumer: replays ingest events through the SAME idempotent P2 store
// path, so the async path reproduces synchronous results without duplicate
// effects. Dedupe by eventId first (at-least-once delivery is assumed).
// Validation rejections are terminal (no retry); unexpected failures retry
// 3x with backoff, then land on the DLT for manual review.
// Inert unless finrecon.messaging.enabled=true.
@Component
@ConditionalOnProperty(name = "finrecon.messaging.enabled", havingValue = "true")
public class IngestEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestEventConsumer.class);

    private final IngestionService ingestion;
    private final DedupeStore dedupe;
    private final ObjectMapper mapper;

    public IngestEventConsumer(IngestionService ingestion, DedupeStore dedupe,
                               ObjectMapper mapper) {
        this.ingestion = ingestion;
        this.dedupe = dedupe;
        this.mapper = mapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = FinreconTopics.DLT_SUFFIX)
    @KafkaListener(topics = "${finrecon.topics.ingest:" + FinreconTopics.INGEST + "}",
            groupId = "${finrecon.consumer.group:finrecon-ingestion}")
    public void consume(String body) {
        IngestEvent event = read(body);
        if (!dedupe.tryClaim(event.eventId().toString())) {
            log.info("Skipping redelivered event {}", event.eventId());
            return;
        }
        replay(event);
    }

    // Package-visible for broker-free tests: same logic, no Kafka needed.
    void replay(IngestEvent event) {
        switch (event.sourceType()) {
            case "PAYMENT_GATEWAY" -> ingestion.ingestPayments(
                    List.of(mapper.convertValue(event.payload(), PaymentIngestRequest.class)));
            case "INTERNAL_LEDGER" -> ingestion.ingestLedger(
                    List.of(mapper.convertValue(event.payload(), LedgerIngestRequest.class)));
            case "SETTLEMENT_SYSTEM" -> ingestion.ingestSettlements(
                    List.of(mapper.convertValue(event.payload(), SettlementIngestRequest.class)));
            default -> throw new IllegalArgumentException(
                    "Unknown sourceType: " + event.sourceType());
        }
    }

    @DltHandler
    public void handleDlt(String body) {
        log.error("Ingest event exhausted retries, on DLT: {}", body);
    }

    private IngestEvent read(String body) {
        try {
            return mapper.readValue(body, IngestEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable ingest event", e);
        }
    }
}
