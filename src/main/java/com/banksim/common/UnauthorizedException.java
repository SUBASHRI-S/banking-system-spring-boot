package com.banksim.common;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", detail);
    }
}
