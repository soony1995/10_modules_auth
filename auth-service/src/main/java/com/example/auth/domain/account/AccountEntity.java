package com.example.auth.domain.account;

import com.example.auth.domain.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "provider_id"})
})
public class AccountEntity {

    public static final String LOCAL_PROVIDER = "LOCAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected AccountEntity() {
    }

    private AccountEntity(String provider, String providerId, String passwordHash) {
        this.provider = provider;
        this.providerId = providerId;
        this.passwordHash = passwordHash;
    }

    public static AccountEntity ofLocal(String email, String passwordHash) {
        return new AccountEntity(LOCAL_PROVIDER, email, passwordHash);
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
