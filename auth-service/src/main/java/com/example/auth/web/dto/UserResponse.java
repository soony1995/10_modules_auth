package com.example.auth.web.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String nickname,
        String role
) {
}
