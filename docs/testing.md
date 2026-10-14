# Testing

Started in Phase 0. The strategy is specification Section 19; this file says how it is carried out here and records results (19.4).

## Levels and how to run them

| Level | Tools | Where | Run with |
|---|---|---|---|
| Unit | JUnit 6, AssertJ, Spring's `ApplicationContextRunner` | `backend/src/test` | `cd backend && ./mvnw test -Dtest=<ClassName>` |
| Web slice | `@WebMvcTest` with `MockMvcTester`, security filter chain included | `backend/src/test` | same |
| Integration | `@JdbcTest` and `@SpringBootTest` against PostgreSQL with pgvector in Testcontainers, real Flyway migrations. Most classes use `@IntegrationTest`, so they share one Spring context and one database container | `backend/src/test` | `cd backend && ./mvnw verify` (all backend tests and the format check) |
| Frontend components | Vitest, jsdom, React Testing Library, `user-event` (ADR-027). `src/test/fakeApi.ts` stubs `fetch` and records the requests; `src/test/renderApp.tsx` renders the whole app at a path | `frontend/src/**/*.test.{ts,tsx}` | `cd frontend && npm test` |
| End to end | Playwright | not yet (Phase 6) | |

Integration tests start the production database image, `pgvector/pgvector:0.8.7-pg18-trixie` pinned by digest (ADR-015), so **Docker must be running**. Without it they fail with "Could not find a valid Docker environment". H2 is never used.

Tests that need settings use the `test` profile (`backend/src/test/resources/application-test.yaml`). The backend JVM runs in UTC, and so does the test JVM when Maven runs the tests; tests started from an IDE need `-Duser.timezone=UTC` (ADR-016).

Fake embedding and chat models (19.1) do not exist yet; they arrive with Phases 3 and 5. No automated test calls a model.

Integration tests share one database, so each test creates its own accounts with unique emails and never assumes an empty table. `LastAdminTest` needs a database whose only admin is the bootstrap admin, so an extra test property gives it a context and a container of its own. No test creates a Mockito mock, yet Spring Boot's test support still loads Mockito, so every backend run prints the agent warning described in `docs/PROGRESS.md`, known issues.

## Test-case table

Results are copied from real runs. Automated results come from `./mvnw verify` (13 tests, 0 failures) and the frontend check command (3 tests, 0 failures), both run on 2026-10-06 in a fresh clone of commit `2c1d580` on branch `feat/infra-foundation`.

| Case | Requirement | What is checked | Test class or manual steps | Expected | Actual | Run |
|---|---|---|---|---|---|---|
| TC-0-01 | Phase 0 exit 1, DEP-2 | A clean clone starts with the documented commands and the interface shows the backend's name and version | Manual: README quickstart in an empty folder (`cp .env.example .env`, set the password, `docker compose build --no-cache`, `docker compose up -d --wait`), then `curl http://localhost:3000/api/v1/meta` and open `http://localhost:3000` | All three services healthy; page shows the name and "Version 0.0.1-SNAPSHOT" | As expected | 2026-10-06, `2c1d580` |
| TC-0-02 | Phase 0 exit 2, 19.1 | Migration V1 ran and enabled pgvector 0.8.7 on PostgreSQL 18 | `PgvectorRoundTripTest.firstMigrationEnablesPgvectorOnPostgresql18` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-03 | Phase 0 exit 2, SEC-7 | Vectors are stored and read back ordered by cosine distance, with the query vector bound as a parameter | `PgvectorRoundTripTest.storesVectorsAndReadsThemBackOrderedByCosineDistance` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-04 | AUTH-8, DEP-1 | Health is public and UP on the full application | `ApplicationStartupTest.healthIsPublicAndUp` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-05 | 15.2 `GET /meta`, D1 | `/api/v1/meta` returns the configured name and the build version | `ApplicationStartupTest.metaReportsTheConfiguredNameAndTheBuildVersion`, `MetaControllerTest.metaIsPublicAndReturnsNameAndVersion` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-06 | AUTH-8 | Every endpoint off the public list answers 401, including other methods on `/meta` and actuator endpoints | `ApplicationStartupTest.endpointsOffThePublicListNeedAuthentication`, `MetaControllerTest.onlyGetIsPublicOnMeta`, `MetaControllerTest.unknownPathsAnswer401NotDetails` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-07 | AUTH-8, 15.1 | API documentation is off outside the `dev` profile | `ApplicationStartupTest.apiDocumentationIsOffOutsideTheDevProfile` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-08 | 16.1, ADR-017 | The OpenAPI document describes `/api/v1/meta` with required fields and JSON content; the committed TypeScript types match it | `ApiDocsTest`, then `npm run gen:api` and `git diff --exit-code -- src/api/schema.d.ts` | Pass, no difference | Pass, no difference | 2026-10-06, `2c1d580` |
| TC-0-09 | 18 (fail fast), D1 | The application refuses to start without a display name, or with a blank one | `AppPropertiesTest` (3 tests) | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-10 | UI-3, Phase 0 exit 1 | The interface shows loading, the name and version, and an error when the backend cannot be reached | `frontend/src/App.test.tsx` (3 tests) | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-11 | 24.4, D24 | Java and TypeScript formatting are enforced | `spotless:check` in `./mvnw verify`; `npm run format:check` | Pass | Pass | 2026-10-06, `2c1d580` |
| TC-0-12 | Phase 0 exit 5, SEC-3 | No secret is in the repository; `.env.example` has placeholders only | Manual: `git ls-files`, `git grep` for password, secret, key and token assignments and for private-key and provider-key shapes, `git check-ignore -v .env`, history search for `.env` | Only placeholders and variable references | As expected | 2026-10-06, `2c1d580` |
| TC-0-13 | DEP-3 | Development mode: database in Compose, backend on the host in `dev`, Vite proxy | Manual: `docker compose up -d db`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, `curl :8080/api/v1/meta`, `:8080/v3/api-docs`, then `npm run dev` and `curl :5173/api/v1/meta` | 200 with the name and version; API documentation 200 in `dev` | As expected | 2026-10-06, `a773ff9` |
| TC-0-14 | Phase 0 exit 4, 19.3 | CI is green | GitHub Actions run [37514871240](https://github.com/uttkarsh-thakur26/capstone/actions/runs/37514871240) on pull request #1, the run on the last code change: commit `4212438` includes `d33e735`, the last commit that changed code, and every later commit changes only documentation. Jobs: backend `./mvnw verify` and the API-type check; frontend lint, type check, tests, build and format check; the Compose stack built from `.env.example`, started, and called on `/api/v1/meta` through nginx | All three jobs green | All three jobs green: backend 13 tests, 0 failures; frontend 3 tests passed; all three services healthy and `/api/v1/meta` returned the name and version | 2026-10-06 18:56 UTC, `4212438` |

### Phase 1 automated cases

Results come from runs on 2026-10-14 at commit `6b7aa92` on branch `feat/auth-roles`, whose code is that of `a3bab16`, the last code change after the fixes of the independent review (ADR-028): `./mvnw verify` in `backend` (110 tests, 0 failures, format check passed) and the frontend check command (41 tests in 8 files, all passed; lint, type check, build and format check passed). The first run, on 2026-10-07 at `1ce5d59`, had 107 and 39 tests, all passed. "Pass" in the Actual column means every test named in the row passed in those runs. Backend classes are in `in.edu.bennett.capstone.knowledge`; frontend files are under `frontend/src`.

| Case | Requirement | What is checked | Test class or file | Expected | Actual | Run |
|---|---|---|---|---|---|---|
| TC-1-01 | 19.2, AUTH-1 | Registration ignores a role in the request; the response carries no password data; USER_REGISTERED is audited without the password | `user.RegistrationTest`: `registrationIgnoresARoleInTheRequest`, `responseCarriesNoPasswordData`, `registrationIsAuditedWithoutThePassword` | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-02 | AUTH-4, ADR-022 | Passwords are stored as Argon2id hashes; 10 to 128 characters counted as code points (9 emoji refused, 128 Devanagari letters accepted) | `user.RegistrationTest`: `storesAnArgon2HashNotThePassword`, `passwordLengthIsTenTo128Characters` (7 cases) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-03 | AUTH-10, ADR-025 | Emails are trimmed and lower-cased; a taken email is refused whatever its case (`409 EMAIL_TAKEN`); invalid input names its fields | `user.RegistrationTest`: `emailIsTrimmedAndLowerCased`, `aTakenEmailIsRefusedWhateverItsCase`, `invalidInputIsRefusedWithTheFieldNames` | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-04 | AUTH-1 | The optional allowed-domain list | `user.RegistrationPropertiesTest` (2 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-05 | AUTH-3 | The bootstrap admin is created from configuration, only while no admin exists; startup fails without settings or with a too-short password; an existing account is never promoted; the password is never printed | `user.AdminBootstrapTest` (7 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-06 | 19.2, AUTH-5 | Wrong password, unknown email and disabled account give the same response; sign-in sets the refresh cookie with its attributes and returns a 15-minute access token; failures are audited without the password | `security.LoginTest` (6 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-07 | 19.2, AUTH-6 | Missing, expired, tampered and wrongly signed tokens give 401; so do `alg: none`, another issuer, a subject that is not a user id or not an account, and a malformed token. The tampered token names another real account, so only the signature check can reject it | `security.AccessTokenTest` (10 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-08 | 19.2, AUTH-6, AUTH-9, ADR-020 | Refresh rotates the token and a reused old token is rejected and revokes its family; other sign-ins unaffected; expired or missing cookie refused; foreign `Origin` refused; an expired bearer sent along does not block refresh; logout needs only the cookie; only the token's hash is stored | `security.RefreshAndLogoutTest` (11 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-09 | 19.2, AUTH-7, S2 | A disabled user is rejected on the next request, at refresh and at sign-in; a password change needs the current password, ends every session, and is audited | `security.AccountStateTest` (4 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-10 | 18, AUTH-6 | The signing key must be at least 256 bits; origins are required and never a wildcard; only `dev` may drop `Secure` from the cookie; the key is never printed | `security.SecuritySettingsTest` (7 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-11 | 19.2, AUTH-8, Phase 1 exit 4 | The bootstrap admin promotes a registered user to FACULTY and the user's unchanged token shows FACULTY on the next request; a role change applies to access on the next request; disabling applies on the next request; students and faculty get 403 on every admin endpoint; method security guards the service on its own; search, filters, sorting, paging and their limits, including a page number beyond the largest row offset (400, not 500) | `user.AdminUsersTest` (9 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-12 | 19.2, AUTH-2 | The last enabled admin can be neither demoted nor disabled, also when two admins demote each other at the same moment | `user.LastAdminTest` (3 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-13 | AUD-1, AUD-2, AUD-4, ADR-024 | Every field is recorded with time and address; every 14.2 action is accepted; secrets are refused in details; entries cannot be updated, deleted or truncated; success entries roll back with their action, failure entries survive it | `audit.AuditServiceTest` (6 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-14 | ADR-024 | The client address comes from the proxy's `X-Forwarded-For` | `audit.ClientAddressTest` (2 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-15 | ADR-024, S4 | A 403 from a role rule records ACCESS_DENIED with the actor and endpoint; a 401 records nothing | `security.AccessDeniedAuditTest` (2 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-16 | 15.1, Phase 1 exit 2, ADR-023 | Problem Details with a `code` and no internal data for 401 and 403 from the filter chain, 403 inside a controller, 404, 405, 400 validation, unreadable body, 415, a domain conflict, 500, and a request the firewall rejects (real port) | `common.ProblemDetailsTest` (11 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-17 | 16.1, ADR-017, ADR-023 | The OpenAPI document describes the error format on every operation and hides `/error`; the committed TypeScript types match it | `ApiDocsTest`, then `npm run gen:api` and `git diff --exit-code -- src/api/schema.d.ts` | Pass, no difference | Pass, no difference | 2026-10-14, `6b7aa92` |
| TC-1-18 | UI-1, AUTH-6, ADR-020 | The API client sends the token only to API calls; one silent refresh on 401, then the request again; the session ends when the refresh fails, and when it returns another user, whose token is then never sent; overlapping requests share one refresh; Web Locks serialize tabs; errors carry their Problem Details | `api/client.test.ts` (7 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-19 | UI-1 | The session is restored from the cookie on start with one request (also under StrictMode); sign-in uses the new token; a silent refresh that returns another user ends the session and clears every cached query; sign-out calls the server and clears every cached query | `auth/AuthProvider.test.tsx` (5 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-20 | 16.4 (route guard), UI-2 | Anonymous visitors go to sign-in and come back; no return address after a deliberate sign-out; loading state; not-allowed page for the wrong role; the page for the right role | `auth/RequireAuth.test.tsx` (5 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-21 | Phase 1 exit 3, AUTH-5, UI-8 | Sign-in: fields checked first, success opens the shell, the server's one message for a failed sign-in, return to the page first asked for | `pages/LoginPage.test.tsx` (4 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-22 | Phase 1 exit 3, AUTH-1, AUTH-4 | Registration: success then sign-in with the email filled in, no role sent; length in characters; a taken email on its field; server field errors on their fields | `pages/RegisterPage.test.tsx` (4 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-23 | Phase 1 exit 3, UI-2 | The shell's navigation by role; sign-out returns to sign-in and clears the cache; the next user starts at home; not-found page | `layout/AppShell.test.tsx` (5 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-24 | Phase 1 exit 4, 14.1, UI-3 | The admin users screen: list, promote, the server's last-admin refusal shown, disable, search with empty state, error state, not reachable for a student | `pages/AdminUsersPage.test.tsx` (7 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-25 | 16.1, D1 | The product name and version from `/meta`, loading and unreachable states; an anonymous visitor goes to sign-in | `App.test.tsx` (4 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-26 | AUTH-2, AUTH-7 | An admin's disable or demotion, committed while a sign-in or a password change of that user has read the account but not yet written it, survives that write; the user's own change is kept too | `user.ConcurrentAccountChangesTest` (2 tests) | Pass | Pass | 2026-10-14, `6b7aa92` |
| TC-1-27 | 4.5, 7.1 | On the running application, open-in-view is off and Hibernate only validates the schema | `ApplicationStartupTest.openInViewIsOffAndHibernateOnlyValidatesTheSchema` | Pass | Pass | 2026-10-14, `6b7aa92` |

### Phase 1 manual checks

From Phase 1 on, the Run column of a manual case also names the browser or tool and who ran the case: "by hand" means a team member performed the steps, "scripted" means the steps were run by a script on that member's machine. The automated Phase 1 cases are added to this table when the phase closes.

| Case | Requirement | What is checked | Test class or manual steps | Expected | Actual | Run |
|---|---|---|---|---|---|---|
| TC-1-M01 | AUTH-6, ADR-013, ADR-020 | The `Secure` refresh cookie, set over plain HTTP on `http://localhost:3000` by the Compose stack (`prod` profile), is kept across a page reload, and logout ends the session | [Refresh cookie in a browser](#refresh-cookie-in-a-browser), steps 1 to 6 | Register 201, sign in 200, `/me` 200, cookie not readable by scripts; after the reload refresh 200 for the same user; logout 204; refresh after logout 401 `SESSION_EXPIRED` | As expected | 2026-10-07, `88e541d`. Chromium 152.0.7977.130 on Windows 11. Scripted on Uttkarsh's machine, not by hand |
| TC-1-M02 | AUTH-6, ADR-013, ADR-020 | The same check in Firefox on Windows, the browser of the demonstration | [Refresh cookie in a browser](#refresh-cookie-in-a-browser), steps 1 to 6 | As TC-1-M01 | As expected: register 201, sign in 200, `/me` 200, cookie not readable by scripts; after the reload refresh 200 for the same user; logout 204; refresh after logout 401 | 2026-10-07, `88e541d`. Firefox 153.0 on Windows (64-bit). By hand, Uttkarsh |
| TC-1-M03 | DEP-2, AUTH-3, AUTH-6, AUTH-9 | The CI stack job's sign-in script works against the stack: the bootstrap admin signs in through nginx, `/auth/me` says ADMIN, and the cookie refreshes the session | The `Sign in through nginx` step of `.github/workflows/ci.yml`, run in Git Bash against a stack started from `.env.example` with a generated signing key and admin password (`python` in place of `python3`) | Exit code 0 | Exit code 0 | 2026-10-07, `88e541d`. curl 8.21.0. Scripted on Uttkarsh's machine |
| TC-1-M04 | ADR-024 | nginx replaces a forged `X-Forwarded-For`, so the audit log records the address nginx saw | `curl -H 'X-Forwarded-For: 6.6.6.6'` to `POST /api/v1/auth/register` through `http://localhost:3000`, then `SELECT host(ip_address) FROM audit_log` for that email in the `db` container | The connecting peer's address, not 6.6.6.6 | `172.19.0.1`, the Docker network's gateway | 2026-10-07, `88e541d`. curl 8.21.0. Scripted on Uttkarsh's machine |
| TC-1-M05 | Phase 1 exit 3, 16.2, UI-2 | In the browser: register, sign in, see the role-aware shell, reload and stay signed in, and sign out | On the Compose stack (`prod` profile, fresh database) at `http://localhost:3001`: register Sam Student at `/register`; sign in at `/login` (email filled in); read the shell; reload `/`; open `/admin/users`; sign out | Registration notice; sign-in opens Home "Welcome, Sam Student" with badge STUDENT and only Home in the navigation; still signed in after the reload; `/admin/users` shows Not allowed; sign-out returns to `/login`, and a reload stays there | As expected. The audit log shows USER_REGISTERED, LOGIN_SUCCESS and LOGOUT for the account | 2026-10-07, `1ce5d59`. Chromium 152.0.7977.130 on Windows 11. Scripted on Uttkarsh's machine (clicks and typing in the browser) |
| TC-1-M06 | Phase 1 exit 4, Appendix B step 1, AUTH-2 | In the browser: the bootstrap admin promotes the registered user to FACULTY | Same stack, continuing TC-1-M05: sign in as the bootstrap admin; open Users; choose FACULTY in Sam Student's role select; sign out; sign in as Sam Student | The admin starts at Home with Home and Users in the navigation; the confirmation "Sam Student is now FACULTY and enabled."; the row shows FACULTY; Sam's next sign-in shows the badge FACULTY | As expected. The audit log shows ROLE_CHANGED from STUDENT to FACULTY by `admin@example.org`. That the change applies to Sam's next request with an unchanged token is shown by TC-1-11, not in the browser, because one browser profile holds one refresh cookie | 2026-10-07, `1ce5d59`. Chromium 152.0.7977.130 on Windows 11. Scripted on Uttkarsh's machine (clicks and typing in the browser) |
| TC-1-M07 | DEP-2, 19.3 | A clean clone builds, passes every check, and starts with the README quickstart | `git clone --branch feat/auth-roles` into an empty folder at `dcbdbd4` (code as `1ce5d59`): `./mvnw verify`; `npm ci` and the frontend checks; `npm run gen:api`; then `cp .env.example .env`, the database password, signing key and admin password, `docker compose build --no-cache`, `docker compose up -d --wait`, `/api/v1/meta`, `/api/v1/auth/me` without a token, and the CI sign-in script. The owner's stack held port 3000, so an override published the clone's interface on 3001 | 107 and 39 tests pass; no type difference; three healthy services; 200 with the name; 401; sign-in script exit 0 | As expected; started in 85 seconds; `npm ci` reported 0 vulnerabilities | 2026-10-07, `dcbdbd4`. Docker 29.8.2, curl 8.21.0. Scripted on Uttkarsh's machine |
| TC-1-M08 | DEP-1, 4.5, 7.1, AUTH-6, AUTH-9 | After the fixes of the independent review, the Compose stack starts in the `prod` profile with schema validation on and open-in-view off, and the CI sign-in script passes through nginx | A stack built from `.env.example` with a generated signing key and admin password and the allowed origin `http://localhost:3001`, and a Compose override that publishes the interface on 3001 without a database port; `/api/v1/meta`; the steps of the `Sign in through nginx` CI step (`node` in place of `python3`); the backend log searched for the open-in-view warning | Three healthy services; meta 200; sign-in sets the cookie with its attributes; `/auth/me` says ADMIN; refresh with the cookie says ADMIN; no warning | As expected. Cookie attributes `Path=/api/v1/auth; Max-Age=604799; Secure; HttpOnly; SameSite=Strict` | 2026-10-14, `6b7aa92` (code as `a3bab16`). Docker 29.8.2, curl 8.21.0, Node.js 24.19.0. Scripted on Uttkarsh's machine |

### Refresh cookie in a browser

Checks that a browser keeps the refresh cookie over plain HTTP on `localhost` and sends it back after a reload (AUTH-6, ADR-013). The sign-in screens do not exist yet, so the steps call the API from the browser's console on the application's own origin. They register a new throwaway student whose password is generated in the page, so no real password is typed.

1. Start the stack (README quickstart) and open `http://localhost:3000`.
2. Open the browser's console: Firefox `Ctrl+Shift+K`, Chrome `Ctrl+Shift+J`. Firefox refuses the first paste into its console until you type `allow pasting` and press Enter.
3. Paste and run:

   ```js
   const pw = crypto.randomUUID();
   const email = `browser-check-${Date.now()}@example.org`;
   const json = { 'Content-Type': 'application/json' };
   const reg = await fetch('/api/v1/auth/register', { method: 'POST', headers: json, body: JSON.stringify({ email, displayName: 'Browser cookie check', password: pw }) });
   const login = await fetch('/api/v1/auth/login', { method: 'POST', headers: json, body: JSON.stringify({ email, password: pw }) });
   const session = await login.json();
   const me = await fetch('/api/v1/auth/me', { headers: { Authorization: 'Bearer ' + session.accessToken } });
   sessionStorage.setItem('browserCheckEmail', email);
   ({ register: reg.status, login: login.status, me: me.status, cookieReadableByScript: document.cookie.includes('refresh_token'), userAgent: navigator.userAgent })
   ```

   Expected: `register: 201`, `login: 200`, `me: 200`, `cookieReadableByScript: false`.
4. Optional: in the storage view (Firefox `Shift+F9`, Chrome Application tab), Cookies, `http://localhost:3000` shows `refresh_token` with HttpOnly, Secure, SameSite Strict and path `/api/v1/auth`. If it is missing, the browser refused the cookie.
5. Reload the page (`F5`), then paste and run:

   ```js
   const expected = sessionStorage.getItem('browserCheckEmail');
   const refresh = await fetch('/api/v1/auth/refresh', { method: 'POST' });
   const refreshed = await refresh.json();
   const logout = await fetch('/api/v1/auth/logout', { method: 'POST' });
   const after = await fetch('/api/v1/auth/refresh', { method: 'POST' });
   ({ afterReloadRefresh: refresh.status, sameUser: refreshed.user?.email === expected, logout: logout.status, refreshAfterLogout: after.status })
   ```

   Expected: `afterReloadRefresh: 200`, `sameUser: true`, `logout: 204`, `refreshAfterLogout: 401`.
6. Record the browser and its version (the `userAgent` from step 3), the date, the commit and who ran it in the table above. An `afterReloadRefresh` of 401 means the browser did not keep the `Secure` cookie on plain HTTP, which needs the exception decision that ADR-013 anticipates.
