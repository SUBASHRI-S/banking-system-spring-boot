package com.banksim.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @Schema(example = "1") @NotNull(message = "fromAccountId is required") Long fromAccountId,

        @Schema(example = "100000000002")
        @NotBlank(message = "toAccountNumber is required")
        @Pattern(regexp = "\\d{12}", message = "toAccountNumber must be exactly 12 digits") String toAccountNumber,

        @Schema(example = "250.00", type = "string")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01") BigDecimal amount,

        @Schema(example = "Rent share") @Size(max = 140, message = "description must be at most 140 characters") String description) {
}
