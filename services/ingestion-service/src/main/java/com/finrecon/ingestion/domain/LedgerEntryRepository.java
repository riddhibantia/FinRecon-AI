package com.finrecon.ingestion.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// P1 repository for ledger_entries.
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByPaymentPaymentId(UUID paymentId);
}
