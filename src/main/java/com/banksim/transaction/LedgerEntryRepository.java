package com.banksim.transaction;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {

    /** Credits minus debits. BANK-NFR-01: this must always equal the account balance. */
    @Query(value = """
            select coalesce(sum(case when entry_type in ('DEPOSIT', 'TRANSFER_CREDIT') then amount else -amount end), 0)
            from ledger_entries where account_id = :accountId
            """, nativeQuery = true)
    BigDecimal netTotalForAccount(@Param("accountId") Long accountId);
}
