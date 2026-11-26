# Identity API testing with Postman

This guide covers the identity-service API routed through the API Gateway. The importable collection is [GlobalRFQ-Identity-Service.postman_collection.json](../backend/postman/GlobalRFQ-Identity-Service.postman_collection.json). It does not require the frontend, Docker, or Redis.

## 1. Start the local services

You need Java 21, a local MySQL server/database, and the four backend processes below. Flyway applies the identity migrations on identity-service startup. Maven does not load `.env` files automatically; configure variables in each IntelliJ run configuration or the PowerShell terminal that launches that service. See [backend-service-platform.md](backend-service-platform.md) for the environment-variable setup and secret-generation commands.

Start in this order:

1. `backend/eureka-server` on port `8761`.
2. `backend/identity-service` on port `8081`, with a reachable MySQL database, a Base64 `JWT_SECRET` that decodes to at least 32 bytes, the local `GATEWAY_SHARED_SECRET`, and `EMAIL_PROVIDER=mock`.
3. `backend/api-gateway` on port `8080`, with the same `GATEWAY_SHARED_SECRET` and the local `EUREKA_SERVER_URL`.

The local Eureka URL defaults to `http://eureka:local-eureka-password-change-me@localhost:8761/eureka/`. Wait until the Eureka dashboard shows both `API-GATEWAY` and `IDENTITY-SERVICE` before sending API requests.

Check service health:

| Service | URL |
| --- | --- |
| Gateway | `GET http://localhost:9080/actuator/health` |
| Identity | `GET http://localhost:8081/actuator/health` |
| Eureka | `GET http://localhost:8762/actuator/health` |

These health checks are on management/direct service ports. Use the gateway's public API port `8080` for API requests.

## 2. Import the collection and create an environment

In Postman, choose **Import**, select the collection JSON linked above, then create and select a local environment with these values:

| Variable | Example |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `origin` | `http://localhost:5173` |
| `officialEmail` | the bootstrapped administrator's official email |
| `personalEmail` | an unused test email address |
| `password` | the bootstrapped administrator's password |
| `newPassword` | a new strong test password |

The collection already declares `accessToken`, `userId`, `otp`, `resetToken`, `verificationToken`, and `inviteToken`. Do not add duplicate blank environment variables for them: Postman environment values override collection variables, which would prevent the collection scripts from using saved values. Keep the environment local and do not sync real passwords, access tokens, invitation links, reset tokens, or OTPs to a shared workspace.

Postman does not need the internal `X-Gateway-Secret`. The gateway creates that header for the private service hop. Do not put the secret in Postman or share it with a browser/client.

## 3. Recommended happy-path test sequence

1. Run **Login** with the bootstrapped administrator. The collection stores `data.accessToken` in `accessToken`; Postman should retain the `refresh_token` HttpOnly cookie.
2. Run **Get current user** and **List assignable roles**. Confirm the user is `ACTIVE` and the returned roles are appropriate for the administrator.
3. Run **Create user and send invitation** with a new, unique pair of email addresses and an assignable business role such as `REVIEWER`.
4. In the dev identity-service logs, find the mock invitation email and copy its invitation token into `inviteToken`. Dev mock email logging is for local testing only; never enable sensitive-body logging in a deployed environment.
5. Run **Accept invitation** with that token and a strong password. Then run **Login** using the new user's official email and password. Update the selected Postman environment's `officialEmail` and `password` values first. Use this lower-privilege account to test role denial cases.
6. Use the administrator token for **List users**, **Get user by ID**, **Update user profile**, **Replace user roles**, **Freeze**, **Unfreeze**, **Unlock**, and **Resend user invitation**. Use the `userId` saved by the create request.
7. For email verification, resend to a test address, copy the verification token from the mock message in the dev logs, and run **Verify email**.
8. For password reset, run **Request password reset OTP**, copy the six-digit OTP from the mock message, run **Verify password reset OTP**, then use the returned `resetToken` in **Reset password**. Update the Postman environment's `password` after a reset or password-change test before logging in again.
9. Run **Search audit logs** as `SUPER_ADMIN` and confirm that the actions above produced entries.
10. Test **Refresh access token** while the cookie still exists. Test **Logout** last because it clears the refresh cookie. Both requests include the allowed `Origin` header from `{{origin}}`.

Use new email addresses when repeating the create-user test; email addresses must be unique. To test lockout/unlock, use a disposable test account rather than the bootstrapped administrator. Five consecutive failed logins lock the account for the configured 15 minutes; use **Unlock user** to recover it sooner.

## 4. API reference and request examples

All successful controller responses use an envelope like:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<short-lived-access-token>",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "user": {
      "id": 1,
      "officialEmail": "admin@example.com",
      "status": "ACTIVE",
      "roles": ["SUPER_ADMIN"]
    }
  },
  "timestamp": "2026-10-20T12:00:00Z",
  "correlationId": "<request-id>"
}
```

Exact message text and nullable fields can differ by endpoint. Authentication cookies are returned in `Set-Cookie`, not inside `data`.

### Authentication and account lifecycle

| Method and path | Authentication | Purpose / test notes |
| --- | --- | --- |
| `POST /api/auth/login` | Public | Uses the official email and password. Saves `data.accessToken` and sets the refresh cookie. |
| `POST /api/auth/refresh` | Refresh cookie + allowed `Origin` | Rotates the refresh cookie and returns a new access token. No JSON body. |
| `POST /api/auth/logout` | Bearer token + refresh cookie + allowed `Origin` | Revokes the refresh session and clears the cookie. No JSON body. |
| `GET /api/auth/me` | Bearer token | Returns the current user's summary. |
| `POST /api/auth/change-password` | Bearer token | Verifies the current password, changes it, revokes refresh sessions, and clears the cookie. |
| `POST /api/auth/forgot-password` | Public | Requests a reset OTP. Returns the same generic response for eligible and unknown addresses. |
| `POST /api/auth/verify-reset-otp` | Public | Verifies the 6-digit OTP and returns a short-lived reset token. |
| `POST /api/auth/reset-password` | Public reset token | Changes the password and revokes existing refresh sessions. |
| `POST /api/auth/verify-email` | Public verification token | Verifies the email address represented by the token. |
| `POST /api/auth/resend-verification` | Public | Requests a verification email. Uses a generic response to limit account discovery. |
| `POST /api/auth/accept-invite` | Public invitation token | Accepts a single-use invitation and sets the user's initial password. |

Examples for JSON requests:

```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json
```

```json
{
  "officialEmail": "{{officialEmail}}",
  "password": "{{password}}"
}
```

```http
POST {{baseUrl}}/api/auth/refresh
Origin: {{origin}}
```

Send no body. Let Postman's cookie jar send `refresh_token`; do not copy it into JSON.

```http
POST {{baseUrl}}/api/auth/logout
Authorization: Bearer {{accessToken}}
Origin: {{origin}}
```

Send no body. Logout is intentionally authenticated and also revokes the cookie-backed refresh session.

```json
{
  "currentPassword": "{{password}}",
  "newPassword": "{{newPassword}}",
  "confirmPassword": "{{newPassword}}"
}
```

Use that body for `POST /api/auth/change-password` with a bearer token.

```json
{
  "emailType": "OFFICIAL",
  "email": "{{officialEmail}}"
}
```

Use that body for `POST /api/auth/forgot-password` and `POST /api/auth/resend-verification`. `emailType` is `OFFICIAL` or `PERSONAL`. The email must belong to the selected address type.

```json
{
  "emailType": "OFFICIAL",
  "email": "{{officialEmail}}",
  "otp": "123456"
}
```

Use that body for `POST /api/auth/verify-reset-otp`. The OTP is six digits, expires after the configured 10 minutes, and is limited to three attempts. On success, save `data.resetToken` and use it immediately:

```json
{
  "resetToken": "{{resetToken}}",
  "newPassword": "{{newPassword}}",
  "confirmPassword": "{{newPassword}}"
}
```

Use that body for `POST /api/auth/reset-password`. Reset tokens expire after 10 minutes.

```json
{ "token": "{{verificationToken}}" }
```

Use for `POST /api/auth/verify-email` and `POST /api/auth/accept-invite`; for the invitation request, add `newPassword` and `confirmPassword`:

```json
{
  "token": "{{inviteToken}}",
  "newPassword": "{{newPassword}}",
  "confirmPassword": "{{newPassword}}"
}
```

Passwords must be 8–64 characters, contain uppercase and lowercase letters, a number and a special character, and contain no spaces. The new password must differ from the current password. Validation failures return `400` with field details.

### User and role administration

These endpoints require a bearer access token. Most are available to `SUPER_ADMIN` and `ADMIN`; exact target hierarchy is also checked by the service. Freeze/unfreeze and audit search are `SUPER_ADMIN` only.

| Method and path | Purpose / query parameters |
| --- | --- |
| `GET /api/roles` | Lists roles the current administrator may assign. |
| `POST /api/users` | Creates a pending user and queues an invitation. |
| `GET /api/users` | Paged list; optional `search`, `role`, `status`, `page`, `size`, `sort`, `direction`. |
| `GET /api/users/{{userId}}` | Gets one user if the caller may manage that user. |
| `PATCH /api/users/{{userId}}` | Updates full name, phone, and/or personal email. A changed personal email must be verified again. |
| `PUT /api/users/{{userId}}/roles` | Replaces the user's entire role set. |
| `POST /api/users/{{userId}}/freeze` | Freezes a lower-ranked user and records a reason. `SUPER_ADMIN` only. |
| `POST /api/users/{{userId}}/unfreeze` | Restores the user's prior status. `SUPER_ADMIN` only. |
| `POST /api/users/{{userId}}/unlock` | Clears a login lock. `SUPER_ADMIN` or `ADMIN`, subject to hierarchy. |
| `POST /api/users/{{userId}}/resend-invite` | Reissues an invitation for a pending user. |

Create a user with unique email addresses and any role you are permitted to assign:

```json
{
  "fullName": "RFQ Reviewer",
  "officialEmail": "reviewer+postman@example.com",
  "personalEmail": "reviewer.personal+postman@example.com",
  "phone": "+910000000000",
  "roles": ["REVIEWER"]
}
```

Valid business roles are `QUOTATION_MANAGER`, `REVIEWER`, `MARKETING_EXECUTIVE`, and `VIEWER`. `SUPER_ADMIN` cannot be assigned through these endpoints. An `ADMIN` may assign business roles, not `ADMIN` or `SUPER_ADMIN`.

List examples:

```http
GET {{baseUrl}}/api/users?page=0&size=20&sort=createdAt&direction=desc
GET {{baseUrl}}/api/users?search=reviewer&role=REVIEWER&status=PENDING_VERIFICATION&page=0&size=10
```

`page` is zero-based, `size` is 1–100, valid sort fields are `fullName`, `officialEmail`, `createdAt`, and `status`, and `direction` is `asc` or `desc`. User statuses are `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, and `DISABLED`.

Update profile:

```json
{
  "fullName": "RFQ Reviewer Updated",
  "phone": "+910000000000",
  "personalEmail": "reviewer.new+postman@example.com"
}
```

Replace roles (this replaces the complete role set):

```json
{ "roles": ["REVIEWER"] }
```

Freeze a user:

```json
{ "reason": "Temporary access suspension for testing" }
```

Unfreeze, unlock, and resend-invite requests have no JSON body.

### Audit search

`GET /api/audit-logs` requires `SUPER_ADMIN` and accepts optional ISO-8601 `from`/`to`, `eventType`, `userId`, `page`, and `size` filters.

```http
GET {{baseUrl}}/api/audit-logs?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z&eventType=LOGIN_SUCCESS&userId={{userId}}&page=0&size=20
Authorization: Bearer {{accessToken}}
```

The audit event values are the `AuditEventType` names returned by the source code, for example `LOGIN_SUCCESS`, `USER_CREATED`, `USER_STATUS_CHANGED`, `PASSWORD_RESET_SUCCESS`, `USER_ROLES_CHANGED`, and `AUDIT_LOGS_VIEWED`.

## 5. Negative and security checks

| Check | How to run | Expected result |
| --- | --- | --- |
| Missing bearer token | Call `GET /api/auth/me` or `GET /api/users` without Authorization | `401 UNAUTHORIZED` |
| Invalid/expired access token | Replace `{{accessToken}}` with a random or expired value | `401` token/authentication error |
| Wrong password | Login with a disposable account and wrong password | Generic `401 INVALID_CREDENTIALS`; repeated failures lock that account |
| Invalid request body | Remove a required field or send malformed JSON | `400 VALIDATION_FAILED` or `400 MALFORMED_REQUEST` |
| Weak password/mismatch | Use a short password or different `confirmPassword` | `400` validation error |
| Wrong/missing Origin on refresh/logout | Set `Origin: https://not-allowed.example` or remove it | `403 ACCESS_DENIED` |
| Refresh without cookie | Clear Postman cookies for `localhost`, then call refresh | `401` invalid/missing refresh session |
| Reused refresh cookie | After rotation, replay the old cookie from a saved request | Reuse is rejected; the session family is revoked |
| Business role tries admin APIs | Login as the invited `REVIEWER`, call `GET /api/users` or `GET /api/roles` | `403 ACCESS_DENIED` |
| Admin tries to manage same/higher rank | Login as an `ADMIN` and target an `ADMIN`/`SUPER_ADMIN` | `403 ACCESS_DENIED` |
| Duplicate email | Create a second user with an existing official or personal address | `409 DUPLICATE_RESOURCE` |
| Unknown user ID | Use a positive ID that does not exist | `404 RESOURCE_NOT_FOUND` |
| Invalid query enum/page size | Try `role=NO_SUCH_ROLE`, `page=-1`, or `size=101` | `400` validation/type error |
| Direct identity API access | Call `http://localhost:8081/api/auth/me` with a bearer token but no gateway secret | `403`; normal clients must use the gateway |

The forgot-password and resend-verification responses are intentionally generic. For an unknown address or a throttled request, do not expect the response body to reveal whether an account exists. Password-reset requests are limited to three per account per hour; each OTP expires after 10 minutes and allows three code attempts. Test throttling through local audit/outbox state instead of response text.

## 6. Response and troubleshooting notes

Error responses look like:

```json
{
  "success": false,
  "status": 403,
  "errorCode": "ACCESS_DENIED",
  "message": "You do not have permission to perform this action",
  "path": "/api/users",
  "timestamp": "2026-10-20T12:00:00Z",
  "correlationId": "<request-id>"
}
```

Common status mappings include `400` validation/bad request, `401` missing/invalid authentication, `403` policy denial, `404` missing resource/route, `409` duplicate or concurrent modification, and `423` account locked. Keep `correlationId` when reporting a failure; it connects the response to server logs.

If the gateway returns `503`, confirm Eureka has both services registered and that the identity database/migrations started successfully. If refresh/logout returns `403`, check the `Origin` value against the configured allowed origins. If an email action returns a generic success but no email appears, check the identity log and notification outbox state: delivery runs asynchronously and may retry. For real SES setup, follow [identity-aws-ses-setup.md](identity-aws-ses-setup.md).

## 7. Current rate-limit boundary

Redis and its gateway IP limiter are intentionally absent during this API-testing phase. Identity still applies account login lockout and database-backed reset-OTP request/attempt limits. This local-only setup must not be exposed to the public internet. Before production, restore a shared gateway rate limiter or an equivalent managed edge control.
