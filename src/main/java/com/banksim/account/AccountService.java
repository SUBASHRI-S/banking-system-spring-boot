package com.banksim.account;

import com.banksim.account.dto.AccountResponse;
import com.banksim.common.AccountNotFoundException;
import com.banksim.common.InvalidRequestException;
import com.banksim.common.Money;
import com.banksim.common.PageResponse;
import com.banksim.transaction.EntryType;
import com.banksim.transaction.LedgerEntry;
import com.banksim.transaction.LedgerEntryRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** BANK-FR-03: unique 12-digit fictional number, balance starts at 0.00. */
    @Transactional
    public AccountResponse openAccount(Long userId, AccountType type) {
        Account account = new Account(userId, nextAccountNumber(), type);
        return AccountResponse.from(accounts.save(account));
    }

    /** BANK-FR-04 */
    @Transactional(readOnly = true)
    public List<AccountResponse> listMine(Long userId) {
        return accounts.findByUserIdOrderByIdAsc(userId).stream().map(AccountResponse::from).toList();
    }

    /** BANK-FR-04: someone else's account looks exactly like a missing one (404). */
    @Transactional(readOnly = true)
    public AccountResponse getMine(Long userId, Long accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .map(AccountResponse::from)
                .orElseThrow(AccountNotFoundException::new);
    }

    /** BANK-FR-05 */
    @Transactional
    public AccountResponse deposit(Long userId, Long accountId, BigDecimal rawAmount) {
        BigDecimal amount = Money.validate(rawAmount);
        Account account = lockOwned(userId, accountId);
        account.assertActive();
        account.credit(amount);
        ledger.save(new LedgerEntry(account.getId(), null, EntryType.DEPOSIT, amount, account.getBalance()));
        return AccountResponse.from(account);
    }

    /** BANK-FR-06 */
    @Transactional
    public AccountResponse withdraw(Long userId, Long accountId, BigDecimal rawAmount) {
        BigDecimal amount = Money.validate(rawAmount);
        Account account = lockOwned(userId, accountId);
        account.assertActive();
        account.debit(amount);
        ledger.save(new LedgerEntry(account.getId(), null, EntryType.WITHDRAWAL, amount, account.getBalance()));
        return AccountResponse.from(account);
    }

    /** BANK-FR-09 (admin). */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAll(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return PageResponse.from(accounts
                .findAll(PageRequest.of(safePage, safeSize, Sort.by("id")))
                .map(AccountResponse::from));
    }

    /** BANK-FR-09 (admin): only ACTIVE and FROZEN can be set through the API. */
    @Transactional
    public AccountResponse setStatus(Long accountId, AccountStatus status) {
        if (status != AccountStatus.ACTIVE && status != AccountStatus.FROZEN) {
            throw new InvalidRequestException("Status must be ACTIVE or FROZEN");
        }
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow(AccountNotFoundException::new);
        account.setStatus(status);
        return AccountResponse.from(account);
    }

    /**
     * Locks the row (SELECT ... FOR UPDATE) so two requests cannot overwrite each other's balance,
     * then checks ownership. A missing account and somebody else's account both give 404.
     */
    private Account lockOwned(Long userId, Long accountId) {
        return accounts.findByIdForUpdate(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(AccountNotFoundException::new);
    }

    private String nextAccountNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String number = String.format("10%010d", RANDOM.nextLong(10_000_000_000L));
            if (!accounts.existsByAccountNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }
}
