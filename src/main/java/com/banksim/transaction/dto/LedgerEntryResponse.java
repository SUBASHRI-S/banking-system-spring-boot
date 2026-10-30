package com.banksim.transaction.dto;

import com.banksim.transaction.EntryType;
import com.banksim.transaction.LedgerEntry;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        Long id,
        EntryType entryType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter,
        Long transferId,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(e.getId(), e.getEntryType(), e.getAmount(), e.getBalanceAfter(),
                e.getTransferId(), e.getCreatedAt());
    }
}
