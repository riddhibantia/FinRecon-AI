package com.finrecon.exceptioncase.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExceptionEvidenceRepository extends JpaRepository<ExceptionEvidence, UUID> {

    List<ExceptionEvidence> findByExceptionExceptionId(UUID exceptionId);
}
