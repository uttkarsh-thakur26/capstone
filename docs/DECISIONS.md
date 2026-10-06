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
