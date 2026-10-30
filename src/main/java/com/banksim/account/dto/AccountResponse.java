package com.banksim.account.dto;

import com.banksim.account.Account;
import com.banksim.account.AccountStatus;
import com.banksim.account.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        AccountType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        AccountStatus status,
        Instant createdAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getAccountNumber(), a.getType(), a.getBalance(), a.getStatus(), a.getCreatedAt());
    }
}
