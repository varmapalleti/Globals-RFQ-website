# Microsoft Authenticator support for identity-service

**Status: design guide only. MFA code and database migrations are not implemented yet.** The client asked for Microsoft Authenticator support after the current identity endpoints are tested. Keep this work in identity-service and do not alter frontend files as part of the current backend task.

## 1. Confirm what “Microsoft Authenticator” means

For this application, the most direct integration is **OATH TOTP**: identity-service creates a shared secret and provisioning QR code; the user scans it in Microsoft Authenticator under the generic/“Other” account flow; the app then supplies a short-lived code at sign-in. Microsoft Authenticator can generate TOTP codes for non-Microsoft accounts.

This is not Microsoft push approval. Push notifications, passwordless sign-in, device registration, Conditional Access, and Entra policies require a Microsoft Entra tenant/app integration and a different sign-in architecture. Confirm with the client whether they require generated codes or Entra push before implementation. Microsoft documents generic account setup in [Add accounts to Microsoft Authenticator](https://support.microsoft.com/en-us/authenticator/how-to-add-your-accounts-to-microsoft-authenticator) and OATH TOTP in [Microsoft Entra OATH tokens](https://learn.microsoft.com/en-us/entra/identity/authentication/concept-authentication-oath-tokens). The interoperable algorithm is specified by [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238).

## 2. Recommended first release

Support authenticator-app codes first, using RFC 6238 TOTP settings interoperable with Microsoft Authenticator and other OATH apps:

- 160-bit or stronger CSPRNG secret, encoded as Base32 for provisioning.
- 6-digit codes, 30-second time step, HMAC-SHA1 for broad TOTP interoperability.
- Allow a narrowly bounded clock window (normally current step ±1); keep server clocks synchronized with NTP.
- Reject replay of a time step that was already accepted for that user.
- Do not claim Microsoft push approval or passwordless functionality in product/API documentation.

## 3. Enrollment and login workflow

1. An authenticated user starts enrollment and proves the current password or performs another recent step-up check.
2. Identity-service creates a pending TOTP secret and returns a provisioning URI plus QR code **once**. It must never log or persist the plaintext seed.
3. User scans the QR in Microsoft Authenticator: **Add account → Other (Google, Facebook, etc.) → Scan QR code**. A manual Base32 entry can be offered as a protected fallback.
4. User submits the current 6-digit code. Only after successful verification does identity-service mark MFA active.
5. Issue one-time recovery codes at activation; show them once and store only strong hashes.
6. At login, validate the password first. If MFA is enabled, return a short-lived, purpose-specific MFA challenge instead of an access token. The user submits the challenge and TOTP or a recovery code. Only then issue the normal access token and refresh cookie.
7. Disable/reset requires recent reauthentication plus a valid TOTP/recovery proof. Notify the user and write an audit event when enrollment, disable, recovery, or reset occurs.

Never issue the normal access token, refresh cookie, or authorization roles before MFA is complete. The pending MFA token must not be accepted as a bearer access token.

## 4. Proposed endpoints

These are design proposals, not live routes. Names and response contracts should be agreed before coding because login will become a two-step flow for MFA-enabled accounts.

| Proposed route | Purpose |
| --- | --- |
| `POST /api/auth/mfa/totp/enroll` | Authenticated, step-up-protected creation of a pending seed and one-time QR/provisioning URI. |
| `POST /api/auth/mfa/totp/confirm` | Confirms enrollment with a current 6-digit code and activates TOTP. |
| `POST /api/auth/mfa/totp/disable` | Disables TOTP after password plus current TOTP/recovery verification. |
| `POST /api/auth/mfa/verify` | Exchanges a short-lived MFA challenge plus code for normal tokens/cookie. |
| `POST /api/auth/mfa/recovery-codes/regenerate` | Replaces recovery codes after step-up authentication; invalidates the previous set. |

Avoid an unauthenticated endpoint that reveals whether an account has MFA. Login responses should make account enumeration difficult while returning a clear `MFA_REQUIRED` state only after the password is correct.

## 5. Data and secret protection

Suggested persistence fields/table:

- User MFA state: `DISABLED`, `PENDING`, or `ENABLED`.
- Encrypted TOTP seed, encryption-key version, activation timestamp, and last accepted time step.
- Separate recovery-code rows with a one-time-use timestamp and high-entropy code hash.
- Separate challenge records (or single-use challenge IDs) with user, expiry, purpose, attempt count, and consumed state.

TOTP seeds cannot be stored as a one-way hash because the server needs the seed to verify future codes. Encrypt them with AES-GCM using a dedicated key from a secret manager/KMS; do not reuse `JWT_SECRET` or `OUTBOX_ENCRYPTION_KEY`. Restrict decryption to the identity-service runtime. Encrypt database/backups, keep the seed out of logs/metrics/traces, return provisioning data once, and clear pending enrollment secrets when they expire or are cancelled.

Atomically persist the last accepted time step so concurrent/replayed requests cannot reuse the same code. Hash recovery codes before storage, invalidate each code after one use, and audit every security-sensitive state change.

## 6. Abuse controls, recovery, and rollout

- Rate-limit enrollment, confirmation, MFA challenge, and recovery-code attempts per account and client IP. During the temporary no-Redis period, use database-backed attempt state; before production, restore shared gateway rate limits or an equivalent managed control.
- Use generic errors for invalid code/challenge; cap retries and expire challenges quickly (for example, 5 minutes). Never trust client-supplied user IDs when a challenge identifies the user.
- Design lost-device recovery with single-use recovery codes and verified support procedures. Support staff should not be able to view the seed or silently bypass MFA.
- Add immutable audit events for TOTP setup/confirmation/disable, failed challenges, recovery-code use/regeneration, and administrator reset.
- Roll out first to `SUPER_ADMIN` and `ADMIN`, then require it for additional roles if the client approves. Add a documented break-glass account procedure and recovery test before enforcing MFA.
- Test TOTP RFC vectors, valid/invalid codes, expired challenges, drift edges, replay/concurrency, brute-force limits, secret redaction, role enforcement, recovery, session revocation, and migration/back-up restore.

## 7. Postman test plan for the future implementation

After the contract is approved and endpoints exist, add a separate MFA folder to the identity collection:

1. Enroll with a valid bearer token; confirm response contains a QR/provisioning value only for the current enrollment.
2. Scan using Microsoft Authenticator and manually enter the displayed code to confirm enrollment.
3. Verify enrollment cannot be confirmed twice, with an expired enrollment, with a malformed code, or by another user.
4. Login as an MFA-enabled user; verify the password-only response contains no normal access token or refresh cookie.
5. Complete the MFA challenge with a valid code and confirm normal tokens are issued. Test expired/reused challenge, invalid code, code replay, and attempt lockout.
6. Use a recovery code once, confirm it cannot be used again, regenerate recovery codes, and verify the prior set is invalidated.
7. Disable MFA with and without valid step-up proof; confirm sessions are revoked and audit events are written.

## 8. Client decisions required before implementation

- Do they want TOTP verification codes, Microsoft Entra push approval, or both?
- Which roles must enroll, and by what deadline? Should MFA be mandatory for all administrators?
- What is the lost-device and support-assisted recovery process?
- Will the client accept a two-stage Postman/frontend login contract for MFA-enabled users?
- What key-management service and audit retention policy should production use?
