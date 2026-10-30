package com.banksim.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Demo User")
        @NotBlank(message = "fullName is required") @Size(max = 100) String fullName,

        @Schema(example = "demo@example.com")
        @NotBlank(message = "email is required") @Email(message = "email must be valid") @Size(max = 254) String email,

        @Schema(example = "Str0ng#Pass1")
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "password must contain a letter and a digit")
        String password) {
}
