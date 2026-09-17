package com.finrecon.reconciliation.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
}
