package com.example.auth.web.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
