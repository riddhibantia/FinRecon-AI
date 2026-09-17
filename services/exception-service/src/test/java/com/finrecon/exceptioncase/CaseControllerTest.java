package com.finrecon.exceptioncase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrecon.exceptioncase.domain.AuditLogRepository;
import com.finrecon.exceptioncase.domain.ExceptionEvidenceRepository;
import com.finrecon.exceptioncase.domain.LedgerEntry;
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

// P4 API tests: sync, queue, detail trace, transitions, error shapes.
@SpringBootTest
@AutoConfigureMockMvc
class CaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

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

    private UUID runId;

    @BeforeEach
    void seed() {
        audits.deleteAll();
        actions.deleteAll();
        evidence.deleteAll();
        exceptions.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();

        ReconciliationRun run = runs.save(new ReconciliationRun("P4-API", "1.0.0"));
        run.complete();
        runId = run.getRunId();
        OffsetDateTime t0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");
        Payment payment = payments.save(new Payment("TXN-API-CASE", "C1", "M1",
                new BigDecimal("100.00"), "INR", "SUCCESS", t0));
        ledgers.save(new LedgerEntry(payment, new BigDecimal("100.00"),
                new BigDecimal("5.00"), new BigDecimal("95.00"), "INR", "SUCCESS",
                t0.plusHours(1)));
        results.save(new ReconciliationResult(run, payment, "MISMATCHED",
                "MISSING_SETTLEMENT", new BigDecimal("95.00")));
    }

    private String syncAndGetCaseId() throws Exception {
        mvc.perform(post("/api/cases/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("runId", runId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opened").value(1));
        String queue = mvc.perform(get("/api/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(queue).get(0).get("exceptionId").asText();
    }

    @Test
    void syncQueueDetailTrace() throws Exception {
        String caseId = syncAndGetCaseId();

        mvc.perform(get("/api/cases").param("category", "MISSING_SETTLEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Detail traces exception -> evidence -> source records -> audit.
        mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MISSING_SETTLEMENT"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.ruleVersion").value("1.0.0"))
                .andExpect(jsonPath("$.evidence.length()").value(4))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.auditTrail.length()").value(1));
    }

    @Test
    void assignResolveFlow() throws Exception {
        String caseId = syncAndGetCaseId();

        mvc.perform(post("/api/cases/{id}/assign", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedTo\":\"analyst-7\",\"actorId\":\"analyst-7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        mvc.perform(post("/api/cases/{id}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"SETTLEMENT_POSTED\",\"actorType\":\"ANALYST\","
                                + "\"actorId\":\"analyst-7\",\"notes\":\"Settled late, confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(jsonPath("$.caseActions.length()").value(2));
    }

    @Test
    void illegalTransitionIs422AndUnknownIs404() throws Exception {
        String caseId = syncAndGetCaseId();

        // Resolve while still OPEN is illegal.
        mvc.perform(post("/api/cases/{id}/resolve", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ILLEGAL_TRANSITION"));

        mvc.perform(get("/api/cases/00000000-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
