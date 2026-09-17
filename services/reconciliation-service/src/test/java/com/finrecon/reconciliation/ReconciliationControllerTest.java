package com.finrecon.reconciliation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.finrecon.reconciliation.domain.LedgerEntry;
import com.finrecon.reconciliation.domain.LedgerEntryRepository;
import com.finrecon.reconciliation.domain.Payment;
import com.finrecon.reconciliation.domain.PaymentRepository;
import com.finrecon.reconciliation.domain.ReconciliationResultRepository;
import com.finrecon.reconciliation.domain.ReconciliationRunRepository;
import com.finrecon.reconciliation.domain.Settlement;
import com.finrecon.reconciliation.domain.SettlementRepository;

// P3 API tests: start a run, read it back, read its results, 404s.
@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mvc;

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
    void seed() {
        results.deleteAll();
        runs.deleteAll();
        settlements.deleteAll();
        ledgers.deleteAll();
        payments.deleteAll();

        OffsetDateTime t0 = OffsetDateTime.parse("2026-09-01T10:00:00+05:30");
        Payment ok = payments.save(new Payment("TXN-API-OK", "C1", "M1",
                new BigDecimal("100.00"), "INR", "SUCCESS", t0));
        ledgers.save(new LedgerEntry(ok, new BigDecimal("100.00"),
                new BigDecimal("5.00"), new BigDecimal("95.00"), "INR", "SUCCESS",
                t0.plusHours(1)));
        settlements.save(new Settlement(ok, new BigDecimal("95.00"),
                new BigDecimal("5.00"), "INR", "SUCCESS",
                LocalDate.parse("2026-09-02"), "BATCH-API"));
        payments.save(new Payment("TXN-API-MISS", "C1", "M1",
                new BigDecimal("50.00"), "INR", "SUCCESS", t0));
    }

    @Test
    void postRunSummarizesMatchedAndMismatched() throws Exception {
        mvc.perform(post("/api/reconcile").param("sourceSet", "API"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ruleVersion").value("1.0.0"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.matched").value(1))
                .andExpect(jsonPath("$.mismatched").value(1))
                .andExpect(jsonPath("$.runId").isString());
    }

    @Test
    void runAndResultsAreReadable() throws Exception {
        String body = mvc.perform(post("/api/reconcile").param("sourceSet", "API"))
                .andReturn().getResponse().getContentAsString();
        String runId = body.replaceAll(".*\"runId\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/reconcile/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceSet").value("API"));

        mvc.perform(get("/api/reconcile/runs/{runId}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknownRunIs404() throws Exception {
        mvc.perform(get("/api/reconcile/runs/00000000-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/reconcile/runs/00000000-0000-4000-8000-000000000000/results"))
                .andExpect(status().isNotFound());
    }
}
