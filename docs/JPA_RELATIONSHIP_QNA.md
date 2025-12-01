# JPA 연관관계 및 데이터 무결성 Q&A 정리

이 문서는 `AccountEntity`와 `UserEntity` 간의 JPA 연관관계 설정, 데이터 무결성, 그리고 트랜잭션 처리에 관한 주요 질의응답을 정리한 것입니다.

## 1. `@ManyToOne` 옵션의 의미

`AccountEntity`에서 `UserEntity`와의 관계를 정의할 때 사용된 옵션에 대한 설명입니다.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id")
private UserEntity user;
```

### `fetch = FetchType.LAZY` (지연 로딩)
*   **목적:** 성능 최적화.
*   **이유:** `EAGER`(즉시 로딩)로 설정 시, `Account`만 조회해도 `User`까지 불필요하게 조인(Join)하여 쿼리가 발생합니다. `LAZY`는 실제 데이터가 필요한 시점에 쿼리를 실행하므로 불필요한 DB 부하를 줄여줍니다.

### `optional = false` (필수 관계)
*   **목적:** 데이터 무결성 및 쿼리 성능 향상.
*   **이유:**
    *   **무결성:** DB 스키마에 `NOT NULL` 제약조건이 설정되어, 사용자 없는 계정 생성을 원천 차단합니다.
    *   **성능:** 관계가 필수임이 보장되므로, JPA가 외부 조인(Left Outer Join) 대신 성능상 유리한 **내부 조인(Inner Join)**을 사용하여 최적화합니다.

---

## 2. 연관관계의 주인 (Owner of Relationship)

### 정의
*   양방향 연관관계에서 **실제 데이터베이스의 외래 키(FK)**를 관리하는 엔티티를 의미합니다.
*   현재 코드에서는 `user_id` FK를 가진 **`AccountEntity`**가 주인입니다.

### 규칙
*   **주인(`Account`)에 값을 설정해야만 DB에 FK 값이 저장됩니다.**
*   주인이 아닌 쪽(`User`)의 리스트에만 값을 추가하면, DB에는 `user_id`가 `null`로 남습니다.

---

## 3. 계정 생성 시나리오별 처리

### Case 1: 최초 회원 가입 (New User + New Account)
*   **상황:** User와 Account 모두 새로 생성.
*   **처리:** `CascadeType.ALL` 덕분에 부모(`User`)만 저장해도 자식(`Account`)까지 자동 저장됩니다.
    ```java
    UserEntity user = UserEntity.create(...);
    user.addAccount(account); // 연관관계 설정
    userRepository.save(user); // 함께 저장됨
    ```

### Case 2: 기존 회원의 계정 추가 (Existing User + New Account)
*   **상황:** 이미 존재하는 User에 새 Account 추가.
*   **처리:**
    1.  **Dirty Checking:** 트랜잭션 내에서 `user.addAccount(account)` 호출 시, 트랜잭션 종료 시점에 자동 반영.
    2.  **직접 저장:** `account.setUser(user)` 후 `accountRepository.save(account)` 호출. (성능상 유리하나 메모리 동기화 주의 필요)

#### 예시: `AuthService`에 `linkSocialAccount` 메서드 추가
아래는 `AuthService`에서 특정 `UserEntity`에 새로운 소셜 계정(`AccountEntity`)을 연동하는 로직의 예시입니다. 기존 `UserEntity`를 조회하여 `addAccount` 편의 메서드를 통해 새로운 `AccountEntity`를 추가합니다.

```java
// auth-service/src/main/java/com/example/auth/service/AuthService.java

import com.example.auth.domain.account.AccountEntity;
import com.example.auth.domain.user.UserEntity;
import com.example.auth.exception.ApiException;
import com.example.auth.repository.AccountRepository; // 추가
import com.example.auth.service.UserService; // 추가
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
// ... 생략

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final AccountRepository accountRepository;
    // ... 생략

    public AuthService(UserService userService,
                       AccountRepository accountRepository,
                       // ... 생략) {
        this.userService = userService;
        this.accountRepository = accountRepository;
        // ... 생략
    }

    @Transactional
    public void linkSocialAccount(UUID userId, String provider, String providerId) {
        // 1. 기존 유저 조회
        UserEntity existingUser = userService.getById(userId);

        // 2. 이미 해당 providerId로 계정이 존재하는지 확인 (선택 사항)
        accountRepository.findByProviderAndProviderId(provider, providerId)
                .ifPresent(account -> {
                    throw new ApiException(HttpStatus.CONFLICT, provider + " account already linked");
                });

        // 3. 새로운 소셜 계정 생성 (passwordHash는 null 허용)
        // AccountEntity에 ofSocial 팩토리 메서드 추가가 필요합니다.
        AccountEntity socialAccount = AccountEntity.ofSocial(provider, providerId); 

        // 4. 연관관계 설정 (편의 메서드 사용)
        existingUser.addAccount(socialAccount);

        // 5. UserEntity의 cascade 설정에 따라 AccountEntity도 함께 저장되거나, 
        //    트랜잭션 종료 시 Dirty Checking으로 자동 INSERT 됩니다.
        //    별도로 accountRepository.save(socialAccount)를 호출하지 않아도 됩니다.
    }

    // ... 다른 메서드 생략
}

// AccountEntity에 ofSocial 팩토리 메서드 추가가 필요합니다.
// 예시: auth-service/src/main/java/com/example/auth/domain/account/AccountEntity.java
/*
// 기존 AccountEntity constructor/ofLocal 메서드들 아래에 추가
private AccountEntity(String provider, String providerId, String passwordHash) {
    this.provider = provider;
    this.providerId = providerId;
    this.passwordHash = passwordHash;
}

public static AccountEntity ofLocal(String email, String passwordHash) {
    return new AccountEntity(LOCAL_PROVIDER, email, passwordHash);
}

public static AccountEntity ofSocial(String provider, String providerId) {
    return new AccountEntity(provider, providerId, null); // social 계정은 비밀번호가 없음
}
*/

---

## 4. 연관관계 편의 메서드의 필요성

```java
// UserEntity.java
public void addAccount(AccountEntity account) {
    account.setUser(this); // 1. DB 반영용 (주인 설정)
    this.accounts.add(account); // 2. 메모리/객체 조회용
}
```

1.  **영속성 컨텍스트(메모리) 무결성:** 한 트랜잭션 내에서 저장 직후 다시 조회했을 때, 리스트에 값이 없어서 발생하는 논리적 버그를 방지합니다.
2.  **실수 방지:** 양쪽 설정 코드를 하나로 캡슐화하여 개발자의 실수를 줄입니다.
3.  **테스트 용이성:** JPA 없는 순수 자바 단위 테스트(Unit Test) 작성 시에도 객체 간 관계가 정상 동작하도록 보장합니다.

---

## 5. 트랜잭션 롤백과 메모리 불일치

### 문제점
*   DB 트랜잭션이 **롤백(Rollback)**되어도, 자바 힙 메모리상의 **객체 상태는 롤백되지 않습니다.**
*   예: `user.addAccount` 호출 후 `save` 실패 -> DB엔 없지만 `user` 객체 리스트엔 `account`가 남아있음 -> "유령 데이터" 문제 발생.

### 해결책
1.  **Stateless 아키텍처 (기본):** 일반적인 웹 요청은 요청 종료 시 영속성 컨텍스트가 닫히고 객체도 폐기되므로, 다음 요청에 영향을 주지 않습니다.
2.  **객체 재사용 시:** `try-catch`로 예외를 잡아 복구하거나 객체를 계속 써야 한다면, 반드시 **영속성 컨텍스트 초기화(`em.clear()`)** 또는 **재조회(`refresh`)**를 통해 오염된 객체를 버려야 합니다.

---

## 6. 연관관계 편의 메서드의 위치 선정 기준

### 일반적인 위치
연관관계의 주인 여부와 상관없이, **비즈니스 로직을 주도하는 엔티티(Aggregate Root)**에 작성하는 것이 관례입니다.

*   **1:N 관계 (User : Account):**
    *   **User(주인이 아님)**에 작성하는 것이 자연스럽습니다.
    *   이유: "유저가 계정을 관리한다"는 개념이 강하며, 유저 엔티티를 조회해서 계정을 추가하는 흐름이 자연스럽기 때문입니다.
*   **N:1 관계 (Member : Team):**
    *   상황에 따라 Member(주인)가 팀을 변경하는 로직이 핵심이라면 Member에 작성할 수도 있습니다.

### "중심이 되는 엔티티" 기준
결국 데이터를 변경하려면 **기존 데이터를 불러오는 진입점(Entry Point)**이 있어야 합니다.
*   `User`를 조회해서 `Account`를 추가한다면 -> `User`에 메서드 위치.
*   `Account`를 먼저 조회해서 주인을 바꾼다면 -> `Account`에 메서드 위치.

**결론:**
> 기술적인 주인(FK 관리자)보다는, **"누구를 통해서 이 행위를 하느냐"**는 **도메인 관점의 주인(중심이 되는 엔티티)**을 기준으로 작성하는 것이 코드 가독성과 유지보수성에 좋습니다.