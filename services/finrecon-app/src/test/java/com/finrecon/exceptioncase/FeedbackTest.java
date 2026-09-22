package com.finrecon.exceptioncase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrecon.shared.domain.AnalystFeedbackRepository;
import com.finrecon.shared.domain.AuditLogRepository;
import com.finrecon.shared.domain.ExceptionEvidenceRepository;
import com.finrecon.shared.domain.LedgerEntryRepository;
import com.finrecon.shared.domain.Payment;
import com.finrecon.shared.domain.PaymentRepository;
import com.finrecon.shared.domain.ReconciliationResult;
import com.finrecon.shared.domain.ReconciliationResultRepository;
import com.finrecon.shared.domain.ReconExceptionRepository;
import com.finrecon.shared.domain.ReconciliationRun;
import com.finrecon.shared.domain.ReconciliationRunRepository;
import com.finrecon.shared.domain.ResolutionActionRepository;
import com.finrecon.shared.domain.SettlementRepository;

// FR-11: an analyst confirms or corrects the classification; the row is
// persisted with an audit entry and appears on the case detail.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class FeedbackTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ReconciliationRunRepository runs;
    @Autowired private ReconciliationResultRepository results;
    @Autowired private PaymentRepository payments;
    @Autowired private LedgerEntryRepository ledgers;
    @Autowired private SettlementRepository settlements;
    @Autowired private ReconExceptionRepository exceptions;
    @Autowired private ExceptionEvidenceRepository evidence;
    @Autowired private ResolutionActionRepository actions;
    @Autowired private AnalystFeedbackRepository feedback;
    @Autowired private AuditLogRepository audits;

    private UUID runId;

    @BeforeEach
    void seed() {
        feedback.deleteAll();
        audits.deleteAll();
        actions.deleteAll();
        evidence.deleteAll();
        exceptions.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();
        ReconciliationRun run = runs.save(new ReconciliationRun("FR11", "1.0.0"));
        run.complete();
        runId = run.getRunId();
        Payment payment = payments.save(new Payment("TXN-FR11", "C1", "M1",
                new BigDecimal("100.00"), "INR", "SUCCESS",
                OffsetDateTime.parse("2026-09-01T10:00:00+05:30")));
        results.save(new ReconciliationResult(run, payment, "MISMATCHED",
                "FEE_VARIANCE", new BigDecimal("5.00")));
    }

    private String openCase() throws Exception {
        mvc.perform(post("/api/cases/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("runId", runId))))
                .andExpect(status().isOk());
        String queue = mvc.perform(get("/api/cases")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(queue).get(0).get("exceptionId").asText();
    }

    @Test
    void correctionIsPersistedAuditedAndVisibleOnDetail() throws Exception {
        String caseId = openCase();

        mvc.perform(post("/api/cases/{id}/feedback", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analystId\":\"analyst-7\",\"originalValue\":\"FEE_VARIANCE\","
                                + "\"correctedValue\":\"AMOUNT_MISMATCH\",\"reason\":\"fee schedule mismatch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctedValue").value("AMOUNT_MISMATCH"));

        mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(jsonPath("$.feedback.length()").value(1))
                .andExpect(jsonPath("$.feedback[0].analystId").value("analyst-7"))
                // Exactly one feedback audit row for this exception. The
                // filtered-path form is deliberate: Jayway applies a trailing
                // .length() to each matched element (property count), not to
                // the match list, so the count is pinned via the whole trail
                // (CASE_OPENED + CASE_FEEDBACK_RECORDED) and the single
                // matching row is pinned via its actorId.
                .andExpect(jsonPath("$.auditTrail.length()").value(2))
                .andExpect(jsonPath("$.auditTrail[?(@.action=='CASE_FEEDBACK_RECORDED')].actorId")
                        .value("analyst-7"));
    }

    @Test
    void missingCorrectionIs400AndUnknownCaseIs404() throws Exception {
        String caseId = openCase();
        mvc.perform(post("/api/cases/{id}/feedback", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analystId\":\"analyst-7\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cases/{id}/feedback", "00000000-0000-4000-8000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analystId\":\"a\",\"correctedValue\":\"X\"}"))
                .andExpect(status().isNotFound());
    }
}