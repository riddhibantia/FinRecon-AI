package com.finrecon.ingestion;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.finrecon.ingestion.ingest.BatchResult;
import com.finrecon.ingestion.ingest.IngestionService;
import com.finrecon.ingestion.ingest.LedgerIngestRequest;
import com.finrecon.ingestion.ingest.PaymentIngestRequest;
import com.finrecon.ingestion.ingest.SettlementIngestRequest;

// P2 REST ingestion for the three source types. JSON arrays and CSV file
// uploads map into the canonical P1 entities. Every success response is a
// BatchResult with accepted/duplicates/rejected counts plus per-row errors;
// the requestId doubles as the correlation ID (also sent as X-Request-Id).
// No matching or reconciliation here.
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestion;

    public IngestionController(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/payments")
    public ResponseEntity<BatchResult> ingestPayments(@RequestBody List<PaymentIngestRequest> rows) {
        return accepted(ingestion.ingestPayments(requireBody(rows)));
    }

    @PostMapping("/ledger-entries")
    public ResponseEntity<BatchResult> ingestLedger(@RequestBody List<LedgerIngestRequest> rows) {
        return accepted(ingestion.ingestLedger(requireBody(rows)));
    }

    @PostMapping("/settlements")
    public ResponseEntity<BatchResult> ingestSettlements(@RequestBody List<SettlementIngestRequest> rows) {
        return accepted(ingestion.ingestSettlements(requireBody(rows)));
    }

    @PostMapping(value = "/payments/csv", consumes = "multipart/form-data")
    public ResponseEntity<BatchResult> ingestPaymentsCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        try (InputStreamReader reader = reader(file)) {
            return accepted(ingestion.ingestPaymentsCsv(reader));
        }
    }

    @PostMapping(value = "/ledger-entries/csv", consumes = "multipart/form-data")
    public ResponseEntity<BatchResult> ingestLedgerCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        try (InputStreamReader reader = reader(file)) {
            return accepted(ingestion.ingestLedgerCsv(reader));
        }
    }

    @PostMapping(value = "/settlements/csv", consumes = "multipart/form-data")
    public ResponseEntity<BatchResult> ingestSettlementsCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        try (InputStreamReader reader = reader(file)) {
            return accepted(ingestion.ingestSettlementsCsv(reader));
        }
    }

    private static <T> List<T> requireBody(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BadIngestRequestException("Request body must be a non-empty JSON array");
        }
        return rows;
    }

    private static InputStreamReader reader(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadIngestRequestException("Multipart field 'file' must not be empty");
        }
        return new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
    }

    private static ResponseEntity<BatchResult> accepted(BatchResult result) {
        return ResponseEntity.ok()
                .header("X-Request-Id", result.requestId().toString())
                .body(result);
    }

    @ExceptionHandler(IngestionService.MalformedCsvException.class)
    public ResponseEntity<Map<String, String>> handleMalformedCsv(
            IngestionService.MalformedCsvException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "MALFORMED_CSV", "message", e.getMessage()));
    }

    @ExceptionHandler(BadIngestRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadIngestRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }

    static final class BadIngestRequestException extends RuntimeException {
        BadIngestRequestException(String message) {
            super(message);
        }
    }
}
