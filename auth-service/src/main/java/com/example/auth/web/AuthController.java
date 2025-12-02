package com.example.auth.web;

import com.example.auth.service.AuthService;
import com.example.auth.web.dto.LoginRequest;
import com.example.auth.web.dto.RefreshRequest;
import com.example.auth.web.dto.SignupRequest;
import com.example.auth.web.dto.TokenResponse;
import com.example.auth.web.dto.UserResponse;
import com.example.auth.web.dto.ValidateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        return withAccessTokenCookie(tokenResponse);
    }

    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokenResponse = authService.refresh(request);
        return withAccessTokenCookie(tokenResponse);
    }

    @GetMapping("/api/v1/auth/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ValidateResponse response = authService.validate(authorization);
        return ResponseEntity.ok()
                .header("X-User-Id", response.userId().toString())
                .body(response);
    }

    private ResponseEntity<TokenResponse> withAccessTokenCookie(TokenResponse tokenResponse) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("token", tokenResponse.accessToken())
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(tokenResponse.expiresIn())
                .secure(false)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .body(tokenResponse);
    }
}
