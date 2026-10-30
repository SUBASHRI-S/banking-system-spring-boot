package com.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksim.account.dto.AccountResponse;
import com.banksim.common.InvalidRequestException;
import com.banksim.transfer.DailyLimitExceededException;
import com.banksim.transfer.IdempotencyKeyReusedException;
import com.banksim.transfer.dto.TransferResponse;
import com.banksim.user.Role;
import com.banksim.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Advanced features: BANK-FR-11 (idempotency key) and BANK-FR-12 (daily limit, set to 100.00 for this class). */
@TestPropertySource(properties = "app.transfer.daily-limit=100.00")
class AdvancedTransferIT extends AbstractIntegrationTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    /** T-ADV-01 */
    @Test
    void sameIdempotencyKeyTwiceCreatesOneTransferAndReturnsTheSameResult() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "80.00");
        AccountResponse b = accountWithFunds(bob, "0.00");

        TransferResponse first = transferService.transfer(alice.getId(), a.id(), b.accountNumber(),
                new BigDecimal("30.00"), "once", "key-123");
        TransferResponse second = transferService.transfer(alice.getId(), a.id(), b.accountNumber(),
                new BigDecimal("30.00"), "once", "key-123");

        assertEquals(first.transferId(), second.transferId());
        assertEquals(1, transferRepository.countByFromAccountId(a.id()));
        assertMoney("50.00", balanceOf(a.id()));
        assertMoney("30.00", balanceOf(b.id()));
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "80.00");
        AccountResponse b = accountWithFunds(bob, "0.00");

        transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("10.00"), null, "key-abc");

        assertThrows(IdempotencyKeyReusedException.class, () ->
                transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("20.00"), null, "key-abc"));
        assertMoney("70.00", balanceOf(a.id()));
    }

    @Test
    void blankIdempotencyKeyIsRejected() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "80.00");
        AccountResponse b = accountWithFunds(bob, "0.00");

        assertThrows(InvalidRequestException.class, () ->
                transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("10.00"), null, "  "));
    }

    /** T-ADV-02 (BANK-FR-12) */
    @Test
    void dailyLimitStopsTransfersThatWouldExceedIt() {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "1000.00");
        AccountResponse b = accountWithFunds(bob, "0.00");

        transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("60.00"), null, null);
        transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("40.00"), null, null); // exactly 100.00 today: allowed

        assertThrows(DailyLimitExceededException.class, () ->
                transferService.transfer(alice.getId(), a.id(), b.accountNumber(), new BigDecimal("0.01"), null, null));

        assertMoney("900.00", balanceOf(a.id()));
        assertMoney("100.00", balanceOf(b.id()));
    }
}
