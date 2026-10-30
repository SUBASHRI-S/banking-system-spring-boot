package com.banksim.auth.dto;

import com.banksim.user.Role;

public record RegisterResponse(Long id, String email, Role role) {
}
