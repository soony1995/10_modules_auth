package com.example.auth.security;

import com.example.auth.domain.user.UserEntity;
import com.example.auth.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(@Value("${security.jwt.secret}") String secret,
                            @Value("${security.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
                            @Value("${security.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(UserEntity user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("token_type", "access")
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenValiditySeconds * 1000);
        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim("token_type", "refresh")
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String getEmail(String token) {
        Object email = parseClaims(token).get("email");
        return email != null ? email.toString() : null;
    }

    public String getRole(String token) {
        Object role = parseClaims(token).get("role");
        return role != null ? role.toString() : null;
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equalsIgnoreCase(String.valueOf(parseClaims(token).get("token_type")));
    }

    public boolean isAccessToken(String token) {
        return "access".equalsIgnoreCase(String.valueOf(parseClaims(token).get("token_type")));
    }

    public Duration getRefreshTokenTtl() {
        return Duration.ofSeconds(refreshTokenValiditySeconds);
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
