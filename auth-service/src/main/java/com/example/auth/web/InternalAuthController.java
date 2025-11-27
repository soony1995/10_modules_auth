package com.example.auth.web;

import com.example.auth.service.AuthService;
import com.example.auth.web.dto.ValidateResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/auth/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ValidateResponse response = authService.validate(authorization);
        return ResponseEntity.ok()
                .header("X-User-Id", response.userId().toString())
                .body(response);
    }
}
