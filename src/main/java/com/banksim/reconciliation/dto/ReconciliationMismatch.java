package com.banksim.reconciliation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record ReconciliationMismatch(
        Long accountId,
        String accountNumber,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ledgerTotal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal difference) {
}
