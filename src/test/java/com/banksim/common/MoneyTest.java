package com.banksim.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void acceptsTwoDecimalsAndNormalizesScale() {
        assertEquals(new BigDecimal("250.00"), Money.validate(new BigDecimal("250")));
        assertEquals(new BigDecimal("0.01"), Money.validate(new BigDecimal("0.01")));
        assertEquals(new BigDecimal("12.50"), Money.validate(new BigDecimal("12.500")));
    }

    @Test
    void rejectsNullZeroNegativeAndTooManyDecimals() {
        assertThrows(InvalidAmountException.class, () -> Money.validate(null));
        assertThrows(InvalidAmountException.class, () -> Money.validate(BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class, () -> Money.validate(new BigDecimal("-1.00")));
        assertThrows(InvalidAmountException.class, () -> Money.validate(new BigDecimal("1.001")));
        assertThrows(InvalidAmountException.class, () -> Money.validate(new BigDecimal("0.001")));
    }

    @Test
    void rejectsAmountAboveCap() {
        assertThrows(InvalidAmountException.class, () -> Money.validate(new BigDecimal("1000000000000.01")));
    }
}
