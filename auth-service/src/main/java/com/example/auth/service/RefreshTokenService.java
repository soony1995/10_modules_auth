package com.example.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void store(UUID userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(refreshKey(userId), token, ttl);
    }

    public boolean matches(UUID userId, String token) {
        String stored = redisTemplate.opsForValue().get(refreshKey(userId));
        return stored != null && stored.equals(token);
    }

    public void delete(UUID userId) {
        redisTemplate.delete(refreshKey(userId));
    }

    private String refreshKey(UUID userId) {
        return "refresh:" + userId;
    }
}
