package com.finrecon.reconciliation.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, UUID> {

    List<ReconciliationResult> findByRunRunId(UUID runId);
}
