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

Tests that need settings use the `test` profile (`backend/src/test/resources/application-test.yaml`). The backend JVM and the test JVM run in UTC (ADR-016).

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
| TC-0-14 | Phase 0 exit 4, 19.3 | CI is green | GitHub Actions run on the pull request | Green | **Pending**: no remote repository exists yet (ADR-008) | |
