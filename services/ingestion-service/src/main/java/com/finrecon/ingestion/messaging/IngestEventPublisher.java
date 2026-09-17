package com.finrecon.ingestion.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

// P5 producer: publishes one event per accepted ingest row, keyed by
// external_txn_id. Best-effort by design: a broker outage must never fail
// the synchronous ingest path, so failures are logged, not thrown.
// Inert unless finrecon.messaging.enabled=true.
@Component
@ConditionalOnProperty(name = "finrecon.messaging.enabled", havingValue = "true")
public class IngestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IngestEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public IngestEventPublisher(KafkaTemplate<String, String> kafka,
                                ObjectMapper mapper,
                                @Value("${finrecon.topics.ingest:" + FinreconTopics.INGEST + "}")
                                String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    public void publish(String sourceType, UUID requestId, String key, Object payload) {
        try {
            IngestEvent event = IngestEvent.of(sourceType, requestId,
                    mapper.valueToTree(payload));
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic, key, mapper.writeValueAsString(event));
            record.headers().add(FinreconTopics.CORRELATION_HEADER,
                    requestId.toString().getBytes(StandardCharsets.UTF_8));
            kafka.send(record);
        } catch (Exception e) {
            log.warn("Dropping ingest event for {} (broker unavailable?): {}",
                    key, e.getMessage());
        }
    }
}
