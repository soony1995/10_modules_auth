# Value Object (VO) Architecture Guide

본 문서는 프로젝트 내에서 **VO(Value Object)**를 언제, 왜, 그리고 어떻게 사용해야 하는지에 대한 아키텍처 가이드라인입니다.

---

## 1. VO (Value Object)란?

**VO(Value Object)**는 도메인 모델에서 **"값(Value)" 그 자체를 표현하는 객체**입니다.

### 1.1 핵심 특징
1.  **불변성 (Immutable):** 생성된 이후에는 값이 절대 변하지 않습니다. 값을 바꾸려면 새로운 객체를 만들어 교체해야 합니다.
2.  **동등성 (Equality):** 객체의 주소값이 아닌, **담고 있는 값**이 같으면 같은 객체로 취급합니다. (`equals()`, `hashCode()` 재정의 필수)
3.  **자가 검증 (Self-Validation):** 생성자에서 값의 유효성을 검증하여, "유효하지 않은 VO는 존재할 수 없음"을 보장합니다.

### 1.2 DTO vs Entity vs VO 비교

| 구분 | DTO (Data Transfer Object) | Entity | VO (Value Object) |
| :--- | :--- | :--- | :--- |
| **목적** | 계층 간 데이터 **전송** | DB 테이블과 매핑되는 **식별 가능한 객체** | 도메인의 **값** 표현 및 로직 캡슐화 |
| **식별자** | 없음 (데이터 묶음) | **있음 (`@Id`)** | **없음** (값 자체가 식별자) |
| **가변성** | 가변 / 불변 (선택) | **가변 (Mutable)** | **불변 (Immutable)** |
| **검증** | `@Valid` (어노테이션 기반) | - | 생성자 내 로직 검증 |
| **사용처** | Controller ↔ Service | Repository ↔ DB | Service, Entity 내부 |

---

## 2. VO를 사용하는 이유 (Why?)

단순히 `String`, `Integer` 같은 원시 타입을 사용하는 것보다 VO를 사용하면 얻을 수 있는 이점들입니다.

1.  **표현력 향상:** `String email`보다 `Email email`이 코드의 의도를 훨씬 명확하게 전달합니다.
2.  **안전한 코드 (Type Safety):** `String` 변수에는 아무 문자열이나 들어갈 수 있지만, `Email` 타입 변수에는 오직 유효한 이메일 객체만 할당될 수 있습니다.
3.  **로직의 캡슐화 (Encapsulation):** 값과 관련된 로직(검증, 계산 등)을 VO 내부에 모아두어 코드 중복을 방지하고 응집도를 높입니다.
    *   예: `Money.add(Money other)`, `Password.matches(String raw)`

---

## 3. 올바른 구현 및 데이터 흐름 (Best Practice)

VO를 도입할 때 가장 권장되는 데이터 처리 흐름입니다. **핵심은 Entity가 원시 타입 대신 VO를 필드로 가지는 것입니다.**

### 3.1 전체 흐름도

```mermaid
graph LR
    Client -->|JSON| Controller
    Controller -->|DTO| Service
    
    subgraph Service Layer
        Service -->|1. DTO에서 값 추출| DTO_Value[Raw Value]
        DTO_Value -->|2. VO 생성 (검증)| VO[VO Object]
        VO -->|3. Entity 생성자에 주입| Entity[Entity Object]
    end
    
    Entity -->|4. JPA (Auto Mapping)| DB[(Database)]
```

### 3.2 단계별 상세 설명

**1. Client → Controller (DTO 사용)**
*   클라이언트는 JSON 데이터를 보냅니다.
*   Controller는 이를 `SignupRequest`(DTO)로 받습니다.
*   이때 `@Valid` 등을 통해 1차적인 형식 검증을 수행할 수 있습니다.

**2. Controller → Service**
*   Service는 DTO를 전달받습니다.

**3. Service (VO 변환 및 Entity 생성) - 핵심!**
*   DTO의 원시 값(`String email`)을 사용하여 **VO 객체(`new Email(dto.email)`)를 생성**합니다.
*   이 과정에서 VO의 생성자가 실행되며 **강력한 도메인 검증**이 수행됩니다. (실패 시 예외 발생)
*   검증을 통과한 VO 객체 **통째로** `UserEntity`의 생성자에 전달합니다.

**4. Entity & DB (JPA 매핑)**
*   `UserEntity`는 `Email` VO를 `@Embedded` 필드로 가지고 있습니다.
*   JPA는 `Email` VO 내부의 값을 추출하여 DB 테이블의 컬럼(`email_address`)에 저장합니다.

### 3.3 코드 예시

**Email VO (`@Embeddable`)**
```java
@Embeddable
public class Email {
    @Column(name = "email_address")
    private String value;

    protected Email() {} // JPA용

    public Email(String value) {
        if (!value.contains("@")) throw new IllegalArgumentException("Invalid Email");
        this.value = value;
    }
    // equals, hashCode...
}
```

**User Entity (`@Entity`)**
```java
@Entity
public class User {
    @Id @GeneratedValue
    private Long id;

    @Embedded // VO를 필드로 가짐
    private Email email; 

    protected User() {}

    public User(Email email) { // 생성자에서 VO를 받음
        this.email = email;
    }
}
```

**Service Logic**
```java
public void signup(SignupRequest dto) {
    // 1. VO 생성 (여기서 검증됨)
    Email email = new Email(dto.getEmail()); 

    // 2. Entity 생성 (VO 주입)
    User user = new User(email);

    // 3. 저장
    userRepository.save(user);
}
```

---

## 4. 결론: 언제 VO를 도입해야 할까?

*   **지금 당장은:** 현재 프로젝트 단계(초기/파일럿)에서는 **DTO + Entity + @Valid** 조합으로 빠르게 개발하는 것을 권장합니다.
*   **추후 고도화 시:**
    *   특정 값(이메일, 좌표, 돈 등)의 검증 로직이 여러 서비스에 흩어져 중복될 때.
    *   Entity의 필드가 너무 많아져서, 관련된 필드끼리 묶어 의미를 부여하고 싶을 때.
    *   도메인 로직이 복잡해져서 값 객체 스스로 행동(메서드)을 가질 필요가 있을 때.
    
    이때 위 가이드를 참고하여 부분적으로 VO를 리팩토링(Refactoring)하여 도입하는 것이 좋습니다.
