package com.banksim.transfer;

import com.banksim.account.Account;
import com.banksim.account.AccountRepository;
import com.banksim.common.AccountNotFoundException;
import com.banksim.common.InvalidRequestException;
import com.banksim.common.Money;
import com.banksim.transaction.EntryType;
import com.banksim.transaction.LedgerEntry;
import com.banksim.transaction.LedgerEntryRepository;
import com.banksim.transfer.dto.TransferResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BANK-FR-07. Everything below runs in ONE database transaction: if any step throws, nothing is saved.
 * Rows are locked with SELECT ... FOR UPDATE in ascending account-id order, so A to B and B to A at the
 * same moment can never wait on each other (no deadlock).
 */
@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final TransferProperties properties;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           LedgerEntryRepository ledger, TransferProperties properties) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.properties = properties;
    }

    @Transactional
    public TransferResponse transfer(Long userId, Long fromAccountId, String toAccountNumber,
                                     BigDecimal rawAmount, String description, String idempotencyKey) {
        BigDecimal amount = Money.validate(rawAmount);
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new InvalidRequestException("Idempotency-Key must be 1 to 100 characters");
        }

        // 1. I must own the source. Not yours looks the same as missing (404). Scalar query: loads no entity.
        if (!accounts.existsByIdAndUserId(fromAccountId, userId)) {
            throw new AccountNotFoundException();
        }
        // 2. Destination is identified by its 12-digit number, never by internal id. Unknown number: 404.
        Long toAccountId = accounts.findIdByAccountNumber(toAccountNumber).orElseThrow(AccountNotFoundException::new);
        if (toAccountId.equals(fromAccountId)) {
            throw new SameAccountTransferException();
        }

        // 3. Lock both rows, lowest id first. These are the first reads of the entities in this transaction,
        //    so the balances we see are current and nobody else can change them until we commit.
        Long lowId = Math.min(fromAccountId, toAccountId);
        Long highId = Math.max(fromAccountId, toAccountId);
        Account low = accounts.findByIdForUpdate(lowId).orElseThrow(AccountNotFoundException::new);
        Account high = accounts.findByIdForUpdate(highId).orElseThrow(AccountNotFoundException::new);
        Account from = fromAccountId.equals(lowId) ? low : high;
        Account to = from == low ? high : low;

        // 4. Same Idempotency-Key again: return the first result and move no money (BANK-FR-11).
        //    Safe against two identical requests at once because they queue on the source row lock above.
        if (idempotencyKey != null) {
            Optional<Transfer> previous = transfers.findByFromAccountIdAndIdempotencyKey(fromAccountId, idempotencyKey);
            if (previous.isPresent()) {
                Transfer t = previous.get();
                if (!t.getToAccountId().equals(toAccountId) || t.getAmount().compareTo(amount) != 0) {
                    throw new IdempotencyKeyReusedException();
                }
                return TransferResponse.from(t, to.getAccountNumber());
            }
        }

        // 5. Business rules on the locked rows.
        from.assertActive();
        to.assertActive();
        enforceDailyLimit(from, amount);
        from.debit(amount);   // throws InsufficientFundsException if the balance would go below zero
        to.credit(amount);

        // 6. Record the transfer and the two ledger entries. A failure here rolls back the balances too.
        Transfer saved = transfers.save(new Transfer(from.getId(), to.getId(), amount, description, idempotencyKey));
        ledger.save(new LedgerEntry(from.getId(), saved.getId(), EntryType.TRANSFER_DEBIT, amount, from.getBalance()));
        ledger.save(new LedgerEntry(to.getId(), saved.getId(), EntryType.TRANSFER_CREDIT, amount, to.getBalance()));
        return TransferResponse.from(saved, to.getAccountNumber());
    }

    private void enforceDailyLimit(Account from, BigDecimal amount) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal sentToday = transfers.sumOutgoingSince(from.getId(), startOfDay);
        BigDecimal alreadySent = sentToday == null ? BigDecimal.ZERO : sentToday;
        if (alreadySent.add(amount).compareTo(properties.dailyLimit()) > 0) {
            throw new DailyLimitExceededException(properties.dailyLimit());
        }
    }
}
