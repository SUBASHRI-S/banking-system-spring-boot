package com.banksim.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "demo@example.com") @NotBlank(message = "email is required") String email,
        @Schema(example = "Str0ng#Pass1") @NotBlank(message = "password is required") String password) {
}
