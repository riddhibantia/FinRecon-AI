package com.finrecon.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.type.ManyToOneType;
import org.junit.jupiter.api.Test;

import com.finrecon.shared.domain.LedgerEntry;
import com.finrecon.shared.domain.Payment;
import com.finrecon.shared.domain.Settlement;

// P1 domain test. Builds the Hibernate mapping model without any database
// connection and checks it matches the V1 migration table contract.
// Needs no Postgres, no H2; mapping mistakes fail here first.
class DomainModelTest {

    private static Metadata metadata() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"))
                .build();
        return new MetadataSources(registry)
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(LedgerEntry.class)
                .addAnnotatedClass(Settlement.class)
                .buildMetadata();
    }

    @Test
    void coreTablesAreMapped() {
        Metadata metadata = metadata();
        assertEquals("payments",
                metadata.getEntityBinding(Payment.class.getName()).getTable().getName());
        assertEquals("ledger_entries",
                metadata.getEntityBinding(LedgerEntry.class.getName()).getTable().getName());
        assertEquals("settlements",
                metadata.getEntityBinding(Settlement.class.getName()).getTable().getName());
    }

    @Test
    void identifiersMatchMigrationPrimaryKeys() {
        Metadata metadata = metadata();
        assertEquals("paymentId",
                metadata.getEntityBinding(Payment.class.getName()).getIdentifierProperty().getName());
        assertEquals("ledgerEntryId",
                metadata.getEntityBinding(LedgerEntry.class.getName()).getIdentifierProperty().getName());
        assertEquals("settlementId",
                metadata.getEntityBinding(Settlement.class.getName()).getIdentifierProperty().getName());
    }

    @Test
    void ledgerAndSettlementTraceToPayment() {
        Metadata metadata = metadata();
        PersistentClass ledger = metadata.getEntityBinding(LedgerEntry.class.getName());
        PersistentClass settlement = metadata.getEntityBinding(Settlement.class.getName());
        assertNotNull(ledger.getProperty("payment"));
        assertNotNull(settlement.getProperty("payment"));
        assertTrue(ledger.getProperty("payment").getType() instanceof ManyToOneType);
        assertTrue(settlement.getProperty("payment").getType() instanceof ManyToOneType);
    }
}
