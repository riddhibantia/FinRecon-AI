package com.finrecon.exceptioncase;

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

import com.finrecon.exceptioncase.cases.CaseService;
import com.finrecon.exceptioncase.domain.AuditLogRepository;
import com.finrecon.exceptioncase.domain.ExceptionEvidenceRepository;
import com.finrecon.exceptioncase.domain.LedgerEntryRepository;
import com.finrecon.exceptioncase.domain.Payment;
import com.finrecon.exceptioncase.domain.PaymentRepository;
import com.finrecon.exceptioncase.domain.ReconExceptionRepository;
import com.finrecon.exceptioncase.domain.ReconciliationResult;
import com.finrecon.exceptioncase.domain.ReconciliationResultRepository;
import com.finrecon.exceptioncase.domain.ReconciliationRun;
import com.finrecon.exceptioncase.domain.ReconciliationRunRepository;
import com.finrecon.exceptioncase.domain.ResolutionActionRepository;
import com.finrecon.exceptioncase.domain.SettlementRepository;

import io.micrometer.core.instrument.MeterRegistry;

// P12: probes answer and case outcomes are metered. Full context (H2).
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CaseService cases;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private ReconciliationRunRepository runs;

    @Autowired
    private ReconciliationResultRepository results;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgers;

    @Autowired
    private SettlementRepository settlements;

    @Autowired
    private ReconExceptionRepository exceptions;

    @Autowired
    private ExceptionEvidenceRepository evidence;

    @Autowired
    private ResolutionActionRepository actions;

    @Autowired
    private AuditLogRepository audits;

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
    void caseOutcomesAreMetered() {
        audits.deleteAll();
        actions.deleteAll();
        evidence.deleteAll();
        exceptions.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();

        ReconciliationRun run = runs.save(new ReconciliationRun("OBS", "1.0.0"));
        run.complete();
        Payment payment = payments.save(new Payment("TXN-OBS-C", "C1", "M1",
                new BigDecimal("10.00"), "INR", "SUCCESS",
                OffsetDateTime.parse("2026-09-01T10:00:00+05:30")));
        results.save(new ReconciliationResult(run, payment, "MISMATCHED",
                "MISSING_SETTLEMENT", new BigDecimal("10.00")));
        cases.syncRun(run.getRunId());

        assertTrue(meters.find("finrecon.cases.opened")
                .tag("category", "MISSING_SETTLEMENT").counter().count() >= 1);
    }
}
