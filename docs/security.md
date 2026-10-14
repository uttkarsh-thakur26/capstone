# Security

Started in Phase 1. It grows with each phase and is complete for Milestone-2 (specification Section 25). The requirements are in `docs/PROJECT_SPEC.md` Sections 5, 6 and 17; this file explains how the code meets them.

## Authentication

### Tokens

| | Access token | Refresh token |
|---|---|---|
| What | HS256 JWT signed with `APP_SECURITY_JWT_SECRET` (at least 32 bytes, checked at startup) | 32 random bytes, base64url |
| Contents | Issuer, subject (the user id), issue and expiry times. No role | Nothing; it is looked up in `refresh_token` by its SHA-256 |
| Lifetime | 15 minutes | 7 days, rotated on every use |
| Where the SPA keeps it | A module variable in memory, never `localStorage` | An `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`, `Secure` outside the `dev` profile |
| Sent with | `Authorization: Bearer …` on API calls | By the browser to every path under `/api/v1/auth` (login, register, refresh, logout, `/me`, `/me/password`); only refresh and logout read it |

Decisions: ADR-020 (strategy, D7), ADR-022 (password hashing), ADR-013 (the `Secure` cookie on plain-HTTP localhost, checked in Firefox 153 and Chromium 152).

### Sign-in, a request, refresh and sign-out

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser (SPA)
  participant N as nginx
  participant A as Backend
  participant D as PostgreSQL
  B->>N: POST /api/v1/auth/login {email, password}
  N->>A: forwards, X-Forwarded-For = client address
  A->>D: account by email
  A->>A: Argon2id check (also for unknown emails), then enabled flag
  A->>D: last_login_at, new refresh token (new family), LOGIN_SUCCESS
  A-->>B: 200 {accessToken, expiresAt, user} + Set-Cookie refresh_token
  B->>A: GET /api/v1/auth/me, Bearer access token
  A->>A: verify signature, issuer, expiry
  A->>D: user by id (role and enabled flag, every request)
  A-->>B: 200 user
  Note over B: 15 minutes later, a call answers 401
  B->>A: POST /api/v1/auth/refresh, cookie, Origin (one tab at a time)
  A->>D: lock the token row; rotate it, or revoke its family if already rotated
  A-->>B: 200 new access token + new cookie, or 401 SESSION_EXPIRED
  B->>A: POST /api/v1/auth/logout, cookie, Origin
  A->>D: revoke the family, LOGOUT
  A-->>B: 204 + cookie cleared
```

Points that are easy to get wrong, and how the code handles them:

- **The token proves identity only (AUTH-7).** `CurrentUserJwtConverter` loads the user by id on every request. A demoted or disabled user loses access on the next request, even with an unexpired token.
- **One answer for every failed sign-in (AUTH-5).** `PasswordAuthenticator` uses Spring Security's `DaoAuthenticationProvider`, which hashes the password even when the email has no account. The enabled flag is checked after the password, so a disabled account costs the same hash as a wrong password. Every failure answers `401 BAD_CREDENTIALS` with the same body.
- **Logout works without a valid access token.** It is authorized by the refresh cookie and the `Origin` check, so an expired access token cannot leave a 7-day refresh token behind (ADR-020).
- **Reuse of a rotated refresh token ends the whole sign-in.** Two clients presenting one token means one of them has a stolen copy (RFC 9700). Rotation locks the token row, so two concurrent refreshes are handled one after the other.
- **The SPA refreshes one request at a time, across tabs too.** Concurrent refreshes in one tab share a request, and the Web Locks API serializes tabs; otherwise two tabs would present the same cookie and end the sign-in. The browser keeps one refresh cookie for all tabs, so after someone else signs in in another tab, a refresh returns their session: the tab then ends its own session instead of carrying on as that user under the previous user's name and cached data.
- **The auth endpoints ignore the bearer header.** An expired access token sent along with `/auth/refresh` would otherwise turn the refresh itself into a 401.
- **The first admin comes from configuration (AUTH-3).** Created at startup only while no admin exists. Startup fails if the settings are missing or would promote an existing account. There is no built-in password.

## Authorization

| Role | Phase 1 abilities |
|---|---|
| ADMIN | Everything below, plus `GET /api/v1/admin/users` and `PATCH /api/v1/admin/users/{id}` |
| FACULTY | Own account: `/auth/me`, own password change |
| STUDENT | Own account: `/auth/me`, own password change |

- **Default deny (AUTH-8).** Public: `GET /api/v1/meta`, health, `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, and the API documentation in `dev`. Everything else needs a valid access token.
- **Admin endpoints are protected twice.** A URL rule (`/api/v1/admin/**` needs ADMIN) and `@PreAuthorize("hasRole('ADMIN')")` on the service methods. Tests check each layer on its own.
- **Identity from the principal only (ACL-E4).** The acting admin's id comes from the authenticated principal; registration ignores any `role` in the body (AUTH-1).
- **The last enabled admin cannot be demoted or disabled (AUTH-2).** The check runs under a lock on the enabled admins' rows, so two admins demoting each other at the same moment cannot both succeed.
- **The SPA's navigation is a convenience (UI-2).** It hides the Users link from non-admins, but every rule is enforced by the server.

The document access rules (ACL-1 to ACL-9) and the access-matrix fixture of 5.4 apply from Phase 2, when documents exist.

## Threats considered so far

| Threat | Defense | Where |
|---|---|---|
| Password guessing against one account | Argon2id makes each guess expensive. Throttling repeated failures (AUTH-5) is `[T2]` and not built yet | ADR-022 |
| Learning which emails have accounts through sign-in | One body for every failed sign-in; the password is hashed even for unknown emails; the enabled flag is checked after the password | `PasswordAuthenticator`, `LoginTest` |
| Learning which emails have accounts through registration | Not prevented: registration answers `409 EMAIL_TAKEN`. Preventing it needs email verification, which 2.2 excludes; accepted and recorded | ADR-025 |
| A stolen access token | Lives 15 minutes, in memory only, never in storage scripts can read later; the user's role and enabled flag are re-read on every request | AUTH-6, AUTH-7 |
| A stolen refresh token | `HttpOnly` cookie, so page scripts cannot read it; stored only as a hash; rotated on every use; reuse revokes the family | ADR-020, `RefreshAndLogoutTest` |
| Cross-site request forgery against refresh or logout | `SameSite=Strict` keeps the cookie off cross-site requests, and a foreign `Origin` is refused with `403` | `OriginCheck`, AUTH-9 |
| A user making themselves admin | Registration ignores a role; only admins change roles; method security as a second layer | AUTH-1, AUTH-2, AUTH-8 |
| Locking everyone out of administration | The last enabled admin cannot be demoted or disabled, also under concurrency | `LastAdminTest` |
| Changing or deleting the audit trail | No update or delete path in code; a database trigger refuses `UPDATE`, `DELETE` and `TRUNCATE` | ADR-024, `AuditServiceTest` |
| A forged client address in the audit log | nginx overwrites `X-Forwarded-For`; Tomcat trusts it only from private or loopback addresses | ADR-024, `ClientAddressTest` |
| Internal details in error responses | Every error is Problem Details with a fixed detail; unexpected exceptions are logged, not described | ADR-023, `ProblemDetailsTest` |
| Weak or missing secrets | The signing key must decode to at least 32 bytes and the origins must be listed, or the application does not start; `.env.example` holds placeholders only | `SecurityProperties`, SEC-3 |
| Secrets in logs | Configuration records print `<set>` instead of a password or key; request records omit passwords from `toString`; audit details refuse secret keys | AUD-2, SEC-10 |
