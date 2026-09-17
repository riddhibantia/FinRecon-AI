package com.finrecon.exceptioncase.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByPaymentPaymentId(UUID paymentId);
}
