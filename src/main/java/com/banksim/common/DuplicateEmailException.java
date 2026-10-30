package com.banksim.common;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException() {
        super(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Duplicate email", "An account with this email already exists");
    }
}
