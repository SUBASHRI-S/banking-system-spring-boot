package com.banksim.common;

import org.springframework.http.HttpStatus;

/** Used for "missing" and for "not yours", so the existence of other people's accounts is never leaked. */
public class AccountNotFoundException extends ApiException {

    public AccountNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found", "Account not found");
    }
}
