package com.banksim.account.dto;

import com.banksim.account.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
        @Schema(example = "SAVINGS") @NotNull(message = "type is required (SAVINGS or CURRENT)") AccountType type) {
}
