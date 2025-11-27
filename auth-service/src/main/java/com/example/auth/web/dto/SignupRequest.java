package com.example.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "Email must be valid")
        @NotBlank
        String email,
        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
        @NotBlank
        String nickname
) {
}
