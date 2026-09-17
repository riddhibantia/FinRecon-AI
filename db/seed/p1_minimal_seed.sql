-- FinRecon AI P1 minimal seed: one gateway record + one ledger record +
-- one settlement record (master #3 example: INR 10,000 SUCCESS gateway,
-- INR 10,000 ledger, INR 9,750 SETTLED settlement with a 250 fee).
-- Synthetic data only. Guarded with WHERE NOT EXISTS so the file is safe
-- to rerun. Apply with: psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql
-- P1 exit: these three source records can be stored and queried.

INSERT INTO payments
    (payment_id, external_txn_id, customer_id, merchant_id, amount, currency, status, event_time)
SELECT '11111111-1111-4111-8111-111111111111',
       'TXN-P1-0001', 'CUST-P1-001', 'MERCHANT-P1-001',
       10000.00, 'INR', 'SUCCESS', '2026-09-01T10:00:00+05:30'
WHERE NOT EXISTS (
    SELECT 1 FROM payments WHERE external_txn_id = 'TXN-P1-0001'
);

INSERT INTO ledger_entries
    (ledger_entry_id, payment_id, gross_amount, fee_amount, net_amount,
     currency, posting_status, posted_at)
SELECT '22222222-2222-4222-8222-222222222222',
       '11111111-1111-4111-8111-111111111111',
       10000.00, 250.00, 9750.00,
       'INR', 'POSTED', '2026-09-01T10:05:00+05:30'
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_entries
    WHERE ledger_entry_id = '22222222-2222-4222-8222-222222222222'
);

INSERT INTO settlements
    (settlement_id, payment_id, settled_amount, fee_amount, currency,
     settlement_status, settlement_date, batch_id)
SELECT '33333333-3333-4333-8333-333333333333',
       '11111111-1111-4111-8111-111111111111',
       9750.00, 250.00, 'INR',
       'SETTLED', '2026-09-02', 'BATCH-P1-001'
WHERE NOT EXISTS (
    SELECT 1 FROM settlements
    WHERE settlement_id = '33333333-3333-4333-8333-333333333333'
);
