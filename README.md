# Fintech OAuth 2.0 / OIDC Microservices Platform

A banking microservices reference platform showing **OAuth 2.0**, **OpenID Connect** and **asymmetric (RS256) JWT**
authentication and authorization built only from free, open-source parts:
Java 17, Spring Boot 3.4 (Spring Security 6, Spring Cloud Gateway), Keycloak, PostgreSQL 16, Redis and Docker Compose.

[![CI](https://github.com/Jyotipr45/fintech-oauth2-oidc-microservices/actions/workflows/ci.yml/badge.svg?branch=feat/oauth2-oidc-security-architecture)](https://github.com/Jyotipr45/fintech-oauth2-oidc-microservices/actions/workflows/ci.yml)

Every push is checked in CI with a Java 17 build, unit and slice tests, a Keycloak realm smoke test, and
an **end-to-end security suite** run against the full Docker Compose stack: 401/403, BOLA/IDOR, tampered and
`alg=none` tokens, ID-token misuse, idempotency, token propagation, velocity limits and `jti` revocation.

📘 **Deep dive:** [`docs/SECURITY_ARCHITECTURE.md`](docs/SECURITY_ARCHITECTURE.md) covers the token model, Mermaid sequence diagrams,
the authorization matrix, CSRF/BFF, the system-design patterns and `curl` walkthroughs.

## Architecture at a glance

```text
 Browser / SPA ──(Auth Code + PKCE S256)──▶ Keycloak (banking-realm, RS256 signing key)
      │                                          │  JWKS: /protocol/openid-connect/certs
      │ Bearer access token                      │  (public keys only)
      ▼                                          ▼
 api-gateway  ── JWT check, CORS, Redis rate limit ──▶ account-service   (RBAC + ownership / BOLA checks)
                                                 └──▶ transfer-service  (jti revocation, client_credentials
                                                                          or token propagation to account-service)
 PostgreSQL 16: one database + one role per service      Redis: rate-limit buckets, jti denylist
```

| Module | Role |
|---|---|
| `common-security` | Shared library: RS256-only `NimbusJwtDecoder` built from the issuer location, with `iss`/`aud`/`exp`/`nbf`/`sub`/`typ` validation, a Keycloak role-to-authority converter, RFC 7807 401/403 handlers and masked logging. It auto-configures for servlet and reactive stacks. |
| `api-gateway` | Spring Cloud Gateway (reactive): edge JWT validation, explicit-origin CORS, Redis token-bucket rate limiting per `sub`, per-route circuit breakers with fallbacks, GET-only retries, request-size limits |
| `account-service` | Resource server: stateless, `@PreAuthorize` RBAC (`CUSTOMER`, `ADMIN`, `AUDITOR`), owner-scoped SQL bound to JWT `sub` (BOLA/IDOR), and an atomic, idempotent internal ledger for machine clients only |
| `transfer-service` | Resource server: user-token propagation vs. Client Credentials, Redis `jti` denylist and per-user velocity limit (fail closed), Resilience4j retry/circuit breaker/bulkhead, `Idempotency-Key`, saga states, transactional outbox on a bounded `ThreadPoolExecutor` |
| `infra/` | Docker Compose stack, Keycloak realm import, PostgreSQL init script, shared service Dockerfile |

## Repository layout

```text
.
├── pom.xml                         # parent: Java 17, Spring Boot 3.4.x, Spring Cloud 2024.0.x BOM
├── common-security/                # shared security auto-configuration (see below)
├── api-gateway/                    # security, CORS, rate limit, circuit breaker, routes
├── account-service/                # accounts, RBAC, ownership, internal ledger, Flyway
├── transfer-service/               # transfers, token propagation, client credentials, outbox
├── docs/SECURITY_ARCHITECTURE.md   # architecture, diagrams, patterns, curl guide
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example                # copy to infra/.env (git-ignored)
│   ├── docker/service.Dockerfile   # multi-stage, non-root, layered JAR
│   ├── keycloak/realm-export.json  # banking-realm: RS256 keys, clients, roles, demo users
│   ├── postgres/init/01-create-databases.sh
│   └── scripts/
│       ├── generate-env.sh         # writes infra/.env with random secrets
│       └── pkce-login.sh           # terminal Authorization Code + PKCE login
└── .github/                        # CI: build, realm smoke test, full-stack e2e suite
```

## Security model, in short

- **Asymmetric keys only.** Keycloak signs access tokens with the private key of an `rsa-generated` RS256 key provider.
  The services hold no signing secret. They download **public** keys from the JWKS endpoint, cache them, and choose a key by `kid`.
  The decoder accepts only `RS256`, so `alg=none` and HMAC key-confusion tokens are rejected.
  Because keys are fetched at runtime, you can rotate keys without downtime.
- **Strict claim validation.** `exp` is required. `exp`/`nbf` are checked with a bounded clock skew. `iss` must match exactly.
  `aud` must name this service. `sub` must be present. `typ` must be `Bearer`, so ID tokens and refresh tokens are not accepted as access tokens.
- **No PII in tokens.** `profile` and `email` are *optional* scopes, so access tokens carry the user UUID (`sub`), roles and permissions only.
- **Least privilege.** Realm roles (`CUSTOMER`, `ADMIN`, `AUDITOR`) are composites of fine-grained `banking-api` client roles
  (`account:read`, `transfer:create`, ...). The machine client `transfer-service-client` holds only `account:balance-adjust`.
- **PKCE enforced.** `banking-web-pkce` is a public client with `pkce.code.challenge.method=S256`. The password, implicit and device grants are disabled.
- **Refresh token rotation.** `revokeRefreshToken=true`, `refreshTokenMaxReuse=0`.
- **Zero hardcoded secrets.** Every credential is an `${ENV_VAR}`. Keycloak substitutes the client secret and demo passwords at import time.
  `.env`, `*.pem`, `*.key` and `*.jks` are git-ignored, and CI fails if any are committed.
- **Logs never contain credentials.** The access log never records headers or query strings.
  The `%maskedMsg` / `%maskedEx` Logback converters redact Bearer/Basic credentials, JWTs and secret key/value pairs.

## Quick start (local)

Prerequisites: Docker with Compose v2, `openssl`, `jq`, and `python3` for the PKCE helper. JDK 17 and Maven 3.9+ are needed only to build outside Docker.

```bash
# 1. Make the issuer hostname resolve on your machine (containers already resolve it).
#    The token "iss" must be identical for your browser/curl and for the services.
echo "127.0.0.1 keycloak" | sudo tee -a /etc/hosts      # Windows: C:\Windows\System32\drivers\etc\hosts

# 2. Generate infra/.env with random secrets (or copy infra/.env.example and edit every CHANGE_ME).
./infra/scripts/generate-env.sh

# 3. Build and start everything: PostgreSQL, Redis, Keycloak, gateway, account and transfer services.
docker compose -f infra/docker-compose.yml up -d --build --wait

# 4. Sign in with Authorization Code + PKCE (opens a URL, catches the redirect locally).
ALICE=$(./infra/scripts/pkce-login.sh | jq -r .access_token)     # user: alice.customer
curl -s -H "Authorization: Bearer $ALICE" http://localhost:8000/api/v1/accounts/me | jq
```

| Endpoint | URL |
|---|---|
| API gateway | <http://localhost:8000> |
| Keycloak admin console | <http://keycloak:8080/admin> (bootstrap admin credentials are in `infra/.env`) |

Demo users: `alice.customer`, `bob.customer` (`CUSTOMER`), `carol.admin` (`ADMIN`) and `dave.auditor` (`AUDITOR`).
They share the password `KEYCLOAK_TEST_USER_PASSWORD` from `infra/.env`.

The [security architecture guide](docs/SECURITY_ARCHITECTURE.md#8-hands-on-testing-with-curl) has the full `curl` walkthrough: PKCE and Client Credentials tokens, 401/403/404, BOLA/IDOR, idempotency, revocation, rate limits and refresh-token rotation.

### API overview

| Method & path | Who |
|---|---|
| `GET /api/v1/accounts/me` | customer (own accounts) |
| `GET /api/v1/accounts` | admin, auditor |
| `GET /api/v1/accounts/{id}` | owner, admin, auditor (others get 404) |
| `POST /api/v1/accounts` | customer |
| `PATCH /api/v1/accounts/{id}/status` | admin |
| `POST /api/v1/accounts/{id}/deposits` | admin |
| `POST /api/v1/transfers` (needs `Idempotency-Key`) | customer, from own account |
| `GET /api/v1/transfers`, `GET /api/v1/transfers/{id}` | owner (`transfer:read-all` sees any) |
| `POST /api/v1/transfers/token-revocations` | any authenticated user (revokes the current token) |

### System design patterns used

API gateway · two-tier rate limiting (Redis token bucket per user + per-user transfer velocity limit) ·
circuit breakers with fallbacks · retry with exponential back-off and jitter (idempotent calls only) · bulkhead ·
timeouts · bounded `ThreadPoolExecutor` with back-pressure · idempotency keys · orchestration saga ·
transactional outbox with `SKIP LOCKED` relay · idempotent receiver · ordered pessimistic locks plus optimistic locking ·
database per service · anti-corruption layer · ports & adapters · zero trust · least privilege · fail closed.
The [patterns table](docs/SECURITY_ARCHITECTURE.md#7-system-design-patterns) maps each one to the code.

## Using `common-security` in a service

```yaml
banking:
  security:
    issuer-uri: ${KEYCLOAK_ISSUER_URI}
    audiences: account-service
    clock-skew: 30s
    jwks-cache-ttl: 5m
```

The library registers a `JwtDecoder` (or `ReactiveJwtDecoder`), a `JwtAuthenticationConverter`, RFC 7807
`AuthenticationEntryPoint`/`AccessDeniedHandler` beans and a masked access-log filter. Each service still declares
its own `SecurityFilterChain`, so its authorization rules stay explicit. For masked logging, add this to `logback-spring.xml`:

```xml
<include resource="banking-logback-masking.xml"/>
<root level="INFO"><appender-ref ref="BANKING_MASKED_CONSOLE"/></root>
```

## Author

**Jyotiprakash Parida**

## License

[MIT](LICENSE) © 2026 Jyotiprakash Parida
