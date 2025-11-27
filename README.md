# Authentication Module (Docker + Spring Boot)

This repository packages the authentication module described in `DEV_SPECS.md` into a runnable Docker Compose environment with the Spring Boot auth server, PostgreSQL, and Redis. The shared gateway/static front-end now lives under `0.Web/` and proxies requests into this stack over the `auth-shared` Docker network.

## Stack
- **Auth Service:** Java 17 / Spring Boot 3.2 / Gradle Kotlin DSL
- **Database:** PostgreSQL 15 (user data)
- **Cache:** Redis 7 (refresh tokens & blacklist-ready)
- **Gateway/Static:** See `0.Web/` (Nginx + React static bundle connected through the shared Docker network)

## Running Locally
```bash
# Create the shared bridge network once (safe to skip if it already exists)
docker network create auth-shared

# Build the auth-service image and start its dependencies
docker compose up --build

# (Optional) From 0.Web/, start the gateway/static stack so http://localhost:8080 routes through Nginx
cd ../0.Web && docker compose up --build

# Auth service remains directly reachable on http://localhost:8082 for debugging
```

Default credentials are defined inside `docker-compose.yml` (database user/password `authuser` / `authpass`). JWT secrets and TTLs can also be overridden with environment variables if required.

> **JWT secret length:** HS512 requires a key that is **at least 64 bytes (512 bits)** long. The default value in `docker-compose.yml` satisfies this requirement; if you change `SECURITY_JWT_SECRET`, make sure the new secret meets the same minimum length or the service will reject signing operations.

## Key Files
- `docker-compose.yml` – Orchestrates PostgreSQL, Redis, and auth-service. Also attaches the service to the external `auth-shared` Docker network used by the web gateway.
- `auth-service/` – Spring Boot project (JWT issuance/validation, signup/login/refresh APIs, `/auth/validate` endpoint, Redis refresh-token persistence).

## Auth API Quickstart
```bash
# Signup
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123","nickname":"soon"}'

# Login (returns access & refresh token)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123"}'

# Call a protected endpoint via gateway (automatically triggers /internal/auth)
curl http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <ACCESS_TOKEN>'

# Refresh the tokens
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

`/auth/validate` and `/api/v1/auth/validate` both send `X-User-Id` headers so the Nginx gateway in `0.Web` can forward the authenticated principal downstream. Failed validations bubble up to the `@unauthorized` location defined in `0.Web/nginx/app.conf`, returning the JSON body expected by the spec.

## Development Notes
- Redis stores refresh tokens with TTLs that match the JWT refresh duration so logout/blacklist behavior can be layered on later.
- Security is fully stateless: Spring Security is configured to permit only signup/login/refresh/validate while every other endpoint requires a valid JWT.
- `gradle` wrapper is disabled to keep the Docker image lean; the multi-stage Dockerfile handles builds without needing Gradle installed on the host.
