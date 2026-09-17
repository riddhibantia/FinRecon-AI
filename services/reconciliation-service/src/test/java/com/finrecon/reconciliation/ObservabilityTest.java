package com.finrecon.reconciliation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.finrecon.reconciliation.domain.LedgerEntryRepository;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.PaymentRepository;
import com.finrecon.reconciliation.domain.ReconciliationResultRepository;
import com.finrecon.reconciliation.domain.ReconciliationRunRepository;
import com.finrecon.reconciliation.domain.SettlementRepository;
import com.finrecon.reconciliation.reconcile.ReconciliationService;

import io.micrometer.core.instrument.MeterRegistry;

// P12: probes answer and run outcomes are metered. Full context (H2).
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReconciliationService service;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgers;

    @Autowired
    private SettlementRepository settlements;

    @Autowired
    private ReconciliationResultRepository results;

    @Autowired
    private ReconciliationRunRepository runs;

    @BeforeEach
    void clean() {
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();
        payments.save(new Payment("TXN-OBS-1", "C1", "M1",
                new BigDecimal("10.00"), "INR", "SUCCESS",
                OffsetDateTime.parse("2026-09-01T10:00:00+05:30")));
    }

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
    void runOutcomesAreMetered() {
        service.run("OBS");
        assertTrue(meters.find("finrecon.recon.runs")
                .tag("status", "COMPLETED").counter().count() >= 1);
        assertNotNull(meters.find("finrecon.recon.results").counter());
    }
}
