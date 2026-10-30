package com.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.banksim.account.dto.AccountResponse;
import com.banksim.user.Role;
import com.banksim.user.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * BANK-NFR-02: parallel transfers never lose or create money and never deadlock.
 * ExecutorService runs the transfers on a pool of threads. CountDownLatch is a starting gun: every task waits
 * on it, then they are all released at the same moment so they really overlap.
 */
class ConcurrentTransferIT extends AbstractIntegrationTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    /** T-CON-01 */
    @Test
    void fiftyParallelTransfersFromAToBEndWithExactBalances() throws Exception {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "1000.00");
        AccountResponse b = accountWithFunds(bob, "0.00");
        int transfers = 50;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < transfers; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    transferService.transfer(alice.getId(), a.id(), b.accountNumber(), TEN, "parallel", null);
                    return null;
                }));
            }
            startGun.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);   // rethrows any failure from a worker thread
            }
        } finally {
            pool.shutdownNow();
        }

        assertMoney("500.00", balanceOf(a.id()));
        assertMoney("500.00", balanceOf(b.id()));
        assertMoney("1000.00", balanceOf(a.id()).add(balanceOf(b.id())));   // total money unchanged
        assertEquals(0, balanceOf(a.id()).compareTo(ledgerRepository.netTotalForAccount(a.id())));
        assertEquals(0, balanceOf(b.id()).compareTo(ledgerRepository.netTotalForAccount(b.id())));
        assertEquals(transfers, transferRepository.countByFromAccountId(a.id()));
    }

    /** T-CON-02: opposite directions at the same time would deadlock without ordered locking. */
    @Test
    void parallelAToBAndBToATransfersFinishWithoutDeadlock() throws Exception {
        User alice = newUser(Role.USER);
        User bob = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "1000.00");
        AccountResponse b = accountWithFunds(bob, "1000.00");
        int aToB = 30;
        int bToA = 20;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < aToB; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    transferService.transfer(alice.getId(), a.id(), b.accountNumber(), TEN, null, null);
                    return null;
                }));
            }
            for (int i = 0; i < bToA; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    transferService.transfer(bob.getId(), b.id(), a.accountNumber(), TEN, null, null);
                    return null;
                }));
            }
            startGun.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);   // a deadlock would show up as a timeout or a database error here
            }
        } finally {
            pool.shutdownNow();
        }

        // A: 1000 - 30*10 + 20*10 = 900     B: 1000 + 300 - 200 = 1100
        assertMoney("900.00", balanceOf(a.id()));
        assertMoney("1100.00", balanceOf(b.id()));
        assertMoney("2000.00", balanceOf(a.id()).add(balanceOf(b.id())));
        assertEquals(0, balanceOf(a.id()).compareTo(ledgerRepository.netTotalForAccount(a.id())));
        assertEquals(0, balanceOf(b.id()).compareTo(ledgerRepository.netTotalForAccount(b.id())));
    }

    /** Parallel withdrawals must never push the balance below zero. */
    @Test
    void parallelWithdrawalsNeverMakeTheBalanceNegative() throws Exception {
        User alice = newUser(Role.USER);
        AccountResponse a = accountWithFunds(alice, "100.00");
        int attempts = 30;   // only 10 of them can succeed

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        int succeeded = 0;
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    try {
                        accountService.withdraw(alice.getId(), a.id(), TEN);
                        return true;
                    } catch (com.banksim.common.InsufficientFundsException e) {
                        return false;
                    }
                }));
            }
            startGun.countDown();
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(10, succeeded);
        assertMoney("0.00", balanceOf(a.id()));
        assertEquals(0, balanceOf(a.id()).compareTo(ledgerRepository.netTotalForAccount(a.id())));
    }
}
