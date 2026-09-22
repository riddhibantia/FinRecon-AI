package com.finrecon.shared.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// P1 repository for payments. findByExternalTxnId supports the P3 exact
// reference match; nothing here ingests or normalizes (P2).
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByExternalTxnId(String externalTxnId);
}
