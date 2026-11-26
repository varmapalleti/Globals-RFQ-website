# Identity service implementation report

Updated: 2026-10-20

This report describes the current backend identity-service work. It supersedes the historical notes in `backend-review.md` where they disagree. No frontend files were changed.

## Implemented API surface

Identity-service listens on port 8081 by default and registers with Eureka. Client applications should call the API Gateway on port 8080; the gateway routes the identity API paths to this service. Success and error responses use the existing API envelopes.

### Authentication and account lifecycle

- `POST /api/auth/login`
- `POST /api/auth/refresh` — rotates the HttpOnly refresh cookie
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/change-password` — verifies the current password, applies the shared strong-password policy, clears `mustChangePassword`, revokes refresh sessions, clears the cookie, and sends notices to verified addresses
- `POST /api/auth/forgot-password`
- `POST /api/auth/verify-reset-otp`
- `POST /api/auth/reset-password`
- `POST /api/auth/verify-email`
- `POST /api/auth/resend-verification`
- `POST /api/auth/accept-invite` — accepts a purpose-specific, single-use invite token and sets the initial password

### User and role administration

- `GET /api/roles` — returns only roles the caller can assign
- `POST /api/users` — creates a pending account and queues an invitation; passwords are never supplied by an administrator
- `GET /api/users` — paginated user listing with search, role, status, and allowlisted sorting; page size is capped at 100
- `GET /api/users/{id}`
- `PATCH /api/users/{id}` — updates name, phone, or personal email; changed personal emails are unverified until verified
- `PUT /api/users/{id}/roles` — replaces roles subject to hierarchy rules
- `POST /api/users/{id}/freeze` and `POST /api/users/{id}/unfreeze`
- `POST /api/users/{id}/unlock`
- `POST /api/users/{id}/resend-invite`

### Audit

- `GET /api/audit-logs` — SUPER_ADMIN-only date, event, and user filters with pagination

## Authorization rules

Role ranks are `SUPER_ADMIN` (1), `ADMIN` (2), and business roles (3). A SUPER_ADMIN can manage ADMIN and business-role users and can assign ADMIN or business roles. Creating or assigning SUPER_ADMIN is disabled. An ADMIN can manage and assign business-role users only. Every target-management operation checks the current actor and target roles from the database, prevents self-management, and requires the actor to have a strictly higher rank. Freeze/unfreeze is SUPER_ADMIN-only; freezing records reason and actor, revokes refresh tokens, and makes existing access tokens fail the active-account check. Unfreeze restores the prior status.

## Security and reliability changes

- Access and refresh token rotation queries use pessimistic locks to serialize concurrent refreshes. Login updates are also serialized to avoid lost failed-attempt counts.
- Passwords use BCrypt; oversized login passwords are rejected through the generic invalid-credentials path.
- Refresh and logout require an allowed `Origin`, in addition to the HttpOnly, SameSite=Strict cookie. Production cookies are Secure.
- User role/status checks use database state for authorization-sensitive operations. Every bearer request also checks that the account remains ACTIVE; this adds one lightweight database lookup per bearer request.
- Notification outbox recipients and template variables are encrypted with AES-GCM before database storage. Delivery is retried up to five attempts with backoff. Delivery is at-least-once; a process failure after provider acceptance but before recording success can produce a duplicate email.
- New Flyway migrations V7 and V8 add role ranks, account status metadata, invitation purpose, optimistic locking, notification encryption/retry metadata, indexes, and email-column lengths aligned with the API limits. V1–V6 are left unchanged.
- V7 preserves existing outbox records. Existing unencrypted pending/processing rows are marked FAILED and require a fresh invitation/reset request; the retry worker will not send them.
- Daily cleanup removes expired refresh, verification, and reset-token data. Audit deletion is disabled by default; set `AUDIT_RETENTION_DAYS` only after the client’s retention requirements are agreed.

## Runtime configuration

- `SERVER_PORT` defaults to `8081`.
- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are required. Every Hikari connection sets the MySQL session timezone to UTC.
- `JWT_SECRET` must be Base64 that decodes to at least 32 bytes.
- `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` create the first administrator when provided; the password must satisfy the password policy. The bootstrap user has no personal email and must change the seeded password.
- The gateway secret is enabled by default in dev and production. Both api-gateway and identity-service require the same `GATEWAY_SHARED_SECRET` of at least 32 characters. The browser and Postman caller must never receive this secret.
- `EUREKA_SERVER_URL` configures registry discovery. Local defaults point to the authenticated development Eureka server; production must use an internal TLS endpoint and secret-managed credentials.
- Production requires `CORS_ALLOWED_ORIGINS` and `OUTBOX_ENCRYPTION_KEY`. The latter must be a stable secret of at least 32 bytes. Do not rotate it while encrypted outbox messages remain pending; key-versioned rotation needs a planned migration.
- `EMAIL_PROVIDER` defaults to `mock`; SES uses the AWS SDK default credential provider chain, `AWS_REGION`, `MAIL_FROM_ADDRESS`, optional `MAIL_FROM_NAME`, and optional `SES_CONFIGURATION_SET`. No AWS keys are stored in the repository. See [identity-aws-ses-setup.md](identity-aws-ses-setup.md) for verified-identity, sandbox, role, and event-telemetry setup. Mock email logging of sensitive message bodies is enabled only in the dev profile.

## Verification and release notes

After the temporary Redis removal, `mvnw -DskipTests compile` succeeded for identity-service, api-gateway, and eureka-server on Java 21. The Postman collection JSON parses and contains 22 requests. Runtime API calls and database-backed tests were not run here because they require the local MySQL configuration and seeded account; real SES delivery additionally requires an AWS profile/role, a verified sender, and SES account access. Migrations were not applied to a live database in this session. Redis and the gateway's shared IP rate limiter are intentionally removed during the local API-testing phase. Identity retains its database-backed account lockout and reset-OTP limits, but this temporary gateway configuration is not ready for internet-facing production. Before production use, restore shared gateway/edge rate limiting, run migration validation and integration/security tests against the exact MySQL version, and verify CORS origins, gateway header handling, SES delivery and event handling, retries, rate limits, and all role boundaries.

The root and dev profiles default to local development. Production must set the required environment variables through the deployment secret manager. Never copy the dev outbox key into production.
