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
