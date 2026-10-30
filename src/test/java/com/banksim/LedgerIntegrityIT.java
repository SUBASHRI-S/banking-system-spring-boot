package com.banksim;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.banksim.account.AccountStatus;
import com.banksim.account.dto.AccountResponse;
import com.banksim.common.AccountFrozenException;
import com.banksim.user.Role;
import com.banksim.user.User;

/** Integration tests against real PostgreSQL: ledger integrity, rollback and frozen accounts. */
class LedgerIntegrityIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_credit ON ledger_entries");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_credit()");
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    /** T-INT-01 (BANK-NFR-01) */
    @Test
    void balanceAlwaysEqualsTheSumOfLedgerEntries() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "0.00");
        AccountResponse b = accountWithFunds(bob, "0.00");

        accountService.deposit(alice.getId(), a.id(), new BigDecimal("1000.00"));
        accountService.withdraw(alice.getId(), a.id(), new BigDecimal("125.50"));
        transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("300.25"), "one", null);
        transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("0.01"), "two", null);
        accountService.deposit(bob.getId(), b.id(), new BigDecimal("40.00"));
        accountService.withdraw(bob.getId(), b.id(), new BigDecimal("10.10"));
        transferService.transfer(bob.getId(), b.id(), a.accountNumber(), new BigDecimal("100.00"), null, null);

        assertMoney("674.24", balanceOf(a.id()));
        assertMoney("230.16", balanceOf(b.id()));
        assertEquals(0, balanceOf(a.id()).compareTo(ledgerRepository.netTotalForAccount(a.id())));
        assertEquals(0, balanceOf(b.id()).compareTo(ledgerRepository.netTotalForAccount(b.id())));
    }

    /** T-INT-02 (BANK-FR-07): a failure after the balances changed must roll everything back. */
    @Test
    void transferFailingMidwayLeavesBothBalancesAndHistoryUnchanged() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "500.00");
        AccountResponse b = accountWithFunds(bob, "100.00");
        long ledgerBefore = ledgerRepository.count();

        // A database trigger makes the LAST insert (the credit ledger row of a 123.45 transfer) fail,
        // after the transfer row and the debit row were already written inside the transaction.
        jdbc.execute("""
                CREATE FUNCTION test_fail_credit() RETURNS trigger AS $$
                BEGIN
                  IF NEW.entry_type = 'TRANSFER_CREDIT' AND NEW.amount = 123.45 THEN
                    RAISE EXCEPTION 'forced failure for rollback test';
                  END IF;
                  RETURN NEW;
                END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER test_fail_credit BEFORE INSERT ON ledger_entries "
                + "FOR EACH ROW EXECUTE FUNCTION test_fail_credit()");

        assertThrows(Exception.class, () ->
                transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("123.45"), null, null));

        assertMoney("500.00", balanceOf(a.id()));
        assertMoney("100.00", balanceOf(b.id()));
        assertEquals(0, transferRepository.countByFromAccountId(a.id()));
        assertEquals(ledgerBefore, ledgerRepository.count());
    }

    /** T-INT-03 (BANK-FR-09) */
    @Test
    void frozenAccountRejectsAllMoneyOperations() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "100.00");
        AccountResponse b = accountWithFunds(bob, "100.00");

        accountService.setStatus(a.id(), AccountStatus.FROZEN);

        assertThrows(AccountFrozenException.class,
                () -> accountService.deposit(alice.getId(), a.id(), new BigDecimal("1.00")));
        assertThrows(AccountFrozenException.class,
                () -> accountService.withdraw(alice.getId(), a.id(), new BigDecimal("1.00")));
        // frozen as the source
        assertThrows(AccountFrozenException.class,
                () -> transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("1.00"), null, null));
        // frozen as the destination
        assertThrows(AccountFrozenException.class,
                () -> transferService.transfer(bob.getId(), b.id(), a.accountNumber(), new BigDecimal("1.00"), null, null));

        assertMoney("100.00", balanceOf(a.id()));
        assertMoney("100.00", balanceOf(b.id()));

        accountService.setStatus(a.id(), AccountStatus.ACTIVE);
        accountService.deposit(alice.getId(), a.id(), new BigDecimal("1.00"));
        assertMoney("101.00", balanceOf(a.id()));
    }

    @Test
    void databaseCheckConstraintBlocksNegativeBalanceEvenIfCodeIsBypassed() {
        User alice = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "10.00");
        var entity = accountRepository.findById(a.id()).orElseThrow();
        entity.debit(new BigDecimal("10.00"));          // legal: balance 0.00
        accountRepository.saveAndFlush(entity);
        // force an illegal value straight through JPA, skipping the domain rule
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "balance", new BigDecimal("-1.00"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(entity));
    }
}