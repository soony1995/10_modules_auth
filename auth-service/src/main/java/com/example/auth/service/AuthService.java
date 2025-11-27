package com.example.auth.service;

import com.example.auth.domain.account.AccountEntity;
import com.example.auth.domain.user.UserEntity;
import com.example.auth.domain.user.enums.UserStatus;
import com.example.auth.exception.ApiException;
import com.example.auth.repository.AccountRepository;
import com.example.auth.security.JwtTokenProvider;
import com.example.auth.web.dto.LoginRequest;
import com.example.auth.web.dto.RefreshRequest;
import com.example.auth.web.dto.SignupRequest;
import com.example.auth.web.dto.TokenResponse;
import com.example.auth.web.dto.UserResponse;
import com.example.auth.web.dto.ValidateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService,
                       AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userService.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        UserEntity user = UserEntity.create(normalizedEmail, request.nickname().trim());
        AccountEntity account = AccountEntity.ofLocal(normalizedEmail, passwordEncoder.encode(request.password()));
        user.addAccount(account);
        UserEntity saved = userService.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getNickname(), saved.getRole().name());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        AccountEntity account = accountRepository.findByProviderAndProviderId(AccountEntity.LOCAL_PROVIDER, normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issueTokens(account.getUser());
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.refreshToken();
        jwtTokenProvider.validateToken(refreshToken);
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token type");
        }
        UUID userId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token mismatch");
        }
        UserEntity user = userService.getById(userId);
        return issueTokens(user);
    }

    public ValidateResponse validate(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        jwtTokenProvider.validateToken(token);
        if (!jwtTokenProvider.isAccessToken(token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Access token required");
        }
        UUID userId = jwtTokenProvider.getUserId(token);
        UserEntity user = userService.getById(userId);
        ensureActive(user);
        return new ValidateResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    private TokenResponse issueTokens(UserEntity user) {
        ensureActive(user);
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        Duration ttl = jwtTokenProvider.getRefreshTokenTtl();
        refreshTokenService.store(user.getId(), refreshToken, ttl);
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    private void ensureActive(UserEntity user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Inactive account");
        }
    }

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authorization header missing");
        }
        return header.substring(7);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email cannot be null");
        }
        return email.trim().toLowerCase();
    }
}
