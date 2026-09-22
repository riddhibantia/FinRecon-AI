package com.finrecon.ingestion.ingest;

// Raw ledger row as received. The payment is referenced by the gateway
// external_txn_id to preserve gateway-to-ledger traceability.
// CSV columns: external_txn_id,gross_amount,fee_amount,net_amount,currency,posting_status,posted_at
public record LedgerIngestRequest(
        String externalTxnId,
        String grossAmount,
        String feeAmount,
        String netAmount,
        String currency,
        String postingStatus,
        String postedAt) {
}
