package com.finrecon.reconciliation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// P3 read access to payments. Reconciliation never writes source records.
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByExternalTxnId(String externalTxnId);
}
