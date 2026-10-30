package com.banksim.account.dto;

import com.banksim.account.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @Schema(example = "FROZEN", description = "ACTIVE or FROZEN") @NotNull(message = "status is required") AccountStatus status) {
}
