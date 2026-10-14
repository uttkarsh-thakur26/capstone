# Resolved versions

What the build actually resolved, not what was requested (specification 3.2 rule 5). Updated at the end of Phase 0 on 2026-10-06, from commit `2c1d580`; the libraries added in Phase 1 were recorded on 2026-10-07 from branch `feat/auth-roles`. Update this file in the same commit as any dependency change.

How these were collected: `./mvnw dependency:list` and `./mvnw -v` in `backend`, `npm ls --depth=0` in `frontend`, `docker image inspect --format '{{index .RepoDigests 0}}'` for images, and the tools' own `--version` output.

## Toolchain

| Tool | Version | Where it is set |
|---|---|---|
| JDK | Eclipse Temurin 25.0.4.1+1 (LTS) | Local `JAVA_HOME`; CI `actions/setup-java` with `java-version: '25'`; images `eclipse-temurin:25.0.4.1_1-*` (ADR-003) |
| Maven | 3.9.16, through Maven Wrapper 3.3.4 | `backend/.mvn/wrapper/maven-wrapper.properties` (ADR-002) |
| Node.js | 24.19.0 locally, 24.21.0 in the image, the 24 line in CI | `frontend/.nvmrc`, `engines` `^24.15.0`, `frontend/Dockerfile` (ADR-012) |
| npm | 11.17.0 locally | |
| Docker Engine / Compose | 29.8.2 / v5.5.1 (Docker Desktop) | Development machine |

## Backend

Managed by the Spring Boot 4.1.1 parent unless marked pinned.

| Library | Version |
|---|---|
| Spring Boot | 4.1.1 |
| Spring Framework | 7.0.9 |
| Spring Security | 7.1.1 |
| Spring Security OAuth2 resource server, JOSE and core, through `spring-boot-starter-security-oauth2-resource-server` (AUTH-6) | 7.1.1 |
| Nimbus JOSE + JWT, through Spring Security | 10.9.1 |
| BouncyCastle (`org.bouncycastle:bcprov-jdk18on`), for Argon2id password hashing, **pinned** (ADR-022) | 1.86 |
| Hibernate ORM | 7.4.5.Final |
| Jakarta Persistence | 3.2.0 |
| Hibernate Validator | 9.1.3.Final |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | 12.4.0 |
| PostgreSQL JDBC driver | 42.7.13 |
| Jackson 3 (`tools.jackson.core:jackson-databind`) | 3.1.5 |
| Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`), brought by springdoc | 2.21.5 |
| Tomcat | 11.0.24 |
| Micrometer | 1.17.1 |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`), **pinned** (ADR-014) | 3.1.1 |
| swagger-core (jakarta), Swagger UI webjar (via springdoc) | 2.2.55, 5.32.14 |
| JUnit Jupiter | 6.0.3 |
| Mockito | 5.23.0 |
| AssertJ | 3.27.7 |
| Testcontainers (`testcontainers`, `testcontainers-postgresql`) | 2.0.5 |
| Spotless Maven plugin, **pinned** (ADR-009) | 3.10.3 |
| google-java-format, **pinned** (ADR-009) | 1.36.1 |

Spring AI 2.0.1 is the planned line (specification 3.1) but is not on the classpath yet; the BOM is added with the first Spring AI dependency.

## Frontend

From `frontend/package-lock.json`. Ranges in `package.json` follow the generator; the lockfile pins the exact versions, and CI installs with `npm ci`.

| Package | Version | Kind |
|---|---|---|
| react, react-dom | 19.3.0 | runtime |
| react-router (ADR-027) | 8.4.0 | runtime |
| @tanstack/react-query | 5.104.1 | runtime |
| react-hook-form, @hookform/resolvers | 7.89.0, 5.9.1 | runtime |
| zod | 4.6.5 | runtime |
| react-aria-components (ADR-021) | 1.21.1 | runtime |
| tailwindcss, @tailwindcss/vite (ADR-021) | 4.3.3 | build |
| vite | 8.3.3 | build |
| @vitejs/plugin-react | 6.1.2 | build |
| typescript (ADR-010) | 6.0.3 | build |
| eslint, @eslint/js | 10.12.0, 10.0.1 | lint |
| typescript-eslint | 8.71.1 | lint |
| eslint-plugin-react-hooks, eslint-plugin-react-refresh | 7.1.1, 0.5.7 | lint |
| eslint-config-prettier | 10.1.8 | lint |
| prettier | 3.9.9 | format |
| vitest | 5.0.3 | test |
| jsdom | 30.1.2 | test |
| @testing-library/react, @testing-library/dom, @testing-library/jest-dom | 16.3.3, 10.4.2, 7.0.1 | test |
| @testing-library/user-event (ADR-027) | 14.6.7 | test |
| openapi-typescript, with an override to the project's TypeScript (ADR-017) | 7.13.0 | type generation |
| @types/react, @types/react-dom, @types/node | 19.3.0, 19.3.0, 24.19.1 | types |
| globals | 17.13.0 | lint |

Generator: create-vite 9.2.1 (ADR-011).

## Container images

| Image | Digest | Contents checked |
|---|---|---|
| `pgvector/pgvector:0.8.7-pg18-trixie` (pinned by digest, ADR-015) | `sha256:9d9c930220cb9bf2f956d10a8f909cf9973d9672ca0278aef2c1e6facccad2e0` | PostgreSQL 18.6, pgvector 0.8.7, `PGDATA=/var/lib/postgresql/18/docker` |
| `eclipse-temurin:25.0.4.1_1-jdk` (backend build stage) | `sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc` | Ubuntu 26.04.1 |
| `eclipse-temurin:25.0.4.1_1-jre` (backend runtime) | `sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636` | Ubuntu 26.04.1, no curl or wget |
| `node:24.21.0-alpine` (frontend build stage) | `sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1` | Alpine 3.24 |
| `nginxinc/nginx-unprivileged:1.30.5-alpine` (frontend runtime) | `sha256:15c994d10d6d78658721c3bcafff14cb281fba2a4bdf9d5ba92c416a472516e3` | Alpine 3.24, runs as uid 101 |

Only the database image is pinned by digest in `compose.yaml` and the tests. The build images are pinned by full version tag; the digests above are what was pulled on 2026-10-06.

## CI actions

`actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7` (major-version tags).
