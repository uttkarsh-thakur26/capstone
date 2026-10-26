# Decisions

Architecture and process decisions, in the format of specification Section 24.7. Each entry that resolves an item of the decision register (specification Section 26) names its D-number in the title. A decision is changed only by a new entry that supersedes it.

| ADR | Register | Title | Status | Date |
|---|---|---|---|---|
| [ADR-001](#adr-001-chunks-live-in-an-application-owned-table-d12) | D12 | Chunks live in an application-owned table | Accepted (specification default) | 2026-10-05 |
| [ADR-002](#adr-002-maven-with-the-maven-wrapper-d3) | D3 | Maven with the Maven Wrapper | Accepted | 2026-10-06 |
| [ADR-003](#adr-003-java-25-lts-on-every-build-path-d4) | D4 | Java 25 LTS on every build path | Accepted | 2026-10-06 |
| [ADR-004](#adr-004-no-lombok-d19) | D19 | No Lombok | Accepted | 2026-10-06 |
| [ADR-005](#adr-005-course-dates-d20) | D20 | Course dates | Accepted, two dates to confirm | 2026-10-06 |
| [ADR-006](#adr-006-maven-coordinates-and-base-package-d2) | D2 | Maven coordinates and base package | Accepted | 2026-10-06 |
| [ADR-007](#adr-007-the-display-name-has-one-source-served-by-meta-d1) | D1 | The display name has one source, served by `/meta` | Accepted | 2026-10-06 |
| [ADR-008](#adr-008-github-with-github-actions-d23) | D23 | GitHub with GitHub Actions | Accepted | 2026-10-06 |
| [ADR-009](#adr-009-google-java-format-through-spotless-d24) | D24 | google-java-format through Spotless | Accepted | 2026-10-06 |
| [ADR-010](#adr-010-typescript-60-not-70) | | TypeScript 6.0, not 7.0 | Accepted | 2026-10-06 |
| [ADR-011](#adr-011-frontend-scaffold-with-eslint-and-its-test-and-format-tools) | | Frontend scaffold with ESLint, and its test and format tools | Accepted | 2026-10-06 |
| [ADR-012](#adr-012-nodejs-24-lts-line) | | Node.js 24 LTS line | Accepted | 2026-10-06 |
| [ADR-013](#adr-013-the-compose-stack-runs-the-prod-profile) | | The Compose stack runs the `prod` profile | Accepted | 2026-10-06 |
| [ADR-014](#adr-014-springdoc-openapi-311-pinned) | | springdoc-openapi 3.1.1, pinned | Accepted | 2026-10-06 |
| [ADR-015](#adr-015-the-pgvector-image-is-pinned-by-tag-and-digest) | | The pgvector image is pinned by tag and digest | Accepted | 2026-10-06 |
| [ADR-016](#adr-016-the-backend-jvm-runs-in-utc) | | The backend JVM runs in UTC | Accepted | 2026-10-06 |
| [ADR-017](#adr-017-api-types-from-openapi-typescript-with-an-npm-override) | | API types from openapi-typescript, with an npm override | Accepted | 2026-10-06 |
| [ADR-018](#adr-018-requirement-tiers-follow-the-course-milestones) | | Requirement tiers follow the course milestones | Accepted | 2026-10-07 |
| [ADR-019](#adr-019-phase-0-was-reviewed-by-its-owner-alone) | | Phase 0 was reviewed by its owner alone | Accepted, exception to 24.2 | 2026-10-07 |
| [ADR-020](#adr-020-jwt-access-token-and-rotating-refresh-cookie-d7) | D7 | JWT access token and rotating refresh cookie | Accepted | 2026-10-07 |
| [ADR-021](#adr-021-tailwind-css-4-with-react-aria-components-d18) | D18 | Tailwind CSS 4 with React Aria Components | Accepted | 2026-10-07 |
| [ADR-022](#adr-022-passwords-are-hashed-with-argon2id) | | Passwords are hashed with Argon2id | Accepted | 2026-10-07 |
| [ADR-023](#adr-023-every-error-is-a-problem-details-response) | | Every error is a Problem Details response | Accepted | 2026-10-07 |
| [ADR-024](#adr-024-the-audit-log-is-append-only-and-written-through-jdbc) | | The audit log is append-only and written through JDBC | Accepted | 2026-10-07 |
| [ADR-025](#adr-025-specification-corrections-for-phase-1) | | Specification corrections for Phase 1 | Accepted | 2026-10-07 |
| [ADR-026](#adr-026-phase-1-is-built-by-one-member) | | Phase 1 is built by one member | Accepted, exception to 24.1 | 2026-10-07 |
| [ADR-027](#adr-027-frontend-libraries-for-routing-server-state-forms-and-components) | | Frontend libraries for routing, server state, forms and components | Accepted | 2026-10-07 |
| [ADR-028](#adr-028-owner-review-with-an-independent-review-while-no-teammate-can-review) | | Owner review with an independent review while no teammate can review | Accepted, standing exception to 24.2 | 2026-10-14 |
| [ADR-029](#adr-029-local-storage-pdf-only-admins-read-everything-no-student-uploads-d8-d9-d15-d16) | D8, D9, D15, D16 | Local storage, PDF only, admins read everything, no student uploads | Accepted (specification defaults) | 2026-10-26 |
| [ADR-030](#adr-030-specification-corrections-for-phase-2) | | Specification corrections for Phase 2 | Accepted | 2026-10-26 |
| [ADR-031](#adr-031-upload-storage-and-download) | | Upload, storage and download | Accepted | 2026-10-26 |
| [ADR-032](#adr-032-one-member-builds-every-phase) | | One member builds every phase | Accepted, standing exception to 24.1; supersedes ADR-026 | 2026-10-26 |

---

### ADR-001: Chunks live in an application-owned table (D12)

Date 2026-10-05, status accepted as the specification default. The team confirms it when Phase 3 starts.

The full record is in `docs/PROJECT_SPEC.md` Section 4.6. In short: chunks and their vectors are stored in the application's own `document_chunk` table and queried with native SQL, so the permission check runs in the same statement as the similarity search. Spring AI's `PgVectorStore` was rejected because access rules would have to be copied into each chunk's metadata.

---

### ADR-002: Maven with the Maven Wrapper (D3)

Date 2026-10-06, status accepted.

**Context.** The backend needs one build tool that works the same on every member's machine, in CI and in the Docker build.

**Decision.** Maven, run through the Maven Wrapper that start.spring.io generates (wrapper 3.3.4, Maven 3.9.16). Nobody installs Maven globally. The wrapper downloads the pinned Maven version on first use.

**Alternatives.** Gradle, which start.spring.io offers as its default. Rejected because the specification default stands and nobody objected, and a declarative `pom.xml` is easy to read in review.

**Consequences.** A JDK is the only prerequisite. `backend/mvnw` must keep LF line endings and its executable bit, or it fails in Linux CI and in the Docker build (`.gitattributes` handles the line endings).

---

### ADR-003: Java 25 LTS on every build path (D4)

Date 2026-10-06, status accepted.

**Context.** Spring Boot 4.1.1 declares support for Java 17 up to and including 26, and Spring Framework is fully tested only on the LTS releases (17, 21, 25). The development machine first had only Oracle JDK 27, a non-LTS release outside that range. start.spring.io lists Java 27 but defaults to 17.

**Decision.** Java 25 everywhere: Eclipse Temurin 25 locally (25.0.4.1+1, set through `JAVA_HOME` and first on `PATH`), `java.version` 25 in the POM, Temurin 25 in CI and in the backend container images.

**Alternatives.** Java 27: rejected, outside the documented range of Spring Boot 4.1.1 and supported by Oracle only until March 2027. Java 21: the permitted fallback, not needed because the only build machine has JDK 25.

**Consequences.** One JDK across local builds, CI and images, so test results carry over. Oracle JDK 27 remains installed on the development machine and must not be used for builds.

---

### ADR-004: No Lombok (D19)

Date 2026-10-06, status accepted.

**Context.** DTOs, configuration properties and entities need constructors and accessors.

**Decision.** No Lombok. DTOs and `@ConfigurationProperties` types are Java records. Classes that need injection have explicit constructors.

**Alternatives.** Lombok: rejected. It is an annotation processor tied to compiler internals and has historically lagged new JDK releases. Records cover most of what it would generate.

**Consequences.** JPA entities, which cannot be records, carry hand-written accessors.

---

### ADR-005: Course dates (D20)

Date 2026-10-06, status accepted, with two dates still to confirm (specification 27.8).

**Context.** The course framework prints the Milestone-2 date in two ways and gives no final evaluation date.

**Decision.** Plan for Milestone-1 on 18 October 2026, Milestone-2 on 23 November 2026 (the earlier of the two printed dates) and the final panel at the end of the semester.

**Alternatives.** Planning for 30 November: rejected, because the stricter reading is safer.

**Consequences.** If 30 November is confirmed, the extra week is spare time, not room for more features. 18 October is a Sunday, so the team confirms whether it is the submission date or the presentation date.

---

### ADR-006: Maven coordinates and base package (D2)

Date 2026-10-06, status accepted.

**Context.** The backend needs a Maven group, an artifact and a base package before it is generated. The specification asks for a neutral technical identifier, not a brand, because the project name is not final (D1).

**Decision.** Group `in.edu.bennett.capstone`, artifact `knowledge-platform`, base package `in.edu.bennett.capstone.knowledge`. Modules are packages under the base package, named as in specification Section 4.2 (`common`, `security`, `user`, `document` and so on).

**Alternatives.** A package named after the product: rejected, because the name is still open and renaming packages later touches every file. `com.example`: rejected, it is a placeholder, not an identifier.

**Consequences.** The final product name (D1) can change without touching any package or Maven coordinate.

---

### ADR-007: The display name has one source, served by `/meta` (D1)

Date 2026-10-06, status accepted. The final project name (D1) stays open.

**Context.** The specification named two sources for the product name: `VITE_APP_NAME`, compiled into the frontend bundle at build time, and the backend property `app.display-name`. Two values can disagree, and the Phase 0 exit criterion asks the frontend to show the backend's name. The final name is not decided, and no name may be invented.

**Decision.** The backend property `app.display-name`, set through the environment variable `APP_DISPLAY_NAME`, is the only source. `GET /api/v1/meta` returns it with the build version, and the SPA shows what it receives. Until the team decides D1, the value is the working description in the specification's header table: "AI-powered document and organizational knowledge platform". The property has no default in code, so the application refuses to start without it. The SPA calls the API with relative `/api` paths, so `VITE_API_BASE_URL` is dropped too, and the frontend needs no `VITE_*` variable at all.

**Alternatives.** `VITE_APP_NAME` alone, or both variables fed from one `.env` value: rejected, because a `VITE_*` value is fixed when the image is built, so a renamed product needs a rebuild and the two copies can drift.

**Consequences.** Renaming the product is one environment change and a restart. The SPA shows the name only after `/meta` answers, so it has a loading state. Specification sections 16.1, 18 and D1 were corrected to match.

---

### ADR-008: GitHub with GitHub Actions (D23)

Date 2026-10-06, status accepted.

**Context.** The Phase 0 exit criterion "CI is green" needs a remote repository and a CI service, and the specification named neither. Integration tests start PostgreSQL in Testcontainers, so the CI runner must provide Docker.

**Decision.** A private GitHub repository under the Phase 0 owner's account, with GitHub Actions on `ubuntu-latest` runners. The workflow is `.github/workflows/ci.yml`. It runs on every pull request and on pushes to `main`. The owner creates the remote. Until it exists, "CI is green" is reported as pending.

**Alternatives.** GitLab CI: not chosen. GitHub Actions' Linux runners provide Docker without extra setup, which the Testcontainers tests need. A public repository: not chosen by the owner. Specification Section 25 already keeps the submitted course documents, which carry team details, out of any public repository.

**Consequences.** Members contribute under their own GitHub accounts once they are added as collaborators. "A red build blocks merging" needs branch protection; whether the owner's plan offers it on a private repository is checked when the remote is created, and until then the rule is followed by convention.

Implementation note (2026-10-06): the repository `uttkarsh-thakur26/capstone` exists and CI ran green on pull request #1. Branch protection and rulesets are not available on it with the owner's plan (the GitHub API answers 403, "Upgrade to GitHub Pro"), so the rule stays a convention: no pull request is merged unless its checks are green.

---

### ADR-009: google-java-format through Spotless (D24)

Date 2026-10-06, status accepted.

**Context.** Specification 24.4 requires one formatter per language, enforced in CI, and names Spotless for Java without a style. The style must be fixed before the first Java file, so that formatting never arrives later as one large commit that blurs each member's history.

**Decision.** The Spotless Maven plugin (3.10.3) with google-java-format 1.36.1, both pinned in `backend/pom.xml` because Spring Boot does not manage them. `spotless:check` is bound to the `verify` phase, so `./mvnw verify` fails on unformatted code locally and in CI. `./mvnw spotless:apply` formats the code. `backend/.mvn/jvm.config` exports the `jdk.compiler` packages that google-java-format uses.

Implementation notes (2026-10-06): google-java-format 1.37.0, released 2026-10-01, fails inside Spotless 3.10.3 with an `InvocationTargetException`. The newest version listed in Spotless 3.10.3's compatibility table is 1.36.1, and it works. Without the exports in `jvm.config`, Spotless opens the compiler packages through `sun.misc.Unsafe`, which JDK 25 reports as a terminally deprecated call; with them, no such call is made.

**Alternatives.** palantir-java-format: a wider 120-column variant of the same formatter, not chosen. The Eclipse formatter: needs a shared settings file and gives no single canonical style.

**Consequences.** Formatting is not discussed in review. google-java-format has no configuration beyond its AOSP variant, so the style cannot drift. google-java-format is upgraded only together with a Spotless release that supports the new version.

---

### ADR-010: TypeScript 6.0, not 7.0

Date 2026-10-06, status accepted. Revisit when TypeScript 7.1 ships its compiler API.

**Context.** Specification 3.1 said "TypeScript 7" and also "take what `npm create vite` generates". On 2026-10-05 TypeScript 7.0.2 was the npm `latest`, but create-vite 9.2.1 generates `typescript ~6.0.2`. TypeScript 7.0 is the native rewrite and ships without a compiler API. typescript-eslint 8.71 declares support only for TypeScript below 6.1, and openapi-typescript 7.13 crashes on 7.0 because it calls the compiler API. A scratch project on TypeScript 6.0.3 passed lint, type check, tests and build.

**Decision.** Use TypeScript 6.0.x, as the generator produces. Avoid options that 6.0 deprecates and 7.0 removes (`baseUrl`, `moduleResolution: node10`, `target: es5`), so the later move stays small.

**Alternatives.** TypeScript 7.0 now: rejected, because the lint and type-generation tools the specification requires do not run on it. Installing 7.0 next to 6.0 under an npm alias: rejected, it works but adds a dual-compiler setup that buys nothing on a project of this size.

**Consequences.** Lint, type check and API type generation run on a supported compiler. Specification 3.1 was corrected. The move to 7.x is a later decision once typescript-eslint and openapi-typescript support it.

---

### ADR-011: Frontend scaffold with ESLint, and its test and format tools

Date 2026-10-06, status accepted.

**Context.** Specification 3.2 says to scaffold with `npm create vite`, and 24.4 says ESLint with Prettier. create-vite 9.2.1 sets up Oxlint by default and offers ESLint only behind `--eslint`. Its React template has no `typecheck` or `test` script and no test runner, but the Phase 0 exit criterion runs lint, type check, tests and build.

**Decision.** Scaffold with `npm create vite@9.2.1 frontend -- --template react-ts --eslint --no-interactive`, pinned so a later template cannot change the result. Then add these development dependencies, each actively maintained (latest releases between July 2025 and October 2026, repositories not archived):

| Package | Why |
|---|---|
| vitest | Test runner that reuses the Vite configuration (named in specification 3.1) |
| jsdom | Browser-like DOM for component tests |
| @testing-library/react, @testing-library/dom, @testing-library/jest-dom | Component tests that query the page the way a user sees it (3.1) |
| prettier | The TypeScript formatter (24.4) |
| eslint-config-prettier | Turns off ESLint rules that conflict with Prettier. Configuration only; last release 2025-07-20, repository active |

Scripts: `lint` (`eslint .`), `typecheck` (`tsc -b`), `test` (`vitest run`), `format:check` (`prettier --check .`) and `build`.

**Alternatives.** Oxlint, the generator's default: rejected, because 24.4 names ESLint and typescript-eslint covers type-aware rules. Running Prettier inside ESLint through eslint-plugin-prettier: rejected, the eslint-config-prettier documentation recommends running Prettier separately. happy-dom instead of jsdom: not needed, jsdom is the common choice with Testing Library.

**Consequences.** The frontend check is `npm run lint && npm run typecheck && npm test && npm run build`, plus `npm run format:check` in CI. Specification 3.2 rule 1 was corrected.

Implementation note (2026-10-07): `lint` runs `eslint . --max-warnings 0`. ESLint exits successfully on warnings, and the recommended React Hooks rules report a missing effect dependency (`react-hooks/exhaustive-deps`) only as a warning, so without the flag CI passed with that bug.

---

### ADR-012: Node.js 24 LTS line

Date 2026-10-06, status accepted. Revisit after Node.js 26 becomes LTS on 2026-10-28.

**Context.** Specification 3.1 asked for "an active LTS line". Node.js 24 is Active LTS until 2026-10-20 and Maintenance LTS until 2028-04-30. Node.js 26 becomes LTS on 2026-10-28. Read literally, the old wording would force a runtime change in the middle of the build window. The development machine runs 24.19.0; the latest 24.x is 24.21.0.

**Decision.** Pin the Node.js 24 line: `frontend/.nvmrc` holds `24`, `package.json` declares `"engines": {"node": "^24.15.0"}` (the floor that jsdom sets on the 24 line), CI reads `.nvmrc`, and the Docker build stage uses `node:24.21.0-alpine`. Specification 3.1 now says "a supported LTS line".

**Alternatives.** Node.js 26: not an LTS release until 2026-10-28, so not now. Following "active LTS" literally: rejected, it would mean switching runtimes three weeks before the Milestone-2 feature freeze.

**Consequences.** Every frontend build uses the same Node.js line. Moving to 26 is a deliberate later decision, not a drift.

---

### ADR-013: The Compose stack runs the `prod` profile

Date 2026-10-06, status accepted.

**Context.** Specification 18 names the profiles `dev`, `test` and `prod` but did not say which one the Docker Compose stack uses. That stack is what a clean clone starts (DEP-2), what Milestone-2 demonstrates and what the final demonstration runs on a team machine (D17). DEP-3 already gives `dev` to the host-run backend with hot reload. Specification 18 also listed environment variables whose names depend on the open model decisions (D5, D6), so a complete `.env.example` could not be written in Phase 0.

**Decision.** The backend container in `compose.yaml` runs with `SPRING_PROFILES_ACTIVE=prod`. `dev` is only for the backend started on the host against the Compose `db` service, and `test` only for automated tests. `.env.example` lists the variables read by the phases built so far, and each phase adds its own.

**Alternatives.** Running the stack under `dev`: rejected, because the demonstration would then run with development settings, such as Swagger UI switched on, that production must not have. A separate `compose` profile: rejected for now, because nothing yet differs from `prod`. If a real difference appears, it is recorded as a new decision.

**Consequences.** What the panel sees is what a deployment runs. `prod` will require `Secure` refresh cookies (AUTH-6) while the local stack serves plain HTTP on `localhost`. Phase 1 must check how the browsers used for the demonstration treat `Secure` cookies on `http://localhost` and, if needed, record an explicit exception for that case.

Implementation note (2026-10-07): checked on the Compose stack (`prod` profile, plain HTTP on `http://localhost:3000`, commit `88e541d`) in Firefox 153 on Windows, the demonstration browser, and in Chromium 152, the engine of the fallback browser Chrome. Both keep the `Secure` refresh cookie and send it back after a reload (`docs/testing.md`, TC-1-M01 and TC-1-M02). No exception is needed: `prod` keeps `Secure`, and only `dev` turns it off (AUTH-6).

---

### ADR-014: springdoc-openapi 3.1.1, pinned

Date 2026-10-06, status accepted.

**Context.** The SPA's API types are generated from the OpenAPI document (specification 16.1), so the backend must publish one from Phase 0, when the frontend first calls `/meta`. springdoc-openapi is named in specification 3.1 as "3.x", but Spring Boot does not manage its version. springdoc 3.0.x is built against Boot 4.0. 3.1.0 is built against Boot 4.1 but exposed MCP, admin and dashboard endpoints by default (advisory GHSA-4v2q-56v7-2cpw). 3.1.1 (2026-09-06) fixes that and makes MCP opt-in.

**Decision.** `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.1.1, pinned in `backend/pom.xml` with a comment giving the reason. The OpenAPI document and Swagger UI are public only in the `dev` profile (AUTH-8) and switched off in `prod` (ADR-013). springdoc's MCP features stay disabled.

**Alternatives.** Writing the OpenAPI document by hand: rejected, because it would drift from the controllers. Hand-written TypeScript types for each DTO: rejected by specification 16.1.

**Consequences.** springdoc brings Jackson 2 (`com.fasterxml.jackson.databind`) through swagger-core, next to Boot's Jackson 3. Application code imports Jackson 3 (`tools.jackson.*`) only, and reviews reject Jackson 2 imports. Each Boot upgrade checks that a matching springdoc release exists.

---

### ADR-015: The pgvector image is pinned by tag and digest

Date 2026-10-06, status accepted.

**Context.** Specification 19.1 requires the integration tests to use the production database image. The pgvector project publishes `0.8.7-pg18` and `pg18` as the Debian bookworm build, and the README's main instruction uses the Debian trixie build (`pg18-trixie`), which matches the official `postgres:18` base. Tags are rebuilt in place when PostgreSQL releases a minor version, so a tag alone can change under the project, for example in the week before a milestone. The next PostgreSQL minor release is scheduled for 2026-11-12.

**Decision.** Use `pgvector/pgvector:0.8.7-pg18-trixie@sha256:9d9c930220cb9bf2f956d10a8f909cf9973d9672ca0278aef2c1e6facccad2e0` in `compose.yaml` and in the Testcontainers configuration. On 2026-10-06 this image contains PostgreSQL 18.6 and pgvector 0.8.7, with `PGDATA=/var/lib/postgresql/18/docker`. The digest is recorded in `docs/VERSIONS.md`. Updating it is a deliberate change: pull, run the full test suite, then update both places and VERSIONS.md in one commit.

**Alternatives.** The tag alone: rejected, because it can move silently. `postgres:18` with pgvector installed by our own Dockerfile: rejected, because it adds an image to maintain for no gain.

**Consequences.** Tests and the Compose stack run the same database bits until someone changes them on purpose. Security fixes in PostgreSQL minors are not picked up automatically, so the digest is reviewed after each PostgreSQL minor release.

---

### ADR-016: The backend JVM runs in UTC

Date 2026-10-06, status accepted. Corrected 2026-10-07: tests started from an IDE are not covered.

**Context.** The first backend build failed: `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`. The PostgreSQL JDBC driver sends the JVM's default zone id when it connects. A Windows JVM set to Indian time reports the legacy id `Asia/Calcutta`. The pinned database image (Debian trixie, tzdata 2026c) keeps only the current id `Asia/Kolkata` and rejects the legacy one. Specification 4.5 already requires every timestamp to be UTC.

**Decision.** The backend JVM always runs in UTC. `KnowledgePlatformApplication.main` sets the default time zone to UTC before Spring starts, which covers the packaged jar, `spring-boot:run` and the application started from an IDE. The POM's Surefire `argLine` passes `-Duser.timezone=UTC` to the forked test JVM, so tests run in UTC when Maven runs them.

Tests started from an IDE are not covered. They never call `main`, and an IDE applies the Surefire `argLine` only if it copies it into its own test configuration, which not every IDE does. Such a test runs in the host's zone and, on a machine whose zone id is a legacy one, fails with the error above. IDE test runs therefore need `-Duser.timezone=UTC` in their run configuration (README, development mode).

**Alternatives.** Leaving the host zone and asking each developer to set `TZ`: rejected, because the build would break on any machine with a legacy zone id. A different database image with legacy zone links: rejected, the image is pinned to match production (ADR-015).

**Consequences.** Logs, database sessions and `timestamptz` values agree on UTC on every machine. The frontend converts to local time for display.

---

### ADR-017: API types from openapi-typescript, with an npm override

Date 2026-10-06, status accepted. Revisit when openapi-typescript releases support for TypeScript 6 or later.

**Context.** Specification 16.1 requires the SPA's API types to be generated from the OpenAPI document with `openapi-typescript`, never written by hand. Its latest release, 7.13.0 (2026-02-11), declares `typescript ^5.x` as a peer dependency, so `npm install` refuses it next to TypeScript 6.0 (ADR-010). Support for TypeScript 6 and 7 is open upstream (issue #2723 and several pull requests); the repository is active and not archived, but there has been no release since February.

**Decision.** Use openapi-typescript 7.13.0 as a development dependency, with `"overrides": {"openapi-typescript": {"typescript": "$typescript"}}` in `frontend/package.json` so it runs on the project's TypeScript. The backend test `ApiDocsTest` saves the OpenAPI document to `backend/target/openapi.json`, and `npm run gen:api` turns it into `frontend/src/api/schema.d.ts`, which is committed. The generator only reads the document and writes type declarations; it runs at development time and never ships in the bundle.

**Alternatives.** Hand-written types: rejected by 16.1. Downgrading the frontend to TypeScript 5: rejected, it would undo ADR-010 for one development tool. Generating from a running dev server: rejected, because it would need the database up just to produce types.

**Consequences.** The override hides the peer mismatch, so a TypeScript upgrade must re-run `npm run gen:api` and the frontend checks to confirm the generator still works. The generated file could drift from the backend if someone changed an endpoint without regenerating, so the CI backend job regenerates it after the tests and fails on any difference.

---

### ADR-018: Requirement tiers follow the course milestones

Date 2026-10-07, status accepted. The specification was changed on 2026-10-06 in commit `d7e636a`; this record was added after the Phase 0 review found that change unrecorded.

**Context.** The specification first defined T1 as the minimum demonstrable product and T2 as what the final submission requires. Under that definition, DEP-4 (demo data loader), DEP-5 (production notes) and DEP-7 (hosting target) carried no tag and were therefore T1, keyword search (RET-5) was T2, and the keyword and hybrid search modes both arrived in Phase 7. The CSET400C framework (specification 27.4) expects a Milestone-2 AI/ML project to show dataset preparation, a baseline or initial model, an experimental pipeline and preliminary results.

**Decision.** T1 is redefined as the Milestone-2 scope, complete at the end of Phase 7, and T2 as what the final evaluation requires (specification 0, 2.3). Three changes follow:

- RET-5 keyword search moves into T1 as Phase 7, "Keyword baseline and first evaluation run". It is the baseline that the first evaluation run (20.6) compares semantic retrieval with.
- `HYBRID` search (RET-6) stays T2 and moves to Phase 8.
- DEP-4, DEP-5 and DEP-7 move to T2. They serve the final evaluation and are built in Phase 11.

Specification sections changed: 0, 2.3, 7.2 and 7.4 (the full-text column and its index arrive in Phase 7), 10, 15.2, 20 (a first evaluation run in T1, the full study in T2), 22 and 23.

**Alternatives.** Keeping keyword search in T2: rejected, because Milestone-2 would then have no baseline for its preliminary results. Keeping DEP-4, DEP-5 and DEP-7 in T1: rejected, because T1 is now the Milestone-2 scope and they belong to the final evaluation.

**Consequences.** T1 grows by keyword search, the evaluation harness and a first results report, on a plan with no spare week. Section 23 gives the cut order inside T1 if the feature freeze is at risk. The demo data loader, the production part of `docs/deployment.md` and the hosting decision (D17) wait for Phase 11.

---

### ADR-019: Phase 0 was reviewed by its owner alone

Date 2026-10-07, status accepted as a recorded exception to specification 24.2, for pull request #1 only.

**Context.** Specification 24.2 requires a teammate to review each pull request before it is merged, with reviewers rotating so that every member reads code in every area, and 27.7 counts those reviews as evidence of individual work. No teammate was available to review pull request #1, which holds Phase 0.

**Decision.** The owner, Uttkarsh, reviewed the branch alone on 2026-10-07 against specification Sections 3, 4, 17 and 22 and the Phase 0 exit criteria. The review found seven gaps, none blocking. Each was fixed in its own commit on the branch before merging.

**Alternatives.** Waiting for a teammate's review: not chosen, because no teammate was available.

**Consequences.** The review record for Phase 0 names only the owner, so it is no evidence that another member has read the Phase 0 code. The exception covers pull request #1 only; 24.2 applies unchanged to later pull requests.

---

### ADR-020: JWT access token and rotating refresh cookie (D7)

Date 2026-10-07, status accepted. The team chose the specification default with two changes.

**Context.** D7 offers the default of AUTH-6 (a short JWT access token plus a rotating refresh cookie) or a simpler variant (an access token only, 60 minutes, then sign in again). The default as written has two gaps. Section 15.2 made `POST /auth/logout` require a valid access token, so after the 15-minute token expires the client cannot log out and the 7-day refresh token stays valid. And rotation only rejected a reused old token: if an attacker copied a refresh token and used it first, the attacker kept a valid chain while the real user was refused.

**Decision.** The default of AUTH-6, with two changes.

- Access token: a JWT signed with HS256, 15 minutes. The key comes from the environment as base64 and must decode to at least 32 bytes (256 bits), or the application does not start. The token carries only the issuer, the subject (the user id), and the issue and expiry times. Role and enabled flag are read from the database on every request (AUTH-7). The SPA keeps the token in memory.
- Refresh token: 32 random bytes, stored as their SHA-256 hash, valid 7 days, rotated on every use. Cookie `refresh_token`: `HttpOnly`, `Secure` (except in `dev`), `SameSite=Strict`, `Path=/api/v1/auth`.
- Change 1: logout is authorized by the refresh cookie and the `Origin` check, not by the access token. It revokes the token, clears the cookie and always answers `204`.
- Change 2: every token rotated from one sign-in shares a `family_id`. Presenting a token that was already rotated revokes the whole family, as the OAuth 2.0 Security Best Current Practice (RFC 9700, refresh token rotation) describes: two clients are using one token, one of them holds a stolen copy, and the server cannot tell which.
- Refresh and logout reject a request whose `Origin` header is present and not in `app.security.allowed-origins`. A request without `Origin` is accepted, because browsers send `Origin` on every cross-origin `POST` and non-browser clients send none; `SameSite=Strict` already keeps the cookie off cross-site requests (AUTH-9).

**Alternatives.** The simpler variant: rejected by the team, because the SPA could not keep a session across reloads without storing a token in the browser, and the refresh test of 19.2 would not apply. The default exactly as written: rejected for the two gaps above. Hashing refresh tokens with Argon2id or bcrypt: rejected, because a token must be found by its value, which needs a deterministic hash, and 256 random bits cannot be guessed, so a slow hash adds nothing. An RS256 key pair: rejected, because the same application signs and verifies, so a key pair only adds key handling.

**Consequences.** `refresh_token` gets a `family_id` column (specification 7.2). Logout works with an expired access token. A client that sends two refresh requests with the same cookie at the same moment logs itself out, so the SPA must run one refresh at a time, also across tabs. The specification (6 AUTH-6, 15.2, 26) was corrected.

---

### ADR-021: Tailwind CSS 4 with React Aria Components (D18)

Date 2026-10-07, status accepted. Exact versions and the maintenance check are recorded when the packages are added, together with the other frontend libraries.

**Context.** UI-8 asks for keyboard operation, labelled controls, managed focus in dialogs and WCAG 2.1 AA contrast, and UI-9 for one consistent set of components. The default of D18, "Tailwind CSS with one accessible component set", named no set, and neither appeared in specification 3.1 (open issue S5). The screens up to Milestone-2 need text fields with labels and error messages, selects, switches, dialogs, tables and menus.

**Decision.** Tailwind CSS 4, through its Vite plugin, for styling. React Aria Components (Adobe, `react-aria-components`, 1.21.1 released 2026-10-06 when checked) as the one component set: unstyled components that provide the accessibility behavior, styled with Tailwind classes.

**Alternatives.** shadcn/ui: a generator that copies styled component source, built on Radix, into the repository, with several helper packages. Rejected: the fastest to look finished, but it puts a large amount of generated code into the repository under the committer's name. Radix UI primitives (`radix-ui` 1.7.0): no text field or table, so those would be built by hand. Headless UI (2.2.10, last release 2026-04-13): a smaller set with no table and a slower release pace.

**Consequences.** Focus management, keyboard handling and label wiring come from a maintained library instead of hand-written code. All styling is in Tailwind classes, so there is one place for spacing and colors. Specification 3.1, 16.1 and 26 were corrected.

---

### ADR-022: Passwords are hashed with Argon2id

Date 2026-10-07, status accepted.

**Context.** AUTH-4 allowed bcrypt or Argon2id, with a maximum of 128 characters. In Spring Security 7.1.1, bcrypt rejects any password longer than 72 bytes: `BCrypt` in `spring-security-crypto-7.1.1.jar` throws "password cannot be more than 72 bytes". A character takes up to 4 bytes in UTF-8, so a valid 128-character password would fail at registration with a server error, and so would a 25-character one written in Devanagari, which takes 3 bytes per character. Spring Security's `Argon2PasswordEncoder` uses BouncyCastle's Argon2 implementation, which Spring Security does not bring in.

**Decision.** Passwords are stored through Spring Security's `DelegatingPasswordEncoder`, which hashes new passwords with Argon2id using Spring's current default parameters (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`), under the id `argon2@SpringSecurity_v5_8`. Add `org.bouncycastle:bcprov-jdk18on` 1.86 (released 2026-09-11, actively maintained by the Legion of the Bouncy Castle), pinned in `backend/pom.xml` because Spring Boot does not manage it. Password lengths, 10 to 128, count Unicode code points.

**Alternatives.** bcrypt with a maximum of 72 bytes: rejected, because it changes AUTH-4 and refuses long passphrases. Hashing the password with SHA-256 before bcrypt: rejected, a home-made construction. PBKDF2, which needs no extra library: rejected, it is the weakest of the options in the OWASP Password Storage Cheat Sheet, which recommends Argon2id first.

**Consequences.** One more dependency to keep current. Each hash costs more memory and time than bcrypt, by design; this matters only for sign-in and registration. The `{id}` prefix that the delegating encoder stores lets the algorithm or its parameters change later without invalidating existing hashes. Specification 3.3 and AUTH-4 were corrected.

---

### ADR-023: Every error is a Problem Details response

Date 2026-10-07, status accepted.

**Context.** Specification 15.1 requires RFC 9457 Problem Details with a stable `code`, field errors for validation, and no internal data. Spring Boot leaves its Problem Details support off by default, and responses written by the Spring Security filter chain (`401`, `403`) are not Problem Details. Errors raised outside Spring MVC, such as a request rejected by Spring Security's firewall, go to the servlet container's error path, where Boot's `BasicErrorController` writes its own JSON (open issue S6).

**Decision.** Three parts, with the mapping from exception to response in one place (24.4).

- One `@RestControllerAdvice` in `common` that extends Spring's `ResponseEntityExceptionHandler`. Spring MVC's own exceptions keep their standard Problem Details. Every response gets a `code`, taken from the domain exception or from a table by status. Validation failures add `errors`, a list of field and message. Anything unexpected becomes a generic `500` with the code `INTERNAL`; the exception is logged and never put in the body. Boot's `spring.mvc.problemdetails.enabled` stays off, because this handler replaces Boot's.
- The filter chain's authentication entry point and access-denied handler pass the exception to the same handler, so `401` and `403` are mapped there too.
- An `ErrorController` replaces Boot's `BasicErrorController`, so the container's error path also answers with Problem Details. Error dispatches are permitted by the security rules, so they are not turned into `401`.

**Alternatives.** Switching on Boot's property alone: rejected, it covers neither the filter chain nor the error path and adds no `code`. Writing the JSON by hand in each security handler: rejected, a second mapping that could drift from the first.

**Consequences.** The SPA can rely on `code` for every failure. The list of codes is kept in `docs/api.md`. A test checks each path: validation, unreadable body, `401` and `403` from the filter chain, `403` from method security, `404`, `405`, `409`, `415`, `500`, and a request rejected by the firewall.

---

### ADR-024: The audit log is append-only and written through JDBC

Date 2026-10-07, status accepted. It settles open issue S4 and the `audit_log` part of S3.

**Context.** Specification 7.2 required `audit_log` to be append-only with no update or delete path, but gave it the `updated_at` column that every table inherited (S3). AUD-4 requires the table and `AuditService.record(...)` in Phase 1. When `ACCESS_DENIED` is recorded was undefined (S4). The client address must be one a client cannot forge: nginx appends to `X-Forwarded-For`, so a client can put any address at the front of that header.

**Decision.**

- Table: a `bigint` identity key and `occurred_at` as the only time column; no `created_at` or `updated_at`. `actor_user_id` references `app_user` with `ON DELETE SET NULL` and is indexed. `ip_address` is `inet`, `details` is `jsonb`. Check constraints list the outcomes and every action in 14.2, so later phases need no migration to add their actions.
- A trigger rejects `UPDATE` and `DELETE` on the table. Revoking those privileges was not enough, because the application connects as the table's owner, whom a `REVOKE` does not bind.
- Entries are written through `JdbcClient`, with the time from the injected `Clock`. There is no JPA entity, so Hibernate never has an object it could update, and the `jsonb` column needs no Hibernate JSON mapping, which would have to choose between the Jackson 3 the application uses and the Jackson 2 that springdoc brings.
- A successful action's entry joins the caller's transaction, so the change and its entry commit together or not at all. `DENIED` and `FAILURE` entries are written in a transaction of their own, so the rollback of the failed action does not erase them.
- `details` never holds passwords, tokens or keys (AUD-2); the service refuses detail keys that name a password, token or secret.
- `ACCESS_DENIED` is recorded for a `403` only. A `404` for a document the user cannot read is not recorded: ACL-8 says it must behave exactly like a missing document, and an extra database write on only that path is a timing difference that could reveal that the document exists. `401` responses are not recorded; `LOGIN_FAILURE` covers wrong credentials.
- The address is the request's remote address. nginx sets `X-Forwarded-For` to the address it received the connection from, replacing anything the client sent, and the backend reads that header only from a proxy on a private network (`server.forward-headers-strategy=native`).

**Alternatives.** A JPA entity marked immutable: rejected for the reasons above. Recording `404`s for unreadable documents: rejected (timing). Keeping `updated_at` on a table whose rows never change: rejected.

**Consequences.** A future decision to delete users (AUTH-11 says they are never deleted) would be blocked by the trigger, because `SET NULL` is an update; it would need a migration of its own, which is the safe direction for an audit trail. Specification 7.1, 7.2, 7.4 and 14.2 were corrected.

---

### ADR-025: Specification corrections for Phase 1

Date 2026-10-07, status accepted. It settles open issues S2, S24 and the Phase 1 tables of S3. The rest of S3 (unique constraints on `document_grant` and `document_summary`, the `ON DELETE` rules of `document.owner_id` and `query_log.user_id`) stays open for Phases 2 and 9.

**Context.** Checking the specification before Phase 0 found gaps that Phase 1 must settle (`docs/PROGRESS.md`, open specification issues). Planning Phase 1 found two more: registration and its domain list sit in different modules, and the last-admin rule can be broken by two concurrent requests.

**Decision.**

- S2: `GET /auth/me` and `PUT /auth/me/password` are built in Phase 1; the role-aware shell needs the first. Changing one's own password requires the current one, revokes all of the user's refresh tokens, and is recorded as the new audit action `PASSWORD_CHANGED`. `PASSWORD_RESET` stays the admin action `[T2]`. Section 16.2 lists no screen for it, so Phase 1 adds none.
- S3 for the Phase 1 tables: `app_user.email` gets a check constraint that it is trimmed and lower-cased (AUTH-10). Foreign keys used in lookups get indexes that 7.4 left out: `refresh_token(user_id)`, `refresh_token(family_id)`, `audit_log(actor_user_id)`. JPA auditing reads the injected `Clock`, and tables written through `JdbcClient` take their times from the same `Clock`. The `audit_log` part is in ADR-024.
- S24: Phases 1, 2 and 6 now read 14.2 and Appendix B. Section 2.2 says SSO is excluded from T1 and T2, because 2.3 lists it as T3. Appendix B's preamble now matches steps 1 to 3: both documents of step 2 are uploaded live, and everything else is indexed in advance.
- The allowed email domains of AUTH-1 move from `app.security` to `app.registration`. Registration belongs to the `user` module, and the `security` module depends on `user`; reading a `security` setting from `user` would make each module depend on the other.
- AUTH-2: the last-admin check and the change run under a lock on the enabled admins' rows. Without it, two admins demoting each other at the same moment would each see the other still enabled, and both changes would succeed.
- Registration with an email that is already registered answers `409` with the code `EMAIL_TAKEN`. This tells the caller that the email has an account. Avoiding that would need email verification, which 2.2 excludes, so it is accepted and listed in the threat table of `docs/security.md`. AUTH-5 still applies to login.

**Alternatives.** Leaving `/auth/me` and the password change to a later phase: rejected, because the shell needs `/auth/me` and both are T1. Reading the domain list from `app.security` inside the `user` module: rejected, it creates the circular module dependency above.

**Consequences.** Specification 2.2, 6, 7.1, 7.2, 7.4, 14.2, 15.2, 18, 23 and Appendix B were corrected. The configuration table in Section 18 now has the groups `app.registration` and `app.bootstrap-admin`.

---

### ADR-026: Phase 1 is built by one member

Date 2026-10-07, status accepted as a recorded exception to specification 24.1, for Phase 1 only.

**Context.** Specification 24.1 splits the work by area: backend and security to member 1, and the screens, Docker and CI to member 3. Phase 1 has work in both areas. The other members are not available yet.

**Decision.** Uttkarsh builds all of Phase 1: backend, security, database, screens, Docker and CI changes, and the documentation.

**Alternatives.** Leaving the screens for the frontend member: not chosen, because that member is not available yet and Phase 1 cannot meet its exit criteria without the screens.

**Consequences.** Every Phase 1 commit is evidence of Uttkarsh's work only. Nobody else is credited for Phase 1. The exception covers Phase 1 only; 24.1 applies unchanged to later phases.

---

### ADR-027: Frontend libraries for routing, server state, forms and components

Date 2026-10-07, status accepted.

**Context.** Phase 1 builds the first screens: sign-in, registration, a role-aware shell, the admin user list, and the not-found and not-allowed pages. Specification 3.1 and 16.1 name React Router, TanStack Query, React Hook Form with Zod, and the component approach of D18 (ADR-021), but no package was installed yet, and 24.3 rule 7 requires a record of need, alternatives and maintenance status for each dependency. All packages below were checked on 2026-10-07: release date of the installed version, and the repository on GitHub (not archived, date of the last push).

**Decision.**

| Package | Version | Released | Repository, last push | Licence | Need |
|---|---|---|---|---|---|
| `react-router` | 8.4.0 | 2026-09-15 | remix-run/react-router, 2026-10-06 | MIT | Routes, protected routes and redirects (16.2) |
| `@tanstack/react-query` | 5.104.1 | 2026-10-02 | TanStack/query, 2026-10-06 | MIT | Server state, cache cleared on sign-out (UI-1) |
| `react-hook-form` | 7.89.0 | 2026-09-26 | react-hook-form/react-hook-form, 2026-10-06 | MIT | Form state and errors |
| `zod` | 4.6.5 | 2026-09-13 | colinhacks/zod, 2026-10-02 | MIT | Form validation rules in one schema per form |
| `@hookform/resolvers` | 5.9.1 | 2026-08-17 | react-hook-form/resolvers, 2026-09-17 | MIT | Connects React Hook Form to Zod. Not named in 3.1, but it is how the two are used together |
| `react-aria-components` | 1.21.1 | 2026-09-04 | adobe/react-spectrum, 2026-10-07 | Apache-2.0 | Accessible components (ADR-021, UI-8) |
| `tailwindcss`, `@tailwindcss/vite` (development) | 4.3.3 | 2026-07-16 | tailwindlabs/tailwindcss, 2026-09-25 | MIT | Styling at build time (ADR-021) |
| `@testing-library/user-event` (development) | 14.6.7 | 2026-09-02 | testing-library/user-event, 2026-09-02 | MIT | Component tests that type and click like a user: it sends the full sequence of pointer and keyboard events a browser sends, which React Aria's components listen to, where `fireEvent` sends one synthetic event |

React Router 8 is used in data mode (`createBrowserRouter`). Version 8 removed the `react-router-dom` package: `RouterProvider` comes from `react-router/dom` and everything else from `react-router`. Its minimum versions are Node.js 22.22 and React 19.2.7; the project uses Node.js 24 (ADR-012) and React 19.3.

The access token is kept in a module variable, not in React state that a library persists and not in browser storage (AUTH-6). The API is called with `fetch` through one small client written for this project, typed by the generated `schema.d.ts`, so no HTTP client library is added.

**Alternatives.** An HTTP client such as axios, or a typed client generated from the OpenAPI document (openapi-fetch): rejected, because one function around `fetch` covers the bearer header, the single silent refresh and Problem Details errors, and the types already come from `openapi-typescript` (ADR-017). Formik instead of React Hook Form: not chosen, the specification names React Hook Form. Testing with `fireEvent` only: rejected, single synthetic events test less of what a user does, such as opening a select and choosing an option with the keyboard.

**Consequences.** Eight packages to keep current. React Router 8 follows a fast release cadence, so an upgrade re-runs the frontend checks. The versions resolved by the lockfile are in `docs/VERSIONS.md`.

---

### ADR-028: Owner review with an independent review while no teammate can review

Date 2026-10-14, status accepted as a standing exception to specification 24.2. It applies from the Phase 1 pull request on and ends as soon as a teammate can review.

**Context.** Specification 24.2 requires a teammate to review each pull request before it is merged, with reviewers rotating, and 27.7 counts those reviews as evidence of individual work. ADR-019 made a one-time exception for pull request #1. The other members are still not available (ADR-026), and recording a new exception for every pull request would repeat one decision.

**Decision.** While no teammate can review, every pull request is reviewed in two parts before it is merged:

- The owner reviews the branch against the specification sections of its phase and its exit criteria.
- An independent review is made in a fresh session that took no part in building the branch, so the code is read without the assumptions of the person who wrote it.
- The pull request lists the findings of both reviews, and for each one the commit that fixed it or the reason it was left unfixed.

Every other merge rule of 24.2 stays: CI is green, and nothing is committed to `main` directly. The exception ends when a teammate can review; from then on 24.2 applies unchanged, and the first pull request a teammate reviews says so.

**Alternatives.** A new exception per pull request, as ADR-019 did: rejected, because it repeats the same decision. Merging without a review until a teammate is available: rejected, because reviews find defects; the review of Phase 0 found seven. Waiting for a teammate before merging: not chosen, because the plan of Section 23 has no spare week.

**Consequences.** Review records under this exception name the owner as the reviewer, so they are no evidence that another member has read the code (27.7). The findings and fixes listed in each pull request make each review checkable afterwards. ADR-019 remains the record for pull request #1.

---

### ADR-029: Local storage, PDF only, admins read everything, no student uploads (D8, D9, D15, D16)

Date 2026-10-26, status accepted. The team keeps the four specification defaults that Phase 2 needs.

**Context.** Phase 2 needs four register items (specification 26): where files are stored (D8), which formats are accepted (D9), what admins may read (D15) and whether students may upload (D16).

**Decision.**

- D8: files are stored on a local volume, behind the `FileStorage` interface (4.4), under generated keys (DOC-2). The Compose stack mounts a named volume for them.
- D9: PDF only. `app.upload` has no allowed-types setting: the content check is written for PDF, so a second type needs code anyway, and a setting with only one value that works would be misleading. Specification 18 was corrected.
- D15: admins read every document (the Admin column of the access matrix), but not other users' conversations (Phase 6).
- D16: students cannot upload. Upload requires ADMIN or FACULTY by URL rule and by method security; a student gets `403` and an `ACCESS_DENIED` entry.

**Alternatives.** S3-compatible storage: T3, and MinIO's community edition is archived (3.3). Word or plain-text files: they need another extractor and their own page model, which T1 does not need. Student uploads: they would put unreviewed files into the shared corpus.

**Consequences.** Backups of the Compose stack must include the files volume as well as the database volume. Moving to S3-compatible storage later means a second `FileStorage` implementation and no change to callers.

---

### ADR-030: Specification corrections for Phase 2

Date 2026-10-26, status accepted. It settles open issues S7, S8, S9, S10 and the Phase 2 tables of S3.

**Context.** Checking the specification before Phase 0 found gaps that Phase 2 must settle (`docs/PROGRESS.md`, open specification issues).

**Decision.**

- S3, Phase 2 tables. `document_grant` rows are replaced as a set and never changed, so the table has `created_at` and no `updated_at`. One grant per document and grantee is a unique constraint with `NULLS NOT DISTINCT` over `(document_id, grantee_type, grantee_role, grantee_user_id)`. `document.owner_id` is `ON DELETE RESTRICT`, because users are never deleted (AUTH-11); `document_grant.created_by` is `ON DELETE SET NULL`. DOC-2's duplicate rule is the unique constraint `(owner_id, sha256)`, which also serves the lookups by owner; the separate indexes on `document(owner_id)`, `document(sha256)` and `document_grant(document_id)` are dropped from 7.4 as redundant.
- S7, status machine. `PROCESSING -> UPLOADED` is legal, but only for the stuck-job recovery of ING-2, never through the API. Reprocess resets the attempt counter, because it is a person's decision to try again and the counter exists to stop crash loops. When recovery runs out of attempts, the document becomes `FAILED` with the new code `PROCESSING_TIMEOUT`. Reprocessing a document that is `UPLOADED` or `PROCESSING` answers `409` with the code `INVALID_STATUS`. `EMPTY`, `UNSUPPORTED_TYPE` and `ENCRYPTED` are caught at upload; ingestion uses them only when the stored file no longer passes the same check.
- S8, access matrix. The Admin and Faculty A cells of D5 and D6 are "read, not retrievable", because ACL-4 applies to everyone. D5 keeps leftover chunks once `document_chunk` exists (Phase 3), so the retrieval tests prove the status filter.
- S9, 403 or 404. A user who can read a document but not manage it gets `403` for a manage action, with an `ACCESS_DENIED` entry: the document's existence is already known to them. A user who cannot read it gets `404` and no entry (ACL-8, ADR-024). Manage rights belong to ADMIN and to the owner while the owner is FACULTY; a demoted owner keeps read access through ACL-1 but no longer manages.
- S10, reprocess. `POST /documents/{id}/reprocess` is built in Phase 2: the status change, the counter reset and the `DOCUMENT_REPROCESSED` entry. It gives the test "illegal status transitions are rejected" a real endpoint. Its button arrives with Phase 3, when a document can reach `FAILED` or `INDEXED` in a running system.

**Alternatives.**

- `ON DELETE CASCADE` on `document.owner_id`: rejected, deleting an account would silently delete institutional documents.
- Keeping the attempt counter across reprocess: rejected, a document that once hit the limit could never be tried again. `INTERNAL` for exhausted attempts: rejected, it mixes a resource problem with real bugs in the failures-by-code figure (14.3). Accepting reprocess while a run is queued or running: rejected, two runs would race on one document.
- Letting owners and admins retrieve failed or archived documents: rejected, it contradicts ACL-4 and DOC-11.
- `404` for readable but unmanageable documents: rejected, it would tell someone looking at a document that it does not exist. Manage rights from ownership alone: rejected, a demoted account could keep sharing documents it no longer should control.
- Leaving reprocess to Phase 3: rejected, the transition rules would have no endpoint to test in Phase 2.

**Consequences.** Specification 5.2 (ACL-2), 5.4, 7.2, 7.4, 8.1 (DOC-1, DOC-7, DOC-9, DOC-10), 8.2, 8.3, 9.2 (ING-2, ING-4), 15.1, 15.2, 18, 23 and 26 were corrected. The `failure_code` check constraint lists `PROCESSING_TIMEOUT` from the start, so Phase 3 needs no migration for it.

---

### ADR-031: Upload, storage and download

Date 2026-10-26, status accepted.

**Context.** DOC-1 to DOC-9 leave open how the type is checked, how an encrypted PDF is treated, how metadata travels with the file, how a failed file deletion survives a restart, and how the SPA downloads a file and shows upload progress while the access token lives only in memory (AUTH-6). Specification 4.5 forbids holding a transaction across file I/O.

**Decision.**

- Content check: the file must start with `%PDF-` and PDFBox must open it. A file that needs a password to open is rejected at upload with `400` and the code `PDF_ENCRYPTED`; a file protected only by an owner password opens and is accepted. The check runs before anything is stored. Add `org.apache.pdfbox:pdfbox` 3.0.8 (released 2026-07-15, Apache-2.0, maintained by the Apache PDFBox project), pinned in `backend/pom.xml` because Spring Boot does not manage it. Spring AI 2.0.2's PDF reader depends on PDFBox 3.0.7, so Phase 3 stays on the same line. The tests generate their fixture PDFs with it (19.1).
- Upload request: a multipart request with a `file` part and a `metadata` part in JSON (title, description, category, document date, grants), validated with Bean Validation like every other request body.
- Order of work: check the file and compute its SHA-256 without a transaction, store it under a generated key, then insert the row, its grants and the audit entry in one short transaction. The unique constraint `(owner_id, sha256)` decides concurrent duplicates; when the insert fails, the stored file is removed. A duplicate answers `409` with the code `DUPLICATE_DOCUMENT` and the existing document's id.
- Deletion: the transaction that deletes the rows also inserts the file's key into `storage_deletion`. After commit the file is deleted and its row removed; a scheduled task retries the rows that remain, so a failed deletion survives a restart.
- Download: `GET /documents/{id}/content` checks access and writes the audit entry in a short transaction, then streams the file with no transaction, with `Content-Disposition: attachment` built by Spring's `ContentDisposition` from a sanitized filename, `X-Content-Type-Options: nosniff` and `Cache-Control: no-store` (a revoked grant must not be served from a cache, ACL-7). The SPA fetches it with the bearer token and saves the blob.
- Upload progress: the SPA sends the upload with `XMLHttpRequest`, which reports upload progress; `fetch` does not. It uses the same in-memory token and the same single silent refresh as every other call.
- `PATCH /documents/{id}` replaces title, description, category and document date together, sets `archived`, or both. Archiving and unarchiving are both recorded as `DOCUMENT_ARCHIVED` with the new value.
- `GET /documents/upload-limits` returns the size limit and the accepted type, so the SPA's client-side check (UI-4) uses the server's values. It is not part of `/meta`, which belongs to the `common` module and would otherwise depend on the `document` module.
- nginx accepts request bodies up to 30 MB, slightly above the 25 MB file limit plus the multipart overhead, so the backend's Problem Details answer the oversized case; nginx's own `413`, for anything larger, is rewritten as Problem Details too.

**Alternatives.** Accepting encrypted files and failing them in ingestion: rejected, the uploader would learn of the problem only after processing. Checking the extension or the client's content type: rejected by DOC-1. Flat form fields for metadata: rejected, grants would need an encoding of their own and validation would differ from the JSON endpoints. Deleting the file inside the transaction: rejected, a rollback cannot bring the file back. Retrying deletions from memory: rejected, a restart forgets them. A sweep that compares the volume with the table: rejected, it must guess which files belong to uploads still in progress. Signed download URLs: rejected, they add a second kind of token, and tokens in URLs end up in logs and history. A plain link to the file: impossible, a link cannot carry an in-memory token.

**Consequences.** One more backend dependency to keep current. A crash between storing a file and inserting its row leaves an unreferenced file on the volume; nothing removes it yet (listed under known issues). Raising `APP_UPLOAD_MAX_FILE_SIZE` above about 29 MB also needs nginx's `client_max_body_size` raised.

---

### ADR-032: One member builds every phase

Date 2026-10-26, status accepted as a standing exception to specification 24.1. It supersedes ADR-026, which covered Phase 1 only.

**Context.** Specification 24.1 splits the work by area among the members. ADR-026 recorded that Uttkarsh built Phase 1 alone because the other members were not available. They are still not available, and the owner has decided to build every phase alone.

**Decision.** Uttkarsh builds every phase: backend, security, database, screens, Docker and CI changes, and the documentation. Reviews follow ADR-028.

**Alternatives.** A new exception for each phase, as ADR-026 did: rejected, it repeats one decision. Waiting for the other members: not chosen, the plan of Section 23 has no spare week.

**Consequences.** Every commit is evidence of Uttkarsh's work only (27.7). If another member joins, a new record states which areas they take over from then on.
