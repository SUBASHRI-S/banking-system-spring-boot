package com.banksim.transfer;

import com.banksim.common.ApiException;
import org.springframework.http.HttpStatus;

public class SameAccountTransferException extends ApiException {

    public SameAccountTransferException() {
        super(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Same account", "Cannot transfer to the same account");
    }
}
