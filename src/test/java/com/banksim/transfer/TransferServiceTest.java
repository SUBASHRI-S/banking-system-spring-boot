package com.banksim.transfer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banksim.account.AccountRepository;
import com.banksim.common.AccountNotFoundException;
import com.banksim.common.InvalidAmountException;
import com.banksim.transaction.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    AccountRepository accounts;
    @Mock
    TransferRepository transfers;
    @Mock
    LedgerEntryRepository ledger;

    TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, transfers, ledger, new TransferProperties(new BigDecimal("100000.00")));
    }

    /** T-UNIT-02 (BANK-FR-07) */
    @Test
    void transferToTheSameAccountIsRejectedBeforeAnyLock() {
        when(accounts.existsByIdAndUserId(1L, USER_ID)).thenReturn(true);
        when(accounts.findIdByAccountNumber("100000000001")).thenReturn(Optional.of(1L));

        assertThrows(SameAccountTransferException.class,
                () -> service.transfer(USER_ID, 1L, "100000000001", new BigDecimal("10.00"), null, null));

        verify(accounts, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(transfers, ledger);
    }

    /** T-UNIT-03 (BANK-FR-07) */
    @Test
    void transferFromAnAccountTheUserDoesNotOwnIsRejected() {
        when(accounts.existsByIdAndUserId(1L, 999L)).thenReturn(false);

        assertThrows(AccountNotFoundException.class,
                () -> service.transfer(999L, 1L, "100000000002", new BigDecimal("10.00"), null, null));

        verify(accounts, never()).findIdByAccountNumber(anyString());
        verify(accounts, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(transfers, ledger);
    }

    /** BANK-FR-07: unknown destination number gives 404. */
    @Test
    void unknownDestinationNumberIsNotFound() {
        when(accounts.existsByIdAndUserId(1L, USER_ID)).thenReturn(true);
        when(accounts.findIdByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.transfer(USER_ID, 1L, "999999999999", new BigDecimal("10.00"), null, null));
        verify(accounts, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void invalidAmountIsRejectedFirst() {
        assertThrows(InvalidAmountException.class,
                () -> service.transfer(USER_ID, 1L, "100000000002", new BigDecimal("1.234"), null, null));
        verifyNoInteractions(accounts, transfers, ledger);
    }
}
