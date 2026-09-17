package com.finrecon.ingestion.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// P1 repository for settlements.
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByPaymentPaymentId(UUID paymentId);

    List<Settlement> findByBatchId(String batchId);
}
