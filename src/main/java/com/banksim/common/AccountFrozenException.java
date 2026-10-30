package com.banksim.common;

import org.springframework.http.HttpStatus;

public class AccountFrozenException extends ApiException {

    public AccountFrozenException() {
        super(HttpStatus.CONFLICT, "ACCOUNT_FROZEN", "Account frozen", "The account is frozen and cannot be used for money operations");
    }
}
