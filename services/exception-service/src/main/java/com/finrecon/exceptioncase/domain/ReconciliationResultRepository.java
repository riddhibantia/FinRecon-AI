package com.finrecon.exceptioncase.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, UUID> {

    List<ReconciliationResult> findByRunRunId(UUID runId);

    List<ReconciliationResult> findByMatchStatus(String matchStatus);
}
