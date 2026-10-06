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

---

### ADR-009: google-java-format through Spotless (D24)

Date 2026-10-06, status accepted.

**Context.** Specification 24.4 requires one formatter per language, enforced in CI, and names Spotless for Java without a style. The style must be fixed before the first Java file, so that formatting never arrives later as one large commit that blurs each member's history.

**Decision.** The Spotless Maven plugin (3.10.3) with google-java-format (1.37.0), both pinned in `backend/pom.xml` because Spring Boot does not manage them. `spotless:check` is bound to the `verify` phase, so `./mvnw verify` fails on unformatted code locally and in CI. `./mvnw spotless:apply` formats the code.

**Alternatives.** palantir-java-format: a wider 120-column variant of the same formatter, not chosen. The Eclipse formatter: needs a shared settings file and gives no single canonical style.

**Consequences.** Formatting is not discussed in review. google-java-format has no configuration beyond its AOSP variant, so the style cannot drift.

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
