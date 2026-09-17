package com.finrecon.exceptioncase.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconExceptionRepository extends JpaRepository<ReconException, UUID> {

    Optional<ReconException> findByResultResultId(UUID resultId);

    List<ReconException> findByStatus(String status);

    List<ReconException> findByCategory(String category);

    List<ReconException> findByAssignedTo(String assignedTo);
}
