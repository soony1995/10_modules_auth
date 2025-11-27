package com.example.auth.repository;

import com.example.auth.domain.account.AccountEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<AccountEntity> findByProviderAndProviderId(String provider, String providerId);
}
