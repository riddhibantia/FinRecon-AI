package com.finrecon.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.finrecon.ingestion.domain.LedgerEntryRepository;
import com.finrecon.ingestion.domain.PaymentRepository;
import com.finrecon.ingestion.domain.SettlementRepository;
import com.finrecon.ingestion.ingest.BatchResult;
import com.finrecon.ingestion.ingest.IngestionService;
import com.finrecon.ingestion.ingest.LedgerIngestRequest;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;
import com.finrecon.ingestion.ingest.SettlementIngestRequest;

// P2 service tests on H2: CSV shape, validation, mapping, idempotency,
// partial batches. No Postgres needed; SQL migrations are covered in P1.
@SpringBootTest
class IngestionServiceTest {

    private static final String PAYMENTS_CSV =
            "external_txn_id,customer_id,merchant_id,amount,currency,status,event_time\n"
            + "TXN-1,CUST-1,MERCH-1,10000.00,INR,SUCCESS,2026-09-01T10:00:00+05:30\n";

    private static final String LEDGER_CSV =
            "external_txn_id,gross_amount,fee_amount,net_amount,currency,posting_status,posted_at\n"
            + "TXN-1,10000.00,250.00,9750.00,INR,POSTED,2026-09-01T10:05:00+05:30\n";

    private static final String SETTLEMENTS_CSV =
            "external_txn_id,settled_amount,fee_amount,currency,settlement_status,settlement_date,batch_id\n"
            + "TXN-1,9750.00,250.00,INR,SETTLED,2026-09-02,BATCH-1\n";

    @Autowired
    private IngestionService ingestion;

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
    void validCsvIngestionPersistsThreeLinkedRecords() throws Exception {
        BatchResult gw = ingestion.ingestPaymentsCsv(new StringReader(PAYMENTS_CSV));
        BatchResult le = ingestion.ingestLedgerCsv(new StringReader(LEDGER_CSV));
        BatchResult st = ingestion.ingestSettlementsCsv(new StringReader(SETTLEMENTS_CSV));

        assertEquals(1, gw.accepted());
        assertEquals(1, le.accepted());
        assertEquals(1, st.accepted());
        assertEquals(0, gw.rejected());
        assertEquals(1, payments.count());
        assertEquals(1, ledgerEntries.count());
        assertEquals(1, settlements.count());

        var payment = payments.findByExternalTxnId("TXN-1").orElseThrow();
        assertEquals(0, payment.getAmount().compareTo(new BigDecimal("10000.00")));
        assertEquals("INR", payment.getCurrency());
        assertEquals("MERCH-1", payment.getMerchantId());
        var ledger = ledgerEntries.findByPaymentPaymentId(payment.getPaymentId()).get(0);
        assertEquals(0, ledger.getNetAmount().compareTo(new BigDecimal("9750.00")));
        var settlement = settlements.findByPaymentPaymentId(payment.getPaymentId()).get(0);
        assertEquals("BATCH-1", settlement.getBatchId());
    }

    @Test
    void invalidRowsAreRejectedWithFieldErrors() throws Exception {
        String csv = "external_txn_id,customer_id,merchant_id,amount,currency,status,event_time\n"
                + "TXN-BAD,CUST-1,MERCH-1,-5.00,inr,,not-a-time\n";
        BatchResult result = ingestion.ingestPaymentsCsv(new StringReader(csv));

        assertEquals(0, result.accepted());
        assertEquals(1, result.rejected());
        assertEquals(0, payments.count());
        var fields = result.errors().stream().map(e -> e.field()).toList();
        assertTrue(fields.contains("amount"));
        assertTrue(fields.contains("currency"));
        assertTrue(fields.contains("status"));
        assertTrue(fields.contains("event_time"));
    }

    @Test
    void missingColumnFailsTheWholeFile() {
        String csv = "external_txn_id,customer_id,merchant_id,amount,currency,status\n"
                + "TXN-1,CUST-1,MERCH-1,10.00,INR,SUCCESS\n";
        assertThrows(IngestionService.MalformedCsvException.class,
                () -> ingestion.ingestPaymentsCsv(new StringReader(csv)));
        assertEquals(0, payments.count());
    }

    @Test
    void ledgerRejectsUnknownTransactionReference() throws Exception {
        BatchResult result = ingestion.ingestLedgerCsv(new StringReader(LEDGER_CSV));
        assertEquals(0, result.accepted());
        assertEquals(1, result.rejected());
        assertEquals("external_txn_id", result.errors().get(0).field());
    }

    @Test
    void repeatedPaymentsAreIdempotent() throws Exception {
        assertEquals(1, ingestion.ingestPaymentsCsv(new StringReader(PAYMENTS_CSV)).accepted());
        BatchResult repeat = ingestion.ingestPaymentsCsv(new StringReader(PAYMENTS_CSV));
        assertEquals(0, repeat.accepted());
        assertEquals(1, repeat.duplicates());
        assertEquals(0, repeat.rejected());
        assertEquals(1, payments.count());
    }

    @Test
    void repeatedLedgerAndSettlementRowsAreIdempotent() throws Exception {
        ingestion.ingestPaymentsCsv(new StringReader(PAYMENTS_CSV));
        assertEquals(1, ingestion.ingestLedgerCsv(new StringReader(LEDGER_CSV)).accepted());
        assertEquals(1, ingestion.ingestLedgerCsv(new StringReader(LEDGER_CSV)).duplicates());
        assertEquals(1, ingestion.ingestSettlementsCsv(new StringReader(SETTLEMENTS_CSV)).accepted());
        assertEquals(1, ingestion.ingestSettlementsCsv(new StringReader(SETTLEMENTS_CSV)).duplicates());
        assertEquals(1, ledgerEntries.count());
        assertEquals(1, settlements.count());
    }

    @Test
    void partialBatchPersistsValidRowsAndReportsBadOnes() throws Exception {
        String csv = "external_txn_id,customer_id,merchant_id,amount,currency,status,event_time\n"
                + "TXN-GOOD,CUST-1,MERCH-1,100.00,INR,SUCCESS,2026-09-01T10:00:00+05:30\n"
                + "TXN-BAD,CUST-1,MERCH-1,abc,INR,SUCCESS,2026-09-01T10:00:00+05:30\n";
        BatchResult result = ingestion.ingestPaymentsCsv(new StringReader(csv));

        assertEquals(1, result.accepted());
        assertEquals(1, result.rejected());
        assertEquals("COMPLETED_WITH_REJECTIONS", result.status());
        assertTrue(payments.findByExternalTxnId("TXN-GOOD").isPresent());
        assertTrue(payments.findByExternalTxnId("TXN-BAD").isEmpty());
    }

    @Test
    void jsonRequestsMapToCanonicalRecords() {
        BatchResult result = ingestion.ingestPayments(List.of(
                new PaymentIngestRequest("TXN-J-1",
                        "CUST-9", "MERCH-9", "250.50", "INR", "SUCCESS",
                        "2026-09-03T12:00:00Z")));
        assertEquals(1, result.accepted());
        var payment = payments.findByExternalTxnId("TXN-J-1").orElseThrow();
        assertEquals(0, payment.getAmount().compareTo(new BigDecimal("250.50")));

        BatchResult led = ingestion.ingestLedger(List.of(
                new LedgerIngestRequest("TXN-J-1", "250.50", "5.50", "245.00",
                        "INR", "POSTED", "2026-09-03T12:05:00Z")));
        assertEquals(1, led.accepted());

        BatchResult stl = ingestion.ingestSettlements(List.of(
                new SettlementIngestRequest("TXN-J-1", "245.00", "5.50",
                        "INR", "SETTLED", "2026-09-04", "BATCH-9")));
        assertEquals(1, stl.accepted());
    }
}
