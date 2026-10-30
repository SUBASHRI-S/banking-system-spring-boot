package com.banksim.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body for deposit and withdraw. At most 2 decimals is enforced in the service (Money.validate). */
public record MoneyRequest(
        @Schema(example = "500.00", type = "string")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01") BigDecimal amount) {
}
