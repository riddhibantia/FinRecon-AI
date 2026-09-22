package com.finrecon.ingestion.ingest;

// Raw gateway row as received (CSV or JSON). All fields stay String here;
// parsing and validation happen in IngestionService so invalid input is
// rejected with a clear error instead of silently coerced.
// CSV columns: external_txn_id,customer_id,merchant_id,amount,currency,status,event_time
public record PaymentIngestRequest(
        String externalTxnId,
        String customerId,
        String merchantId,
        String amount,
        String currency,
        String status,
        String eventTime) {
}
