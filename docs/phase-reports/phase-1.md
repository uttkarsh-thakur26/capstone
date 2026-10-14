## Phase 1: Authentication and roles

Owner: Uttkarsh, who built the whole phase because the other members are not available yet (ADR-026, a recorded exception to 24.1). Branch `feat/auth-roles`, 18 commits on top of `main` (`4b51372`) before this report, last code change `1ce5d59`. Written 2026-10-07.

Status: all four exit criteria are met with evidence below. The phase closes when CI is green on the pull request, the pull request has been reviewed under ADR-028 (the owner's review and an independent review in a fresh session, a standing exception to 24.2 while no teammate can review), and it is merged.

```
Implemented   Error model: RFC 9457 Problem Details with a stable code for every error, from
              controllers, from the security filter chain and from the container's error path,
              with the format documented in the OpenAPI document (15.1, ADR-023).
              Accounts: self-registration as STUDENT, any role in the body ignored (AUTH-1);
              Argon2id passwords of 10 to 128 characters counted as code points (AUTH-4,
              ADR-022); trimmed, lower-cased, unique emails (AUTH-10); optional allowed email
              domains; the first admin from configuration, only while no admin exists (AUTH-3).
              Sign-in, refresh and sign-out (AUTH-5 to AUTH-9, ADR-020): one generic failure;
              15-minute HS256 access token holding only the user id; role and enabled flag read
              on every request (AUTH-7); 7-day refresh token stored as SHA-256 in an HttpOnly,
              SameSite=Strict, Secure cookie, rotated on use, with family revocation on reuse;
              logout by cookie; Origin check; GET /auth/me and PUT /auth/me/password (S2).
              Roles: default deny, admin URL rule plus method security (AUTH-8); admin user list
              with search and paging, role and enabled changes, the last-admin rule under a row
              lock (AUTH-2, 14.1).
              Audit: append-only audit_log guarded by a trigger, AuditService.record and
              recordSeparately (AUD-1 to AUD-4); every Phase 1 action recorded; ACCESS_DENIED
              for 403 only (S4); real client address through nginx (ADR-024).
              Frontend: React Router, TanStack Query, React Hook Form with Zod, Tailwind CSS and
              React Aria Components (ADR-021, ADR-027); API client with the token in memory, one
              silent refresh, refreshes serialized across tabs (UI-1); session restored on reload;
              route guard; sign-in, registration, role-aware shell with sign-out, home, admin
              users, not found and not allowed (16.2, UI-2, UI-3, UI-8).
              Ops: Compose and .env.example pass the new settings; the CI stack job signs in
              through nginx. Documents: data-model.md, security.md and api.md started (25).

Files         backend/src/main/java/in/edu/bennett/capstone/knowledge/
                common/ ApiException, ApiExceptionHandler, ProblemCodes, ProblemErrorController,
                  ProblemResponse, FieldErrorResponse, ApiDocsConfig, PageResponse,
                  PersistenceConfig; CommonConfig (Clock)
                audit/ AuditService, AuditEvent, AuditAction, AuditOutcome
                user/ UserService, UserAccount, UserRepository, Role, RegistrationController,
                  AdminUserController, AdminBootstrap, UserQuery, ValidPassword, Emails, request
                  and response records, RegistrationProperties, BootstrapAdminProperties
                security/ SecurityConfig, JwtConfig, SecurityProperties, CurrentUser,
                  CurrentUserJwtConverter, AccessTokenService, RefreshToken(Service, Repository),
                  RefreshCookies, OriginCheck, PasswordAuthenticator, PasswordConfig, AuthService,
                  AuthController, AccessDeniedAuditor, ProblemDetailsSecurityHandler
              backend/src/main/resources/ application*.yaml, db/migration/V2 to V4
              backend/src/test/ 14 new test classes, IntegrationTest, TestAccounts, AuditRows
              frontend/src/ api/client.ts, api/types.ts, auth/, layout/, pages/, components/,
                routes.tsx, App.tsx, app/, test/fakeApi.ts, test/renderApp.tsx, 8 test files
              frontend/nginx.conf, compose.yaml, .env.example, .github/workflows/ci.yml
              docs/ DECISIONS.md, PROJECT_SPEC.md, PROGRESS.md, VERSIONS.md, architecture.md,
                data-model.md, security.md, api.md, testing.md, README.md

Database      V2__app_user.sql: accounts; unique email with a check that it is normalized;
                role check constraint.
              V3__audit_log.sql: audit entries, bigint identity, no updated_at, check constraints
                on action and outcome, indexes on occurred_at and actor_user_id, and a trigger
                refusing UPDATE, DELETE and TRUNCATE.
              V4__refresh_token.sql: refresh tokens by SHA-256, family id, indexes on user_id and
                family_id, cascade on user deletion.
              Applied to the owner's existing Phase 0 database on 2026-10-07 without error.

API           POST /api/v1/auth/register        public
              POST /api/v1/auth/login           public
              POST /api/v1/auth/refresh         refresh cookie, Origin check
              POST /api/v1/auth/logout          refresh cookie, Origin check
              GET  /api/v1/auth/me              authenticated
              PUT  /api/v1/auth/me/password     authenticated
              GET  /api/v1/admin/users          ADMIN
              PATCH /api/v1/admin/users/{id}    ADMIN
              Every operation documents ProblemResponse as its default response. Error codes:
              docs/api.md.

Tests         Backend: 107 tests in 19 classes, 107 passed, 0 failed, 0 skipped; 94 of them are
              new in this phase (14 new classes; 13 tests in 5 classes are from Phase 0).
              Frontend: 39 tests in 8 files, 39 passed; 36 are new (App.test.tsx was rewritten
              from 3 to 4 tests). Test-case table: docs/testing.md, TC-1-01 to TC-1-25 and the
              manual checks TC-1-M01 to TC-1-M07.
              Mutation checks, each removing one safeguard and showing its test fails: the /error
              controller, code-point password length, chain revocation on refresh reuse, the
              bearer header ignored on auth endpoints, the last-admin row lock, @PreAuthorize on
              the admin service, forwarded headers, the shared refresh in the SPA, the role check
              on the Users link, the error notice on the admin screen, the return address after
              a deliberate sign-out.

Evidence      See below, one entry per exit criterion, then the clean-clone check.

Decisions     ADR-020 token strategy (D7), ADR-021 Tailwind CSS 4 with React Aria Components
              (D18), ADR-022 Argon2id, ADR-023 Problem Details everywhere, ADR-024 the audit log,
              ADR-025 specification corrections (S2, S3 for Phase 1 tables, S24, app.registration,
              the last-admin lock, EMAIL_TAKEN), ADR-026 one member builds Phase 1, ADR-027
              frontend libraries. ADR-013 gained an implementation note: Firefox 153 and
              Chromium 152 keep the Secure refresh cookie on http://localhost, so no exception.

Deviations    From the specification as it stood before this phase, each corrected in the
              specification with its decision record:
              - Logout is authorized by the refresh cookie, not the access token, and reuse of
                a rotated token revokes its family (ADR-020; 6 AUTH-6, 15.2).
              - Argon2id only, lengths in code points (ADR-022; AUTH-4, 3.3).
              - audit_log has no created_at or updated_at; ACCESS_DENIED for 403 only (ADR-024).
              - /auth/me and the own password change built in Phase 1, with PASSWORD_CHANGED
                (ADR-025; 14.2, 23). Allowed email domains under app.registration (ADR-025; 18).
              Not in the specification, added because the phase needed them:
              - Auth endpoints ignore the bearer header, so an expired token cannot block a
                refresh. The enabled flag is checked after the password, so a disabled account
                answers as slowly as a wrong password.
              - nginx overwrites X-Forwarded-For; the CI stack job signs in through nginx.
              - After a deliberate sign-out the next user starts at home. Found as a bug in the
                browser check (an admin's last page was handed to the next user), fixed in
                1ce5d59.
              Differences from the approved plan:
              - Commit order: accounts before audit, because audit_log references app_user; the
                ACCESS_DENIED listener came with sign-in, which provides the principal.
              - VERSIONS.md was not updated in the same commits that added BouncyCastle and the
                resource server, as the file asks; fixed in a2c4bb5.
              - The not-allowed page is shown in place by the route guard, not at its own path.
              Evidence limits:
              - The browser checks of exit criteria 3 and 4 were scripted in Chromium 152 on a
                throwaway stack at port 3001, not done by hand. In Firefox, the demonstration
                browser, only the refresh-cookie path was checked by hand (TC-1-M02).
              - "Applies on the next request" with an unchanged token is shown by backend tests;
                the browser shows it on the user's next sign-in, because one browser profile holds
                one refresh cookie.

Known issues  - No teammate can review yet: the review follows ADR-028, a standing exception to
                24.2, so it is no evidence that another member has read the code (27.7).
              - Expired and revoked refresh_token rows are never deleted.
              - Throttling of failed sign-ins (AUTH-5) and rate limits (SEC-8) are [T2].
              - Registration answers 409 EMAIL_TAKEN, which reveals registered emails (accepted,
                ADR-025).
              - Every backend run prints the JDK warning that Mockito attaches itself as an agent,
                although no test creates a mock; Spring Boot's test support loads Mockito. Fix:
                pass Mockito as -javaagent in the Surefire argLine.
              - The JavaScript bundle is 523 kB (162 kB gzipped), above Vite's 500 kB advisory
                limit; splitting routes into separate chunks is left for later.
              - One browser profile holds one refresh cookie, so two accounts cannot be signed in
                at the same time in one browser.
              - Playwright end-to-end tests come with Phase 6 (S20).
              - Branch protection is unavailable on the repository plan (from Phase 0).

How to run    docker compose up --build        (after cp .env.example .env and setting the
                                                password, signing key and admin password;
                                                README quickstart)
              cd backend && ./mvnw verify       (Docker running, JDK 25)
              cd frontend && npm run lint && npm run typecheck && npm test && npm run build \
                && npm run format:check
              Sign in at http://localhost:3000 with APP_BOOTSTRAP_ADMIN_EMAIL and
              APP_BOOTSTRAP_ADMIN_PASSWORD. Development mode: README, "Development mode".

Next          Get CI green on the pull request, review it under ADR-028 and list the findings
              and fixes in it, merge. Then Phase 2, document management (read 5, 7, 8, 14.2, Appendix B steps 2
              and 3; needs D8, D9, D15, D16, all with defaults). Open specification issues for
              Phase 2: S7, S8, S9, S10, and the rest of S3. The Milestone-1 pack (18 October)
              comes first on the calendar (specification 23).
```

### Evidence

**1. All "Authentication and accounts" tests in Section 19.2 pass.** Met.

Each 19.2 item and the test that checks it:

| 19.2 item | Test | Result |
|---|---|---|
| Registration ignores a role in the request | `user.RegistrationTest.registrationIgnoresARoleInTheRequest` | Pass |
| Wrong password and unknown email give the same response | `security.LoginTest.wrongPasswordUnknownEmailAndDisabledAccountGiveTheSameResponse` (bodies compared byte for byte, a disabled account included) | Pass |
| Missing, expired, tampered and wrongly signed tokens give 401 | `security.AccessTokenTest.aMissingTokenGets401` and `badTokensGet401` (expired, another key, payload changed after signing, `alg: none`, another issuer, bad subject, unknown user, malformed) | Pass |
| A disabled user is rejected | `security.AccountStateTest.aDisabledUserIsRejectedOnTheNextRequestAtRefreshAndAtSignIn`; `user.AdminUsersTest.disablingAUserTakesEffectOnTheirNextRequestAndIsAudited` | Pass |
| A non-admin gets 403 on admin endpoints | `user.AdminUsersTest.studentsAndFacultyGet403OnEveryAdminEndpoint` and `methodSecurityGuardsTheServiceOnItsOwn`; `security.AccessDeniedAuditTest` | Pass |
| Refresh rotates the token and a reused old token is rejected | `security.RefreshAndLogoutTest.refreshRotatesTheTokenAndIssuesANewAccessToken` and `aReusedRefreshTokenIsRejectedAndEndsTheWholeSignIn` | Pass |
| The last admin cannot be demoted or disabled | `user.LastAdminTest` (3 tests, one with two concurrent demotions) | Pass |
| A role change applies on the user's next request | `user.AdminUsersTest.theBootstrapAdminPromotesARegisteredUserToFacultyEffectiveOnTheirNextRequest` and `aRoleChangeAppliesToAccessOnTheNextRequest` | Pass |

Run on 2026-10-07 at `1ce5d59`, Docker running, JDK 25:

```
$ cd backend && ./mvnw verify
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 | security.AccessTokenTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 | security.AccountStateTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 | security.LoginTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 | security.RefreshAndLogoutTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 | user.AdminUsersTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 | user.LastAdminTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 | user.RegistrationTest
... (19 classes in total)
Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
--- spotless:3.10.3:check (default) @ knowledge-platform ---
BUILD SUCCESS
```

The concurrency test holds the admins' row lock from a second transaction, waits until `pg_stat_activity` shows the service blocked on it, then releases it; the service must refuse with `LAST_ADMIN`. With the lock removed the test fails because both demotions succeed, which is the bug it guards against.

**2. Errors are Problem Details with no internal data.** Met.

`common.ProblemDetailsTest`, 11 tests, passed in the run above. Each response is checked for `application/problem+json`, the expected status and `code`, a title and detail, only the fields `ProblemResponse` documents, and none of: `Exception`, `java.`, `in.edu.bennett`, `org.springframework`, `SELECT`, `app_user`, `.java`, "static resource". The paths covered: 401 and 403 from the filter chain, 403 inside a controller, 404, 405, 400 with field errors, an unreadable body, 415, a domain 409, a 500 whose exception message contains SQL and a file path (not echoed), and a URL with `;` sent to a real port, which Spring Security's firewall rejects before any controller and which the container's error path answers. Without the `ProblemErrorController` that last test fails. `ApiDocsTest` checks that the OpenAPI document gives every operation the `ProblemResponse` default response and does not list `/error`.

**3. A user can register, sign in, see a role-aware shell and sign out in the browser.** Met.

Component tests (Vitest, jsdom, `user-event`), 39 passed at `1ce5d59`:

```
$ cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run format:check
> eslint . --max-warnings 0
 Test Files  8 passed (8)
      Tests  39 passed (39)
✓ built in 517ms
All matched files use Prettier code style!
```

They cover registration, sign-in, the route guard, navigation by role and sign-out (TC-1-19 to TC-1-25). In the browser (TC-1-M05): the Compose stack built from `1ce5d59` with a fresh database, Chromium 152.0.7977.130 on Windows 11, clicks and typing scripted on Uttkarsh's machine:

1. `/register`: Sam Student registered; the sign-in page showed "Your account is ready" with the email filled in.
2. Sign-in opened Home, "Welcome, Sam Student", with the badge STUDENT and only Home in the navigation.
3. A full reload of `/` kept the session (restored from the refresh cookie).
4. `/admin/users` showed "Not allowed".
5. Sign out returned to `/login`; the following reload stayed there.

The audit log of that stack recorded USER_REGISTERED, LOGIN_SUCCESS and LOGOUT for the account. In Firefox 153 on Windows, the demonstration browser, the refresh-cookie path (sign-in, reload, refresh, logout) was checked by hand by Uttkarsh (TC-1-M02); the screens themselves were not run in Firefox.

**4. The bootstrap admin can promote a registered user to FACULTY, and the change applies on that user's next request.** Met.

`user.AdminUsersTest.theBootstrapAdminPromotesARegisteredUserToFacultyEffectiveOnTheirNextRequest` signs in as the admin from `app.bootstrap-admin` (`application-test.yaml`), promotes a registered student, and calls `/auth/me` with the student's unchanged access token: it now says FACULTY. The ROLE_CHANGED entry names the admin as actor with `{"from": "STUDENT", "to": "FACULTY"}`. `aRoleChangeAppliesToAccessOnTheNextRequest` shows the same token gaining and losing admin access within three requests. `pages/AdminUsersPage.test.tsx` covers the screen.

In the browser (TC-1-M06), continuing the session of criterion 3: the bootstrap admin signed in and started at Home, not on the page Sam had open, with Home and Users in the navigation. On Users, choosing FACULTY in Sam Student's role select showed "Sam Student is now FACULTY and enabled." and the row changed to FACULTY. After the admin signed out, Sam Student's next sign-in showed the badge FACULTY. The audit log recorded ROLE_CHANGED from STUDENT to FACULTY by `admin@example.org`.

**Clean clone (DEP-2).** The branch was cloned at `dcbdbd4` (code identical to `1ce5d59`) into an empty folder: no `.env`, no untracked files, `backend/mvnw` mode 100755 with LF endings. In the clone, `./mvnw verify` passed with 107 tests; `npm ci` reported 0 vulnerabilities and the frontend checks passed with 39 tests; `npm run gen:api` left `schema.d.ts` unchanged. Following the README quickstart (`cp .env.example .env`, then the database password, signing key and admin password), `docker compose build --no-cache` and `up -d --wait` brought all three services to healthy in 85 seconds. `/api/v1/meta` answered 200 with the name and version, `/api/v1/auth/me` without a token answered 401, and the CI sign-in script (bootstrap admin through nginx, `/auth/me`, refresh with the cookie) passed. One difference from the README: the owner's own stack held port 3000, so a Compose override published the clone's interface on 3001, without a database port, and the allowed origin was set to match.
