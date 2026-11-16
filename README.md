# Fintech OAuth 2.0 / OIDC Microservices Platform

A banking microservices reference platform showing **OAuth 2.0**, **OpenID Connect** and **asymmetric (RS256) JWT**
authentication and authorization built only from free, open-source parts:
Java 17, Spring Boot 3.4 (Spring Security 6, Spring Cloud Gateway), Keycloak, PostgreSQL 16, Redis and Docker Compose.

> **Status:** Phase 1 is complete: build system, infrastructure, Keycloak realm and the `common-security` library.
> Phase 2 adds the gateway, account and transfer services, plus the full security architecture guide.

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
| `api-gateway` | Spring Cloud Gateway: edge routing, first-line JWT validation, explicit-origin CORS and Redis token-bucket rate limiting *(Phase 2)* |
| `account-service` | Resource server: stateless, `@PreAuthorize` RBAC (`CUSTOMER`, `ADMIN`, `AUDITOR`), ownership bound to JWT `sub` *(Phase 2)* |
| `transfer-service` | Resource server: user-token propagation vs. Client Credentials, Redis `jti` revocation *(Phase 2)* |
| `infra/` | Docker Compose stack, Keycloak realm import, PostgreSQL init script, shared service Dockerfile |

## Repository layout

```text
.
├── pom.xml                         # parent: Java 17, Spring Boot 3.4.x, Spring Cloud 2024.0.x BOM
├── common-security/                # shared security auto-configuration (see below)
├── api-gateway/                    # pom.xml (code in Phase 2)
├── account-service/                # pom.xml (code in Phase 2)
├── transfer-service/               # pom.xml (code in Phase 2)
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example                # copy to infra/.env (git-ignored)
│   ├── docker/service.Dockerfile   # multi-stage, non-root, layered JAR
│   ├── keycloak/realm-export.json  # banking-realm: RS256 keys, clients, roles, demo users
│   ├── postgres/init/01-create-databases.sh
│   └── scripts/generate-env.sh     # writes infra/.env with random secrets
└── .github/workflows/ci.yml        # Java 17 build + Keycloak realm/token smoke test
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

Prerequisites: Docker with Compose v2, JDK 17 and Maven 3.9+ for building outside Docker, and `openssl`.

```bash
# 1. Make the issuer hostname resolve on your machine (the containers already resolve it).
#    The token "iss" must be identical for your browser/curl and for the services.
echo "127.0.0.1 keycloak" | sudo tee -a /etc/hosts      # Windows: C:\Windows\System32\drivers\etc\hosts

# 2. Generate infra/.env with random secrets (or copy .env.example and edit).
./infra/scripts/generate-env.sh

# 3. Build the shared library.
mvn -B -pl common-security -am verify

# 4. Start the infrastructure. In Phase 1, start the backing services only:
docker compose -f infra/docker-compose.yml up -d postgres redis keycloak
```

Keycloak's admin console is at <http://keycloak:8080/admin>. The bootstrap admin credentials are in `infra/.env`.
Demo users: `alice.customer`, `bob.customer` (`CUSTOMER`), `carol.admin` (`ADMIN`) and `dave.auditor` (`AUDITOR`).

Smoke test, machine-to-machine token:

```bash
set -a; . infra/.env; set +a
curl -s -X POST http://keycloak:8080/realms/banking-realm/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=transfer-service-client \
  --data-urlencode "client_secret=${TRANSFER_SERVICE_CLIENT_SECRET}" | jq '{token_type, expires_in}'
```

Full PKCE, `401`/`403` and BOLA test walkthroughs ship with Phase 2 in `docs/SECURITY_ARCHITECTURE.md`.

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
