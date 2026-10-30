package com.banksim.common;

public class InvalidAmountException extends InvalidRequestException {

    public InvalidAmountException(String detail) {
        super(detail);
    }
}
