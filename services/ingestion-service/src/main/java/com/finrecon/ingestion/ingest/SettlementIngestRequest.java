package com.finrecon.ingestion.ingest;

// Raw settlement row as received. The payment is referenced by the gateway
// external_txn_id to preserve gateway-to-settlement traceability.
// CSV columns: external_txn_id,settled_amount,fee_amount,currency,settlement_status,settlement_date,batch_id
public record SettlementIngestRequest(
        String externalTxnId,
        String settledAmount,
        String feeAmount,
        String currency,
        String settlementStatus,
        String settlementDate,
        String batchId) {
}
