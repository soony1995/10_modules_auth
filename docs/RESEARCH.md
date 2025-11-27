# Authentication Module: Research & Architecture Decisions

이 문서는 인증 모듈 개발을 위한 **아키텍처 의사결정(ADR)**과 **상세 구현 가이드**를 통합한 문서입니다.

---

## Part 1. 아키텍처 의사결정 기록 (Architecture Decision Record)

프로젝트 진행 과정에서 논의되고 확정된 주요 기술적 의사결정 사항입니다.

### 1. 인프라 및 배포 환경 (Infrastructure)
*   **Target Environment:** Minikube (Kubernetes)
*   **Scaling Strategy:** 다중 파드(Pod) 실행 및 오토스케일링 고려.
*   **Build Tool:** Gradle (Kotlin DSL 권장) - 멀티 모듈 프로젝트 구성을 위함.
*   **Module Strategy:** 소스 코드는 **Gradle Multi-module**로 관리하되, 배포는 각 모듈을 독립된 **Docker Container(MSA)**로 실행.

### 2. 인증 아키텍처 (Authentication Architecture)
*   **Stateless vs Stateful:** **Stateless (JWT)** 방식 채택.
    *   **이유:** K8s 환경에서 파드의 잦은 생성/소멸과 로드밸런싱 이슈 해결. Sticky Session이나 Session Clustering 비용 제거.
*   **Token Storage:**
    *   **Access Token:** 클라이언트(메모리/헤더) 관리.
    *   **Refresh Token:** **Redis** (Shared Storage)에 저장.
    *   **이유:** 다중 서버 환경에서 토큰 만료 처리 및 강제 로그아웃(Blacklist) 기능을 모든 파드가 공유하기 위함.

### 3. 마이크로서비스 간 통신 보안 (Inter-Service Security)
*   **User Context Propagation:**
    *   사용자 요청 시 Gateway/클라이언트가 보낸 `Authorization` 헤더(JWT)를 내부 서비스 호출 시에도 그대로 전파(Relay).

*   **Verification Strategy Decision (Changed):** **Nginx `auth_request` Module**
    *   **[Deprecated] Previous Decision:** 공통 라이브러리 (Shared Library)
        *   *폐기 사유:* 라이브러리 방식은 각 마이크로서비스마다 검증 코드를 중복 실행해야 하며, 라이브러리 업데이트 시 모든 서비스를 재배포해야 하는 번거로움이 있음.
    *   **New Decision:** **Centralized Auth via Nginx (Gateway)**
        *   **설명:** Nginx가 API Gateway 역할을 수행하며, `auth_request` 모듈을 통해 모든 요청을 먼저 **Auth Service**에 보내 검증함. 검증이 통과된(200 OK) 요청만 뒷단의 서비스로 전달.
        *   **채택 이유:**
            1.  **완벽한 관심사 분리:** 비즈니스 서비스(주문, 결제 등)는 인증 로직에서 완전히 해방됨 (코드 간결화).
            2.  **중앙 제어:** 보안 정책 변경 시 Auth Service와 Nginx 설정만 바꾸면 전체 시스템에 즉시 적용됨.
            3.  **효율성:** Nginx를 웹 서버이자 Gateway로 활용하여 인프라 복잡도를 낮춤.

### 4. 데이터베이스 설계 전략
*   **Selected Database:** **PostgreSQL**
    *   **채택 이유:**
        1.  **표준 준수:** SQL 표준 준수율이 높아 복잡한 쿼리 처리에 안정적임.
        2.  **JSON 지원:** 추후 인증 모듈 외에 비정형 데이터(로그 등)를 저장할 때 JSONB 타입 등 강력한 NoSQL 기능을 활용할 수 있음.
        3.  **환경 일치:** `Docker Compose`를 통해 로컬 개발 환경과 배포 환경(K8s)을 동일한 DB 엔진으로 유지하여 잠재적인 버그를 예방함.

*   **Schema:** 확장성을 위해 **Users(본체)**와 **Accounts(인증 수단)** 테이블 분리.

---

## Part 2. 로그인 모듈 설계 및 구현 통합 가이드 (Detailed Guide)

아래 내용은 인증 시스템 구축을 위한 상세 가이드라인 및 레퍼런스입니다.

### 1. 요구사항 및 정책 정의 (Planning)

#### 1-1. 인증 수단 결정
*   **기본 수단:** 소셜 로그인(OAuth) + ID/비밀번호(Email).
*   **B2B/사내 시스템:** SSO(Google Workspace, Okta 등) + MFA 필수.

#### 1-2. 비밀번호 및 보안 정책
*   **비밀번호 정책:** 최소 길이(8~12자 이상) 준수, 흔한 비밀번호 차단.
*   **저장 방식:** 평문 저장 금지. `BCrypt` 사용.
*   **UX:** 로그인 실패 시 "아이디 또는 비밀번호가 올바르지 않습니다"로 메시지 통일(계정 존재 여부 은폐).

---

### 2. MFA (다중 요소 인증) 설계 전략

| 등급 | 조합 예시 | 특징 및 권장 대상 |
| :-- | :-- | :-- |
| **기본 (Low)** | 비밀번호 + SMS/이메일 OTP | 구현 용이, 피싱 취약. (일반 서비스 백업용) |
| **표준 (Mid)** | 비밀번호 + TOTP 앱 (Google Auth) | 보안성/구현 난이도 적절. (대부분의 서비스 권장) |
| **강력 (High)** | 비밀번호 + **패스키/FIDO2** | 피싱 저항성 강력, UX 우수. (핀테크, 관리자) |

---

### 3. 상세 구현 로직 (Implementation Flows)

#### 3-1. 토큰 기반 인증 (JWT) - **[채택된 방식]**
1.  **로그인:** 클라이언트 ID/PW 전송 → 서버 검증 → **Access Token**(짧은 수명) + **Refresh Token**(긴 수명) 발급.
2.  **저장:** Refresh Token은 **Redis**에 `key: userId, value: token` 형태로 저장.
3.  **요청:** 클라이언트는 `Authorization: Bearer <Token>` 헤더 전송 → 서버는 서명(Signature) 검증.
4.  **갱신:** Access Token 만료(401) → Refresh Token으로 재발급 요청 → 서버가 Redis 조회 및 검증 후 새 토큰 발급.

#### 3-2. 계정 통합 및 비밀번호 재설정 시퀀스

**A. 소셜 로그인 + 계정 통합 (Account Linking)**
1.  사용자가 소셜 로그인 시도.
2.  백엔드가 소셜 Provider로부터 `email` 획득.
3.  `users` 테이블에 해당 이메일이 존재하는지 확인.
    *   **없음:** 신규 가입 진행.
    *   **있음:** 기존 계정(`user_id`)을 찾아 `accounts` 테이블에 소셜 정보(`provider`, `sub_id`) 추가 (자동 통합).

**B. 비밀번호 재설정 (Password Reset)**
1.  **요청:** 이메일 입력.
2.  **발송:** **난수 코드** 생성 → Redis 저장(TTL 10분) → 이메일 발송 (링크 포함).
3.  **검증:** 사용자가 링크 클릭 및 새 비밀번호 입력 → 코드 검증.
4.  **처리:** 비밀번호 해시 업데이트 + **Redis의 Refresh Token 전체 삭제(강제 로그아웃)**.

---

### 4. 데이터베이스 스키마 설계 (ERD)

#### 1) Users (사용자 기본 정보)
```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    nickname        VARCHAR(50),
    role            VARCHAR(20) DEFAULT 'USER',
    status          VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, BANNED, LEAVE
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
```

#### 2) Accounts (인증 수단 / 소셜 연동)
```sql
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(50) NOT NULL,     -- 'local', 'google', 'kakao'
    provider_id     VARCHAR(255),             -- google sub 값 등
    password_hash   VARCHAR(255),             -- local일 때만 존재
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(provider, provider_id)
);
```

#### 3) LoginHistory (감사 로그)
```sql
CREATE TABLE login_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID,
    input_email     VARCHAR(255),
    is_success      BOOLEAN DEFAULT FALSE,
    fail_reason     VARCHAR(100),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP DEFAULT NOW()
);
```

---

### 5. 운영 및 보안 체크리스트 (Ops & Security)
1.  **HTTPS (TLS):** 모든 통신 암호화 필수.
2.  **Rate Limiting:** 로그인 시도 횟수 제한 (Brute-force 방어).
3.  **Audit Log:** 로그인 성공/실패, 정보 변경 등 중요 이벤트 기록.
4.  **Error Handling:** 보안상 모호한 에러 메시지 제공.
