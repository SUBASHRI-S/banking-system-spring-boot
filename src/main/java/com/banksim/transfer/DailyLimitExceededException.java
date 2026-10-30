package com.banksim.transfer;

import com.banksim.common.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public class DailyLimitExceededException extends ApiException {

    public DailyLimitExceededException(BigDecimal limit) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DAILY_LIMIT_EXCEEDED", "Daily limit exceeded",
                "Total outgoing transfers per day cannot exceed " + limit.toPlainString());
    }
}
