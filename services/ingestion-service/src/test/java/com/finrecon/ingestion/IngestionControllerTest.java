package com.finrecon.ingestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.finrecon.ingestion.domain.LedgerEntryRepository;
import com.finrecon.ingestion.domain.PaymentRepository;
import com.finrecon.ingestion.domain.SettlementRepository;

// P2 REST tests: JSON + CSV upload endpoints, counts, error shapes,
// idempotent repeats. Runs on H2; Postgres wiring is proven where Docker
// exists (see docs/DATABASE.md).
@SpringBootTest
@AutoConfigureMockMvc
class IngestionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Autowired
    private SettlementRepository settlements;

    @BeforeEach
    void clean() {
        settlements.deleteAll();
        ledgerEntries.deleteAll();
        payments.deleteAll();
    }

    @Test
    void jsonPaymentsReturnCountsAndCorrelationId() throws Exception {
        mvc.perform(post("/api/ingest/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"externalTxnId":"TXN-R1","customerId":"C1","merchantId":"M1",
                                  "amount":"100.00","currency":"INR","status":"SUCCESS",
                                  "eventTime":"2026-09-01T10:00:00+05:30"}]"""))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.sourceType").value("PAYMENT_GATEWAY"))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.requestId").isString());

        // Repeat is idempotent, not a second row.
        mvc.perform(post("/api/ingest/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"externalTxnId":"TXN-R1","customerId":"C1","merchantId":"M1",
                                  "amount":"100.00","currency":"INR","status":"SUCCESS",
                                  "eventTime":"2026-09-01T10:00:00+05:30"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));
        assert payments.count() == 1;
    }

    @Test
    void jsonValidationFailuresReportPerRowErrors() throws Exception {
        mvc.perform(post("/api/ingest/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"externalTxnId":"","customerId":"C1","merchantId":"M1",
                                  "amount":"-1","currency":"xx","status":"SUCCESS",
                                  "eventTime":"tomorrow"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_REJECTIONS"));
        assert payments.count() == 0;
    }

    @Test
    void emptyBodyIsBadRequest() throws Exception {
        mvc.perform(post("/api/ingest/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void csvUploadIngestsLedgerAfterPayment() throws Exception {
        String paymentsCsv = "external_txn_id,customer_id,merchant_id,amount,currency,status,event_time\n"
                + "TXN-C1,C1,M1,500.00,INR,SUCCESS,2026-09-01T10:00:00+05:30\n";
        mvc.perform(multipart("/api/ingest/payments/csv")
                        .file(new MockMultipartFile("file", "payments.csv",
                                "text/csv", paymentsCsv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        String ledgerCsv = "external_txn_id,gross_amount,fee_amount,net_amount,currency,posting_status,posted_at\n"
                + "TXN-C1,500.00,10.00,490.00,INR,POSTED,2026-09-01T10:05:00+05:30\n";
        mvc.perform(multipart("/api/ingest/ledger-entries/csv")
                        .file(new MockMultipartFile("file", "ledger.csv",
                                "text/csv", ledgerCsv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));
        assert ledgerEntries.count() == 1;
    }

    @Test
    void malformedCsvUploadIsBadRequest() throws Exception {
        String badCsv = "external_txn_id,customer_id\nTXN-X,C1\n";
        mvc.perform(multipart("/api/ingest/payments/csv")
                        .file(new MockMultipartFile("file", "bad.csv",
                                "text/csv", badCsv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_CSV"));
    }
}
