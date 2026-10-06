## Phase 0: Foundation

Owner: Uttkarsh. Branch `feat/infra-foundation`, 25 commits on top of `main`, last commit `2c1d580` before this report. Written 2026-10-06; the CI result was added the same day.

Review: **not yet reviewed.** No teammate was available. The owner will run an independent review of the branch before merging it. No other reviewer is named, because nobody else has reviewed it.

Status: all six exit criteria are met with evidence below. The last one, "CI is green", was met on 2026-10-06 by the first workflow run on pull request #1 (https://github.com/uttkarsh-thakur26/capstone/pull/1). Phase 0 closes when the owner has reviewed the branch and merged it.

```
Implemented   Repository layout (22). Spring Boot 4.1.1 application on Java 25 (ADR-003) with
              default-deny security (AUTH-8), a public GET /api/v1/meta (15.2, D1, ADR-007),
              fail-fast configuration (18) and the first Flyway migration enabling pgvector (7.1).
              React 19 / TypeScript 6 / Vite 8 frontend showing the backend's name and version,
              with types generated from the OpenAPI document (16.1, ADR-017). Docker Compose stack
              (DEP-1, DEP-2) and development mode (DEP-3). GitHub Actions workflow (19.3, ADR-008).
              Formatting enforced for Java and TypeScript (24.4, ADR-009, ADR-011).
              docs/PROGRESS.md, DECISIONS.md, VERSIONS.md, testing.md, architecture.md,
              deployment.md (local part), README quickstart.

Files         backend/pom.xml, backend/.mvn/jvm.config, backend/Dockerfile
              backend/src/main/java/in/edu/bennett/capstone/knowledge/
                KnowledgePlatformApplication.java, common/{AppProperties, CommonConfig,
                MetaController, MetaResponse}.java, security/SecurityConfig.java
              backend/src/main/resources/application.yaml, application-dev.yaml,
                db/migration/V1__enable_pgvector.sql
              backend/src/test/java/.../{TestcontainersConfiguration, PgvectorRoundTripTest,
                ApplicationStartupTest, ApiDocsTest}.java, common/{MetaControllerTest,
                AppPropertiesTest}.java, resources/application-test.yaml
              frontend/package.json, vite.config.ts, eslint.config.js, .prettierrc.json, .nvmrc,
                Dockerfile, nginx.conf, src/App.tsx, src/App.test.tsx, src/api/meta.ts,
                src/api/schema.d.ts (generated), src/test/setup.ts
              compose.yaml, .env.example, .gitattributes, .gitignore, .github/workflows/ci.yml

Database      V1__enable_pgvector.sql: CREATE EXTENSION IF NOT EXISTS vector. No tables yet;
              the vector column arrives with document_chunk in Phase 3, once D6 fixes N.

API           GET /api/v1/meta (public): {"name": app.display-name, "version": build version}.
              GET /actuator/health and /actuator/health/** (public).
              /v3/api-docs and /swagger-ui/** in the dev profile only.
              Everything else: 401.

Tests         Backend: 13 tests in 5 classes, 13 passed, 0 failed, 0 skipped (2 against PostgreSQL
              via @JdbcTest, 5 on the full application, 3 web slice, 3 configuration).
              Frontend: 3 tests in 1 file, 3 passed. Test-case table: docs/testing.md.

Evidence      See below, one entry per exit criterion.

Decisions     ADR-002 to ADR-017 in docs/DECISIONS.md: D3, D4, D19, D20 accepted; D2 coordinates;
              D1 single source for the name; D23 GitHub with Actions; D24 google-java-format;
              TypeScript 6.0; frontend scaffold with ESLint; Node.js 24; Compose runs prod;
              springdoc 3.1.1; pgvector image by digest; JVM in UTC; openapi-typescript override.

Deviations    From the specification as it stood before this phase, each corrected in the
              specification in the same commit as its decision:
              - TypeScript 6.0, not 7 (3.1, ADR-010).
              - No VITE_APP_NAME or VITE_API_BASE_URL (16.1, 18, D1, ADR-007).
              - "A supported LTS line" for Node.js (3.1, ADR-012).
              Not in the specification, added because Phase 0 needed them:
              - springdoc in Phase 0, not with docs/api.md in Phase 1: the frontend's /meta types
                must be generated (16.1).
              - google-java-format 1.36.1, not the newest 1.37.0, which Spotless 3.10.3 cannot run.
              - A CI step that fails when the committed API types differ from the backend.
              - A CI job that builds both images, starts the Compose stack and calls
                /api/v1/meta through nginx, so DEP-2 is checked on every run.
              - Section 3.3 rewritten with the verified traps.
              Phase reports live in docs/phase-reports/; Section 24.6 names no location.

Known issues  - Branch protection and rulesets are not available on this private repository with
                the owner's GitHub plan (API answers 403, "Upgrade to GitHub Pro"), so "a red build
                blocks merging" (19.3) is kept by convention, not enforced by GitHub.
              - JDK 25 warns that Mockito attaches itself as an agent at test time. Harmless now;
                a future JDK will refuse it. Fix when the first mock is written: pass Mockito as
                -javaagent in the Surefire argLine.
              - Backend tests need Docker Desktop running. Without it: "Could not find a valid
                Docker environment".
              - Ports 3000 and 5432 on 127.0.0.1 must be free; a local PostgreSQL on 5432 clashes.
              - google-java-format stays at 1.36.1 until a Spotless release supports a newer one.
              - openapi-typescript runs through an npm override (ADR-017).
              - The Phase 0 vector test uses a temporary vector(3) table, not the real schema.

How to run    docker compose up --build                       (after cp .env.example .env)
              cd backend && ./mvnw verify                      (Docker running)
              cd frontend && npm run lint && npm run typecheck && npm test && npm run build
              Development mode: README, "Development mode".

Next          Phase 1, authentication and roles (read 5.1, 6, 7, 14.1, 17; needs D7 and D18).
              D18's component set is not named yet. The open specification issues tagged Phase 1
              in docs/PROGRESS.md must be settled first. The Milestone-1 pack (18 October) has
              priority over Phase 1 (specification 23).
```

### Evidence

**1. From a clean clone, the documented commands start the stack and the frontend shows the backend's name and version.** Met.

The running stack and its volume were removed (`docker compose down -v`). The branch was cloned into an empty folder at `2c1d580`. The clone had no `.env` and no untracked files; `backend/mvnw` was checked out with LF endings and mode 100755. Following the README:

```
$ cp .env.example .env            # then set POSTGRES_PASSWORD
$ docker compose build --no-cache
 Image knowledge-platform-frontend Built
 Image knowledge-platform-backend Built
$ docker compose up -d --wait
 Container knowledge-platform-backend-1 Healthy
 Container knowledge-platform-db-1 Healthy
 Container knowledge-platform-frontend-1 Healthy
$ curl -s -w '\nHTTP %{http_code}\n' http://localhost:3000/api/v1/meta
{"name":"AI-powered document and organizational knowledge platform","version":"0.0.1-SNAPSHOT"}
HTTP 200
$ curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:3000/api/v1/documents
HTTP 401
```

Build and start took 84 seconds. The page at `http://localhost:3000`, read in a browser, shows the heading "AI-powered document and organizational knowledge platform" and "Version 0.0.1-SNAPSHOT", with the same text as the tab title. The rendering is also covered by `frontend/src/App.test.tsx`. Inside the stack: the backend runs as `uid=10001(app)` on OpenJDK 25.0.4.1; the database reports PostgreSQL 18.6, pgvector 0.8.7 and migration 1 "enable pgvector" applied.

Limit of this evidence: same machine, so Docker's base images and the Maven and npm caches were warm. The second-machine check is part of the Milestone-2 checkpoint (Section 23).

**2. Backend build passes, including a Testcontainers test that stores a vector and reads it back by distance.** Met.

In the clean clone:

```
$ cd backend && ./mvnw verify
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in ...ApiDocsTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in ...ApplicationStartupTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...common.AppPropertiesTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...common.MetaControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ...PgvectorRoundTripTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- spotless:3.10.3:check (default) @ knowledge-platform ---
[INFO] BUILD SUCCESS
```

`PgvectorRoundTripTest` runs against the pinned production image with the real migrations. It checks that V1 is recorded in `flyway_schema_history`, that the extension version is 0.8.7 and that the server is 18.x. It then inserts three `vector(3)` rows with bound parameters and asserts the order 1, 3, 2 by `<=>`, a distance of 1 − 1/√1.01 within 1e-6, and the stored text `[0.7,0.7,0]`.

**3. Frontend lint, type check, tests and build pass.** Met.

In the clean clone, after `npm ci` (0 vulnerabilities):

```
$ npm run lint && npm run typecheck && npm test && npm run build && npm run format:check
> eslint .
> tsc -b
 Test Files  1 passed (1)
      Tests  3 passed (3)
> tsc -b && vite build
✓ built in 218ms
All matched files use Prettier code style!
exit=0
```

`npm run gen:api` followed by `git diff --exit-code -- src/api/schema.d.ts` showed no difference.

**4. CI is green.** Met on 2026-10-06.

The private repository `uttkarsh-thakur26/capstone` was created by the owner, `main` and `feat/infra-foundation` were pushed, and pull request #1 was opened. Its first workflow run checked commit `d845a04`. Summary of `gh run view 37510571843 --json status,conclusion,jobs` (times UTC):

```
Run 37510571843 (event pull_request), 18:20:39 to 18:22:13: success
  Backend build and tests                  success   1 min 29 s
    Verify (./mvnw -B -ntp verify)         success
    API types match the backend            success
  Frontend lint, types, tests and build    success   25 s
    npm ci, lint, typecheck, test, build, format:check: each success
```

From the job logs:

- **Backend.** Temurin 25.0.4 on the `ubuntu-24.04` runner image, with Docker server 28.0.4. Testcontainers 2.0.5 started `pgvector/pgvector@sha256:9d9c9302…e2e0`, and Flyway reported PostgreSQL 18.6. `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`, then `spotless:3.10.3:check`, then `BUILD SUCCESS` (Maven total 1:08 min). After that, `npm run gen:api` regenerated `src/api/schema.d.ts` and `git diff --exit-code` found no difference.
- **Frontend.** Node.js 24.21.0 from `.nvmrc`. `npm ci` added 258 packages. Lint and type check passed, `Tests 3 passed (3)`, the build succeeded, and Prettier reported all files formatted.

Run: https://github.com/uttkarsh-thakur26/capstone/actions/runs/37510571843

**5. No secret is in the repository. `.env.example` has placeholders only.** Met.

```
$ git ls-files | grep -iE '(^|/)\.env($|\.)|secret|credential|\.pem$|\.key$'
.env.example
$ git grep -nIiE '(password|passwd|secret|api[_-]?key|token|private[_-]?key)\s*[:=]\s*[^$ {]' \
    -- . ':!docs/PROJECT_SPEC.md' ':!frontend/package-lock.json'
.env.example:7:POSTGRES_PASSWORD=change-me
backend/mvnw:189-191: the wrapper's own empty-credential handling
compose.yaml:12: POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}
$ git grep -nIE 'BEGIN [A-Z ]*PRIVATE KEY|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}'
(no output)
$ git check-ignore -v .env
.gitignore:2:.env	.env
$ git log --all --name-only --format= | grep -xE '\.env'
(no output)
```

The local `.env` holds a randomly generated password and was never staged.

**6. `docs/VERSIONS.md` lists the resolved versions.** Met. Built from `./mvnw dependency:list`, `npm ls --depth=0`, `docker image inspect` and the tools' version output on 2026-10-06.
