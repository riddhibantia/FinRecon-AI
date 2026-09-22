package com.finrecon.shared.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalystFeedbackRepository extends JpaRepository<AnalystFeedback, UUID> {

    List<AnalystFeedback> findByExceptionExceptionIdOrderByCreatedAtAsc(UUID exceptionId);
}