package com.finrecon.exceptioncase.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionActionRepository extends JpaRepository<ResolutionAction, UUID> {

    List<ResolutionAction> findByExceptionExceptionIdOrderByCreatedAtAsc(UUID exceptionId);
}
