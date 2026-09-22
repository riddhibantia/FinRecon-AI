package com.finrecon.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// FR-13: KPIs, ageing, and impact are read from the shared schema and
// repeated verbatim. No value is computed by the UI or invented here.
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class ReportingControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        // Delete in reverse dependency order to respect FK constraints.
        jdbc.update("DELETE FROM analyst_feedback");
        jdbc.update("DELETE FROM exceptions");
        jdbc.update("DELETE FROM exception_evidence");
        jdbc.update("DELETE FROM resolution_actions");
        jdbc.update("DELETE FROM reconciliation_results");
        jdbc.update("DELETE FROM reconciliation_runs");
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM ledger_entries");
        jdbc.update("DELETE FROM payments");

        UUID payment = UUID.fromString("22222222-2222-4222-8222-222222222222");
        jdbc.update("INSERT INTO payments (payment_id, external_txn_id, customer_id, merchant_id,"
                + " amount, currency, status, event_time, created_at)"
                + " VALUES (?, 'txn-001', 'cust-1', 'merch-1', 100.00, 'USD', 'SETTLED',"
                + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", payment);

        UUID run = UUID.fromString("11111111-1111-4111-8111-111111111111");
        jdbc.update("INSERT INTO reconciliation_runs (run_id, source_set, started_at, status,"
                + " rule_version) VALUES (?, 'ALL', CURRENT_TIMESTAMP, 'COMPLETED', '1.0.0')", run);
        jdbc.update("INSERT INTO reconciliation_results (result_id, run_id, payment_id, match_status,"
                + " mismatch_type, amount_difference, created_at)"
                + " VALUES (?, ?, ?, 'MATCHED', NULL, NULL, CURRENT_TIMESTAMP)",
                UUID.fromString("33333333-3333-4333-8333-333333333333"), run, payment);
        UUID mismatched = UUID.fromString("44444444-4444-4444-8444-444444444444");
        jdbc.update("INSERT INTO reconciliation_results (result_id, run_id, payment_id, match_status,"
                + " mismatch_type, amount_difference, created_at)"
                + " VALUES (?, ?, ?, 'MISMATCHED', 'FEE_VARIANCE', 12.00, CURRENT_TIMESTAMP)",
                mismatched, run, payment);
        UUID exceptionId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        jdbc.update("INSERT INTO exceptions (exception_id, result_id, category, severity, status,"
                + " created_at) VALUES (?, ?, 'FEE_VARIANCE', 'HIGH', 'OPEN', CURRENT_TIMESTAMP)",
                exceptionId, mismatched);
        jdbc.update("INSERT INTO analyst_feedback (feedback_id, exception_id, analyst_id,"
                + " original_value, corrected_value, created_at)"
                + " VALUES (?, ?, 'analyst-7', 'FEE_VARIANCE', 'AMOUNT_MISMATCH', CURRENT_TIMESTAMP)",
                UUID.fromString("66666666-6666-4666-8666-666666666666"), exceptionId);
    }

    @Test
    void kpisRepeatStoredFacts() throws Exception {
        mvc.perform(get("/api/reports/kpis"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.results.total").value(2))
                .andExpect(jsonPath("$.results.matched").value(1))
                .andExpect(jsonPath("$.results.mismatched").value(1))
                .andExpect(jsonPath("$.results.autoMatchRate").value("0.5000"))
                .andExpect(jsonPath("$.cases.open").value(1))
                .andExpect(jsonPath("$.cases.byCategory[0].category").value("FEE_VARIANCE"))
                .andExpect(jsonPath("$.impact.absoluteUnresolvedDifference").value("12.00"))
                .andExpect(jsonPath("$.feedback.total").value(1));
    }

    @Test
    void ageingUsesAnInjectedBoundary() throws Exception {
        mvc.perform(get("/api/reports/ageing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unresolvedTotal").value(1))
                .andExpect(jsonPath("$.olderThanDays").value(2))
                .andExpect(jsonPath("$.openOlderThanBoundary").value(0));
    }
}