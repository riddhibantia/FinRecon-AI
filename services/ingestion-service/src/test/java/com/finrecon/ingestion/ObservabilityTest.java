package com.finrecon.ingestion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.finrecon.ingestion.ingest.IngestionService;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;

import io.micrometer.core.instrument.MeterRegistry;

// P12: probes answer and ingestion outcomes are metered. Full context (H2).
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private MeterRegistry meters;

    @Test
    void livenessIsUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessIsUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loggingConfigCarriesRequestId() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream("logback-spring.xml");
        assertNotNull(resource, "logback-spring.xml must be on the classpath");
        String config = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("%X{requestId}"));
    }

    @Test
    void ingestOutcomesAreMetered() {
        ingestion.ingestPayments(List.of(new PaymentIngestRequest(
                "TXN-MET-1", "C1", "M1", "10.00", "INR", "SUCCESS",
                "2026-09-01T10:00:00+05:30")));
        assertNotNull(meters.find("finrecon.ingest.accepted")
                .tag("sourceType", "PAYMENT_GATEWAY").counter());
        assertTrue(meters.find("finrecon.ingest.accepted")
                .tag("sourceType", "PAYMENT_GATEWAY").counter().count() >= 1);
    }
}
