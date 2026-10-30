package com.banksim.transaction;

import com.banksim.account.AccountRepository;
import com.banksim.common.AccountNotFoundException;
import com.banksim.common.PageResponse;
import com.banksim.transaction.dto.LedgerEntryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public TransactionService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** BANK-FR-08: paginated, newest first, filter by type and date range (UTC days, both ends inclusive). */
    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> history(Long userId, Long accountId, EntryType type,
                                                     LocalDate from, LocalDate to, int page, int size) {
        if (!accounts.existsByIdAndUserId(accountId, userId)) {
            throw new AccountNotFoundException();
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Specification<LedgerEntry> spec = (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entryType"), type));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
        }
        if (to != null) {
            Instant endExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), endExclusive));
        }

        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Page<LedgerEntry> result = ledger.findAll(spec, PageRequest.of(safePage, safeSize, newestFirst));
        return PageResponse.from(result.map(LedgerEntryResponse::from));
    }
}
