package com.banksim.common;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", detail);
    }
}
