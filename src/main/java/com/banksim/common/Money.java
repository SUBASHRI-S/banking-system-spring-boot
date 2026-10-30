package com.banksim.common;

import java.math.BigDecimal;

/** Amount rules shared by deposit, withdraw and transfer (BANK-FR-05). Never use double for money. */
public final class Money {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000.00");

    private Money() {
    }

    /** Returns the amount with scale 2, or throws if it is null, below 0.01, above the cap or has more than 2 decimals. */
    public static BigDecimal validate(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be at least 0.01");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount is too large");
        }
        return amount.setScale(2);
    }
}
