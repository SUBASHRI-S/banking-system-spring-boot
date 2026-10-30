package com.banksim.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banksim.account.dto.AccountResponse;
import com.banksim.common.AccountFrozenException;
import com.banksim.common.AccountNotFoundException;
import com.banksim.common.InsufficientFundsException;
import com.banksim.common.InvalidAmountException;
import com.banksim.transaction.EntryType;
import com.banksim.transaction.LedgerEntry;
import com.banksim.transaction.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final long USER_ID = 7L;
    private static final long ACCOUNT_ID = 1L;

    @Mock
    AccountRepository accounts;
    @Mock
    LedgerEntryRepository ledger;

    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, ledger);
    }

    private Account accountWithBalance(String balance) {
        Account a = new Account(USER_ID, "100000000001", AccountType.SAVINGS);
        ReflectionTestUtils.setField(a, "id", ACCOUNT_ID);
        a.credit(new BigDecimal(balance));
        return a;
    }

    /** T-UNIT-01 (BANK-FR-06) */
    @Test
    void withdrawWithInsufficientFundsThrowsAndWritesNothing() {
        Account a = accountWithBalance("100.00");
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(a));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(USER_ID, ACCOUNT_ID, new BigDecimal("250.00")));

        assertEquals("INSUFFICIENT_FUNDS", ex.getCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(a.getBalance()));
        verifyNoInteractions(ledger);
    }

    /** T-UNIT-04 (BANK-FR-05) */
    @Test
    void zeroNegativeAndThreeDecimalAmountsAreRejectedBeforeTouchingTheDatabase() {
        for (String bad : new String[] {"0", "0.00", "-5.00", "1.234"}) {
            assertThrows(InvalidAmountException.class,
                    () -> service.deposit(USER_ID, ACCOUNT_ID, new BigDecimal(bad)), "amount " + bad);
        }
        assertThrows(InvalidAmountException.class, () -> service.deposit(USER_ID, ACCOUNT_ID, null));
        verifyNoInteractions(accounts, ledger);
    }

    @Test
    void depositIncreasesBalanceAndWritesLedgerRow() {
        Account a = accountWithBalance("100.00");
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(a));

        AccountResponse response = service.deposit(USER_ID, ACCOUNT_ID, new BigDecimal("50.25"));

        assertEquals("150.25", response.balance().toPlainString());
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).save(captor.capture());
        assertEquals(EntryType.DEPOSIT, captor.getValue().getEntryType());
        assertEquals("50.25", captor.getValue().getAmount().toPlainString());
        assertEquals("150.25", captor.getValue().getBalanceAfter().toPlainString());
    }

    /** BANK-FR-04: someone else's account is reported as not found. */
    @Test
    void depositIntoSomeoneElsesAccountLooksLikeNotFound() {
        Account a = accountWithBalance("100.00");
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(a));

        assertThrows(AccountNotFoundException.class,
                () -> service.deposit(999L, ACCOUNT_ID, new BigDecimal("10.00")));
        verifyNoInteractions(ledger);
    }

    /** BANK-FR-09 */
    @Test
    void frozenAccountRejectsDepositAndWithdraw() {
        Account a = accountWithBalance("100.00");
        a.setStatus(AccountStatus.FROZEN);
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(a));

        assertThrows(AccountFrozenException.class, () -> service.deposit(USER_ID, ACCOUNT_ID, new BigDecimal("1.00")));
        assertThrows(AccountFrozenException.class, () -> service.withdraw(USER_ID, ACCOUNT_ID, new BigDecimal("1.00")));
        verifyNoInteractions(ledger);
    }
}
