package com.banksim.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient funds",
                "Balance " + balance.toPlainString() + " is lower than requested " + requested.toPlainString());
    }
}
