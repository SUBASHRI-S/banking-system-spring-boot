package com.banksim.transfer.dto;

import com.banksim.transfer.Transfer;
import com.banksim.transfer.TransferStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long transferId,
        Long fromAccountId,
        String toAccountNumber,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        TransferStatus status,
        Instant createdAt) {

    public static TransferResponse from(Transfer t, String toAccountNumber) {
        return new TransferResponse(t.getId(), t.getFromAccountId(), toAccountNumber, t.getAmount(),
                t.getStatus(), t.getCreatedAt());
    }
}
