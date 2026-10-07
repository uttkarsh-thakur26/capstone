# Testing

Started in Phase 0. The strategy is specification Section 19; this file says how it is carried out here and records results (19.4).

## Levels and how to run them

| Level | Tools | Where | Run with |
|---|---|---|---|
| Unit | JUnit 6, AssertJ, Spring's `ApplicationContextRunner` | `backend/src/test` | `cd backend && ./mvnw test -Dtest=<ClassName>` |
| Web slice | `@WebMvcTest` with `MockMvcTester`, security filter chain included | `backend/src/test` | same |
| Integration | `@JdbcTest` and `@SpringBootTest` against PostgreSQL with pgvector in Testcontainers, real Flyway migrations | `backend/src/test` | `cd backend && ./mvnw verify` (all backend tests and the format check) |
| Frontend components | Vitest, jsdom, React Testing Library | `frontend/src/**/*.test.tsx` | `cd frontend && npm test` |
| End to end | Playwright | not yet (Phase 6) | |

Integration tests start the production database image, `pgvector/pgvector:0.8.7-pg18-trixie` pinned by digest (ADR-015), so **Docker must be running**. Without it they fail with "Could not find a valid Docker environment". H2 is never used.

Tests that need settings use the `test` profile (`backend/src/test/resources/application-test.yaml`). The backend JVM runs in UTC, and so does the test JVM when Maven runs the tests; tests started from an IDE need `-Duser.timezone=UTC` (ADR-016).

Fake embedding and chat models (19.1) do not exist yet; they arrive with Phases 3 and 5. No automated test calls a model.

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

### Phase 1 manual checks

From Phase 1 on, the Run column of a manual case also names the browser or tool and who ran the case: "by hand" means a team member performed the steps, "scripted" means the steps were run by a script on that member's machine. The automated Phase 1 cases are added to this table when the phase closes.

| Case | Requirement | What is checked | Test class or manual steps | Expected | Actual | Run |
|---|---|---|---|---|---|---|
| TC-1-M01 | AUTH-6, ADR-013, ADR-020 | The `Secure` refresh cookie, set over plain HTTP on `http://localhost:3000` by the Compose stack (`prod` profile), is kept across a page reload, and logout ends the session | [Refresh cookie in a browser](#refresh-cookie-in-a-browser), steps 1 to 6 | Register 201, sign in 200, `/me` 200, cookie not readable by scripts; after the reload refresh 200 for the same user; logout 204; refresh after logout 401 `SESSION_EXPIRED` | As expected | 2026-10-07, `88e541d`. Chromium 152.0.7977.130 on Windows 11. Scripted on Uttkarsh's machine, not by hand |
| TC-1-M02 | AUTH-6, ADR-013, ADR-020 | The same check in Firefox on Windows, the browser of the demonstration | [Refresh cookie in a browser](#refresh-cookie-in-a-browser), steps 1 to 6 | As TC-1-M01 | Not run yet | Pending: to be run by hand by Uttkarsh |
| TC-1-M03 | DEP-2, AUTH-3, AUTH-6, AUTH-9 | The CI stack job's sign-in script works against the stack: the bootstrap admin signs in through nginx, `/auth/me` says ADMIN, and the cookie refreshes the session | The `Sign in through nginx` step of `.github/workflows/ci.yml`, run in Git Bash against a stack started from `.env.example` with a generated signing key and admin password (`python` in place of `python3`) | Exit code 0 | Exit code 0 | 2026-10-07, `88e541d`. curl 8.21.0. Scripted on Uttkarsh's machine |
| TC-1-M04 | ADR-024 | nginx replaces a forged `X-Forwarded-For`, so the audit log records the address nginx saw | `curl -H 'X-Forwarded-For: 6.6.6.6'` to `POST /api/v1/auth/register` through `http://localhost:3000`, then `SELECT host(ip_address) FROM audit_log` for that email in the `db` container | The connecting peer's address, not 6.6.6.6 | `172.19.0.1`, the Docker network's gateway | 2026-10-07, `88e541d`. curl 8.21.0. Scripted on Uttkarsh's machine |

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
