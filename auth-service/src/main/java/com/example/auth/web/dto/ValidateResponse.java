package com.example.auth.web.dto;

import java.util.UUID;

public record ValidateResponse(
        UUID userId,
        String email,
        String role
) {
}
