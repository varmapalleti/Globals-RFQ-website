# Backend Review — Global RFQ Identity Service

> Audited: `backend/identity-service` (Spring Boot 3.x, Java 21)
> Reviewer: Senior Frontend Engineer (AI)
> Date: 2026-10-12

---

## 1. API Contract Table

All endpoints share **base URL** `http://localhost:8081`.

**Important**: A `GatewaySecretFilter` runs before every request (including public ones).
When `GATEWAY_SECRET_ENABLED=true` the header `X-Gateway-Secret: <GATEWAY_SHARED_SECRET>` must be present on **every** browser request or the backend returns 403.
In `dev` profile the env var defaults to `true` but the secret must still be set.
See §4 Issue #2 for the full impact on the frontend.

### Auth Endpoints (`/api/auth`)

| # | Method | Path | Auth Required | Request Body Fields | Validation | Success Status | Success Response Shape |
|---|--------|------|---------------|---------------------|------------|----------------|------------------------|
| 1 | POST | `/api/auth/login` | No | `officialEmail` (string), `password` (string) | email: NotBlank, Email, Size≤190; password: NotBlank, Size≤128 | 200 | `ApiResponse<AuthResponse>` |
| 2 | POST | `/api/auth/refresh` | No (cookie) | — (reads `refresh_token` cookie) | — | 200 | `ApiResponse<AuthResponse>` |
| 3 | POST | `/api/auth/logout` | No (cookie) | — (reads `refresh_token` cookie) | — | 200 | `ApiResponse<Void>` |
| 4 | GET  | `/api/auth/me` | Yes (JWT Bearer) | — | — | 200 | `ApiResponse<UserSummary>` |
| 5 | POST | `/api/auth/forgot-password` | No | `emailType` (OFFICIAL\|PERSONAL), `email` (string) | emailType: NotNull; email: NotBlank, Email, Size≤190 | 200 | `ApiResponse<Void>` |
| 6 | POST | `/api/auth/verify-reset-otp` | No | `emailType`, `email`, `otp` (string) | emailType: NotNull; email: NotBlank, Email, Size≤190; otp: NotBlank, pattern `^\d{6}$` | 200 | `ApiResponse<ResetTokenResponse>` |
| 7 | POST | `/api/auth/reset-password` | No | `resetToken` (string), `newPassword` (string), `confirmPassword` (string) | resetToken: NotBlank; newPassword: NotBlank + @StrongPassword; confirmPassword: NotBlank; @AssertTrue passwords match | 200 | `ApiResponse<Void>` |
| 8 | POST | `/api/auth/verify-email` | No | `token` (string) | token: NotBlank, Size≤200 | 200 | `ApiResponse<Void>` |
| 9 | POST | `/api/auth/resend-verification` | No | `emailType`, `email` (string) | emailType: NotNull; email: NotBlank, Email, Size≤190 | 200 | `ApiResponse<Void>` |

### @StrongPassword Rules (PasswordPolicy.java)

The validator adds **one constraint violation per rule broken**. Field name in the error will be `newPassword`.

| Rule | Violation message |
|------|-------------------|
| Length ≥ 8 | "Password must be at least 8 characters long" |
| Length ≤ 64 (also checks UTF-8 byte count ≤ 72) | "Password must be at most 64 characters long" |
| At least one uppercase letter | "Password must contain at least one uppercase letter" |
| At least one lowercase letter | "Password must contain at least one lowercase letter" |
| At least one digit | "Password must contain at least one number" |
| At least one special character | "Password must contain at least one special character" |
| No whitespace | "Password must not contain spaces" |

---

## 2. Exact JSON Envelopes

### Success Envelope (`ApiResponse<T>`)

```json
{
  "success": true,
  "message": "Login successful",
  "data": { ... },
  "timestamp": "2026-10-12T09:15:00Z",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

> `data` is **omitted** (`NON_NULL`) when null — e.g., logout, forgot-password, reset-password, verify-email, resend-verification.

### Error Envelope (`ApiErrorResponse`)

```json
{
  "success": false,
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Validation failed",
  "path": "/api/auth/reset-password",
  "fieldErrors": [
    { "field": "newPassword", "message": "Password must be at least 8 characters long" },
    { "field": "newPassword", "message": "Password must contain at least one uppercase letter" },
    { "field": "confirmPasswordMatching", "message": "Passwords do not match" }
  ],
  "timestamp": "2026-10-12T09:15:00Z",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

> `fieldErrors` is **omitted** when null (non-validation errors).
> `@AssertTrue` field name is the method name minus `is` prefix with first char lowercased → `confirmPasswordMatching`.

### `AuthResponse` shape (in `data`)

```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": 1,
    "fullName": "System Administrator",
    "officialEmail": "admin@globalrfq.com",
    "officialEmailVerified": true,
    "personalEmailVerified": false,
    "status": "ACTIVE",
    "mustChangePassword": true,
    "roles": ["SUPER_ADMIN"]
  }
}
```

### `ResetTokenResponse` shape (in `data`)

```json
{
  "resetToken": "abc123...",
  "expiresInSeconds": 600
}
```

### `X-Correlation-Id` Header

- **Inbound**: Client may send `X-Correlation-Id: <8–64 chars, alphanum/dash/underscore>`. Validated by regex `^[A-Za-z0-9\-_]{8,64}$`.
- **Outbound**: The server always echoes a correlation ID in the response header `X-Correlation-Id` (either the sanitized inbound or a server-generated UUID).
- The same ID appears in the JSON response body as `correlationId`.
- Frontend should read the response header and surface it on unexpected errors for support tracing.

---

## 3. Cookie Details & Token Lifetimes

### Refresh Token Cookie

| Property | Value |
|----------|-------|
| Cookie name | `refresh_token` |
| Path | `/api/auth` |
| HttpOnly | `true` |
| SameSite | `Strict` |
| Secure | `false` in dev (`COOKIE_SECURE=false`), `true` in prod |
| Max-Age | Equals `refresh-token-expiry-days` in seconds |

### Token Lifetimes (from `application.yml`)

| Token | Lifetime |
|-------|----------|
| JWT Access Token | 15 minutes (`access-token-expiry-minutes: 15`) → 900 seconds |
| Refresh Token | 7 days (`refresh-token-expiry-days: 7`) |
| OTP | 10 minutes (`otp-expiry-minutes: 10`) |
| Reset Token (after OTP verified) | 10 minutes (hard-coded in `PasswordResetService.RESET_TOKEN_MINUTES = 10`) |
| Email Verification Token | 24 hours (`verification-token-expiry-hours: 24`) |

### Security Limits

| Setting | Value |
|---------|-------|
| Max failed login attempts before lock | 5 |
| Account lock duration | 15 minutes |
| Max OTP verification attempts | 3 |
| Max OTP requests per hour | 3 |

### Refresh Token Rotation

Tokens rotate on every `/refresh` call. The old hash is marked `revoked=true` and `replacedBy` is set.
If a revoked token is replayed → ALL user tokens are revoked immediately (token reuse detection).

---

## 4. Issues Found

### HIGH Priority

#### H-1: Gateway Secret Blocks All Browser Requests
**File**: `GatewaySecretFilter.java`, `SecurityConfig.java`, `application.yml`

**Problem**: `GATEWAY_SECRET_ENABLED` defaults to `true`. Every request (including public auth endpoints) is blocked with HTTP 403 unless `X-Gateway-Secret` header is present. A browser SPA cannot safely store or transmit a shared secret — the secret would be visible to anyone who opens DevTools.

**Impact**: The entire application will fail in a real deployment where a reverse proxy gateway (nginx, AWS API Gateway) is the intended caller. In dev mode without such a proxy, you must either:
- Set `GATEWAY_SECRET_ENABLED=false` in `.env` for local dev, OR
- Proxy all frontend requests through a local nginx/caddy that adds the header.

**Suggested Fix for Backend** (do not implement — report only): The filter should be disabled by default for dev profile or the frontend should be served through the gateway. For the frontend: configure `VITE_GATEWAY_SECRET_ENABLED` as false by default in dev, and document that the frontend Vite dev server proxy must add the header in the production path.

**Frontend Action**: Add `X-Gateway-Secret` header injection in the Axios interceptor when `VITE_GATEWAY_SECRET` env var is set. In dev, point the Vite proxy at the backend and inject the header server-side there. Document this clearly.

---

#### H-2: CORS Config Only Active for `dev` Profile — No Production CORS
**File**: `CorsConfig.java` — annotated `@Profile("dev")`

**Problem**: CORS is **only configured in the `dev` profile**. In `prod` profile, there is no `CorsConfigurationSource` bean. Spring Security will apply its default CORS behavior (deny all cross-origin requests). This will break the browser client in production.

**Suggested Fix**: Remove `@Profile("dev")` and parameterize origins from `app.cors.allowed-origins`.

---

#### H-3: `StrongPassword` Validation Produces Multiple Field Errors Under One Field Name
**File**: `StrongPasswordValidator.java`, `GlobalExceptionHandler.java`

**Problem**: The custom `@StrongPassword` validator adds multiple constraint violations to the same field (`newPassword`). Spring's `MethodArgumentNotValidException` handler collects them correctly, but the `fieldErrors` list will contain **multiple entries with field = "newPassword"**. The frontend must handle this: instead of `setError("newPassword", ...)` with a single message, it must collect **all messages** for `newPassword` and the password checklist must cross-reference them.

**Frontend Action**: In the error normalizer, when mapping to form fields, collect multiple messages per field as an array. The Zod `reset-password` schema must match all 7 rules. ✅ Handled in implementation.

---

#### H-4: `@AssertTrue` Field Name is `confirmPasswordMatching` (Not `confirmPassword`)
**File**: `ResetPasswordRequest.java`

**Problem**: The `@AssertTrue(message = "Passwords do not match")` method is `isConfirmPasswordMatching()`. Spring reports the field name as `confirmPasswordMatching` (method name minus `is`, first char lowercased). The frontend error mapping **must** handle this field name, not `confirmPassword`.

**Frontend Action**: Map `confirmPasswordMatching` → the confirmPassword input field. ✅ Handled in implementation.

---

### MEDIUM Priority

#### M-1: `personal_email` Is NOT NULL in V1 Schema but Nullable in Entity/Service
**File**: `V1__create_identity_schema.sql` (line 16), `User.java` (line 41), `PasswordResetService.java`

**Problem**: The V1 SQL has `personal_email VARCHAR(150) NOT NULL UNIQUE` — but the JPA entity has `@Column(name="personal_email", unique=true)` with no `nullable=false`, and the service code does `if (user.getPersonalEmail() != null ...)`. V5 migration does not alter the `users` table so the DB constraint remains. If an admin tries to create a user without a personal email it will fail at DB level with a confusing error. The frontend should not ask for personal email in the current screens, but OTP flow allows `emailType=PERSONAL`.

**Frontend Action**: Do not offer "Personal email" as an option in forgot-password / resend-verification unless the product requirement confirms personal email registration is supported.

---

#### M-2: `User.java` Maps `created_at` Column to `updatedAt` Field
**File**: `User.java` (line 76–77)

```java
@Column(name="created_at", insertable = false, updatable = false)
private Instant updatedAt;   // ← WRONG: field is updatedAt but column is created_at
```

The `updated_at` column (which exists in the DB) is never mapped. This is a backend bug — not surfaced to frontend but indicates `UserSummary` will never have `updatedAt`. **No frontend impact** since `UserSummary` doesn't expose these fields.

---

#### M-3: OTP Attempt Limit Is `otpMaxAttempts` (Config = 3) but Schema Default = 5
**File**: `V1__create_identity_schema.sql` (line 76: `max_attempts INT NOT NULL DEFAULT 5`), `AppSecurityProperties.java` (`otp-max-attempts: 3`)

The DB column `max_attempts` is never actually used by the service — the service reads from `AppSecurityProperties.otpMaxAttempts()` (= 3). The schema column is dead code. **No frontend impact**, but the UI should display "3 attempts" messaging.

---

#### M-4: SameSite=Strict Cookie Will Be Dropped on Top-Level Navigation
**File**: `RefreshCookieService.java`

**Problem**: `SameSite=Strict` means the cookie is NOT sent when the user arrives at the app from an external link (e.g., email verification link → `/verify-email?token=...`). This is correct for the refresh token (it's a security feature). However, it means the user's session is not restored after they click an email link and navigate back. The refresh-on-startup flow handles this correctly since it gracefully handles a missing cookie.

**Frontend Action**: The session restoration on app start already handles the missing cookie case → redirect to login. ✅ No problem.

---

#### M-5: No Rate-Limit Response When OTP Rate-Limited — Silent 200
**File**: `PasswordResetService.java` (lines 88–92)

When `recent >= otpMaxRequestsPerHour`, the service **silently returns** (no OTP sent, no error thrown). The controller always returns the same generic 200 message. This is intentional anti-enumeration — but the UX impact is that a user who genuinely hit the limit will be confused why they never receive an OTP.

**Frontend Action**: The UI should always show "Check your inbox" after step 1 and provide clear instructions: "If you don't receive the code in 2 minutes, try again later (max 3 per hour)."

---

#### M-6: `GatewaySecretFilter` Not Registered as Spring Bean — Manual Instantiation Only
**File**: `SecurityConfig.java` (line 43), `GatewaySecretFilter.java`

`GatewaySecretFilter` is instantiated manually with `new` inside `securityFilterChain()`. This means it won't benefit from Spring lifecycle hooks. Not a frontend concern but worth noting.

---

### LOW Priority

#### L-1: `ConstraintDeclarationException` Handler Has Wrong Parameter Type
**File**: `GlobalExceptionHandler.java` (lines 48–52)

```java
@ExceptionHandler(ConstraintDeclarationException.class)
public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, ...) {
```

The `@ExceptionHandler` annotation lists `ConstraintDeclarationException` but the method parameter is `ConstraintViolationException`. Spring matches on the parameter type, so this handler actually catches `ConstraintViolationException`, not `ConstraintDeclarationException`. The annotation value is misleading. Low frontend impact since validation errors still produce the same envelope.

---

#### L-2: `log.warn` Logs Business Error Incorrectly
**File**: `GlobalExceptionHandler.java` (line 37)

```java
log.warn("Business error [{}]", ex.getErrorCode(), ex.getMessage());
```

Only one `{}` placeholder but two arguments. SLF4J will use `ex.getErrorCode()` for `{}` and attach `ex.getMessage()` as a second argument (treated as a throwable if it's a `Throwable`). Should be `"Business error [{}]: {}"` with two placeholders. Low severity — just log noise.

---

#### L-3: `app.cors.allowed-origins` is Comma-Separated String — Not a YAML List
**File**: `application.yml` (line 40)

```yaml
app:
  cors:
    allowed-origins: http://localhost:5173
```

`CorsConfig.java` splits on `,` manually. This works but is non-idiomatic. Adding a second origin (e.g., staging) requires knowing to use `http://localhost:5173,https://staging.globalrfq.com` with no spaces or to understand the split logic. Low risk but could cause bugs.

---

#### L-4: `log-sensitive-in-mock: true` in `dev` Profile Logs OTP Codes
**File**: `application-dev.yml`

In dev, `log-sensitive-in-mock: true` means the mock email sender will log OTPs and verification links to the console. This is intentional for development. Acceptable, but devs must not commit real credentials or run with this setting in any shared environment.

---

#### L-5: `SecurityErrorWriter` — Verify It Writes `X-Correlation-Id` in Error Responses
**File**: `SecurityErrorWriter.java` (not read directly)

Errors written by `GatewaySecretFilter`, `CustomAuthenticationEntryPoint`, and `CustomAccessDeniedHandler` bypass the `GlobalExceptionHandler`. Need to verify `SecurityErrorWriter` also sets `X-Correlation-Id`. The `ApiErrorResponse.of()` calls `MDC.get(CorrelationIdFilter.MDC_KEY)` which should work since `CorrelationIdFilter` runs before security filters (it's `@Order(HIGHEST_PRECEDENCE)`). ✅ Likely fine.

---

## 5. Gaps — APIs the UI Needs That Don't Exist

The following screens/features are explicitly requested but have **no backend endpoint**. I have created typed stubs marked `// TODO: Backend endpoint missing`.

| Gap | Screen That Needs It | Stub Location |
|-----|---------------------|---------------|
| **Change Password** (authenticated user changing their own password, required when `mustChangePassword=true`) | Post-login forced change | `src/features/auth/api/authApi.ts` — `changePassword` stub |
| **User Management** — Create user, list users, assign roles | Admin dashboard | `src/features/users/api/usersApi.ts` — full stub |
| **Profile Management** — Update personal email, phone, fullName | Profile page | `src/features/profile/api/profileApi.ts` — full stub |

### Impact on Screens Built

- **Seeded admin has `mustChangePassword=true`**: After login, the frontend detects this and must route to a change-password page. But the change-password API does not exist. The frontend will show an informational screen explaining the situation and listing the steps when the backend is ready.
- **User Management screen**: NOT built. Listed as missing.
- **Profile screen**: NOT built. Listed as missing.

---

## 6. Verified Known Backend Facts

| Fact | Verified? | Notes |
|------|-----------|-------|
| Base URL `http://localhost:8081` | ✅ | Assumed; no `server.port` in yml — Spring default is 8080. **Discrepancy**: no explicit port found. Dev may run on 8080 unless overridden by `.env`. |
| Auth endpoints under `/api/auth` | ✅ | Confirmed: `@RequestMapping("/api/auth")` |
| Public: login, refresh, logout, forgot-password, verify-reset-otp, reset-password, verify-email, resend-verification | ✅ | Confirmed in `SecurityConfig.PUBLIC_POST[]` |
| Protected: `GET /me` | ✅ | Not in PUBLIC_POST list, `anyRequest().authenticated()` covers it |
| Access token in JSON body, in-memory only | ✅ | `AuthResponse.accessToken` field |
| Refresh token cookie name `refresh_token` | ✅ | `RefreshCookieService.COOKIE_NAME = "refresh_token"` |
| Refresh token cookie Path `/api/auth` | ✅ | `COOKIE_PATH = "/api/auth"` |
| SameSite=Strict | ✅ | `sameSite("Strict")` |
| Refresh tokens rotate | ✅ | New token created, old marked revoked |
| Login failures are generic (except `ACCOUNT_NOT_VERIFIED`) | ✅ | `INVALID_CREDENTIALS` for locked, disabled, wrong password, user not found. `ACCOUNT_NOT_VERIFIED` (403) for unverified email |
| Forgot password always returns 200 | ✅ | Controller always returns generic message |
| OTP: 6 digits, valid 10 min, 5 attempts → actually 3 attempts (config), 3 requests/hr | ✅ | Config: `otpMaxAttempts: 3`, `otpMaxRequestsPerHour: 3` — stated "5 attempts" in brief is **incorrect** — actual is **3** |
| Reset token valid 10 min | ✅ | `RESET_TOKEN_MINUTES = 10` hard-coded |
| Password policy: 8–64 chars, uppercase, lowercase, digit, special, no spaces | ✅ | `PasswordPolicy.java` |
| `mustChangePassword` in login response | ✅ | `UserSummary.mustChangePassword` |
| Email verification link: `{frontend}/verify-email?token=...` | ✅ | `EmailVerificationService` line 72 |
| `expiresInSeconds` in AuthResponse | ✅ | `long expiresInSeconds` |

### Critical Discrepancy Found

> **Port**: The brief says base URL is `http://localhost:8081`. The `application.yml` does not set `server.port`. Spring Boot defaults to `8080`. If the dev `.env.example` sets `SERVER_PORT=8081`, it would be `8081`. Treat as configurable via `VITE_API_BASE_URL` in the frontend `.env`.

> **OTP Attempts**: The brief says "5 attempts" but `application.yml` sets `otp-max-attempts: 3`. The frontend UI must show the correct value from UX copy (use 3, or make it configurable).
