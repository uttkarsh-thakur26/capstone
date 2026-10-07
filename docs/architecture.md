# Architecture

Started in Phase 0. It grows with each phase and is complete for Milestone-2 (specification Section 25). The design itself is specified in `docs/PROJECT_SPEC.md` Section 4; this file describes what exists and how it fits together.

## Containers

```mermaid
flowchart LR
  user([Browser])
  subgraph compose [Docker Compose stack]
    fe["frontend<br/>nginx: built SPA,<br/>proxies /api"]
    be["backend<br/>Spring Boot 4.1, Java 25<br/>profile prod"]
    db[("db<br/>PostgreSQL 18<br/>+ pgvector 0.8.7")]
  end
  user -- "HTTP :3000" --> fe
  fe -- "/api/*" --> be
  be -- "JDBC, Flyway" --> db
```

- The SPA and the API share one origin: nginx serves the built frontend and forwards `/api` to the backend (AUTH-9, DEP-1). The backend port is not published.
- In development mode (DEP-3) only `db` runs in Compose. The backend runs on the host with the `dev` profile, and the Vite dev server forwards `/api` to it.
- File storage and the model provider (specification 4.2) do not exist yet. They arrive in Phases 2, 3 and 5.

## Backend modules

Package by feature under `in.edu.bennett.capstone.knowledge` (ADR-006), with the module names of specification 4.2. Only modules that contain code exist.

| Module | Since | Responsibility so far | Public API | Depends on |
|---|---|---|---|---|
| `common` | Phase 0 | Settings (`app.display-name`), `GET /api/v1/meta`, the `Clock`, JPA auditing, the error model (`ApiException`, `ApiExceptionHandler`, the `/error` controller, `ProblemResponse` in the OpenAPI document), the page envelope | `ApiException`, `PageResponse`, `FieldErrorResponse`, `ProblemResponse` | none |
| `audit` | Phase 1 | Append-only audit entries through `JdbcClient`, with the client address and time (AUD-1 to AUD-4) | `AuditService` (`record`, `recordSeparately`), `AuditEvent`, `AuditAction`, `AuditOutcome` | none |
| `user` | Phase 1 | Accounts and roles: registration, the bootstrap admin, sign-in lookups, password change, the admin user list and role and enabled changes | `UserService`, `UserResponse`, `UserCredentials`, `Role`, `Emails`, `ValidPassword` | `common`, `audit` |
| `security` | Phase 0 | Default-deny filter chain, JWT access tokens, the per-request user (`CurrentUser`), refresh tokens and their cookie, the `Origin` check, sign-in, refresh, sign-out, `ACCESS_DENIED` auditing | `CurrentUser`; HTTP only otherwise | `common`, `user`, `audit` |
| `document`, `storage`, `ingestion`, `retrieval`, `assistant`, `conversation`, `analytics` | Not started | See specification 4.2 | | |

The dependencies run one way, so there is no cycle. Two choices keep them that way: the allowed email domains are read by `user` from `app.registration`, not from `app.security` (ADR-025), and the admin controller in `user` reads the acting admin's id from the principal by expression (`@AuthenticationPrincipal(expression = "id()")`), without a compile-time reference to `security`. Other modules use a module's service, never its repository or entities (specification 4.3): `app_user` is reached only through `UserService`, and `refresh_token` stores a user id, not a relation to the account entity.

### A request through the backend

`GET /api/v1/admin/users` from the SPA:

1. nginx forwards `/api` to the backend and sets `X-Forwarded-For` (ADR-024).
2. `SecurityConfig`: the bearer token is read and checked by Spring Security's resource server (signature, issuer, expiry; `JwtConfig`). `CurrentUserJwtConverter` loads the user by id; a disabled or missing user is a 401 (AUTH-7). The URL rule needs ADMIN, else 403 and `ACCESS_DENIED` through `AccessDeniedAuditor`.
3. `AdminUserController` validates the paging parameters and calls `UserService.listUsers`.
4. `UserService.listUsers` is checked again by `@PreAuthorize` (AUTH-8), runs one read-only query with the filters and the allow-listed sort, and maps entities to `AdminUserResponse` records. Entities never leave the module.
5. Any exception on the way becomes Problem Details in `ApiExceptionHandler` (ADR-023).

## Database

Schema changes go only through Flyway migrations in `backend/src/main/resources/db/migration`; Hibernate validates and never changes the schema (`ddl-auto=validate`). Tables, diagram and rules: [`data-model.md`](data-model.md).

| Migration | What it does |
|---|---|
| `V1__enable_pgvector.sql` | Creates the `vector` extension |
| `V2__app_user.sql` | Accounts |
| `V3__audit_log.sql` | Audit entries, append-only by trigger |
| `V4__refresh_token.sql` | Refresh tokens |

## Frontend

React 19 with TypeScript 6 (ADR-010), built by Vite 8, styled with Tailwind CSS 4 and React Aria Components (ADR-021, ADR-027).

| Folder | Holds |
|---|---|
| `src/api` | `schema.d.ts`, generated from the OpenAPI document (`npm run gen:api`, ADR-017) and never edited by hand; `types.ts`, short names for its types; `client.ts`, the one way the SPA calls the API (bearer token in memory, one silent refresh on 401, refreshes one at a time across tabs, `ApiError` with the Problem Details) |
| `src/auth` | `AuthProvider` (restores the session from the refresh cookie on start, signs in and out, clears the query cache on sign-out), `useAuth`, `RequireAuth` (the route guard) |
| `src/layout` | `AppShell` (navigation by role, user, sign-out), `AuthLayout`, the product name and version from `/meta` |
| `src/pages` | Sign-in, registration, home, admin users, not found, not allowed |
| `src/components` | Styled React Aria components (button, text field bound to React Hook Form, role select, enabled switch, alert) |
| `src/routes.tsx` | The routes of 16.2 that exist so far |
| `src/test` | `fakeApi` (a stubbed `fetch` with recorded requests) and `renderApp` (the whole app with a memory router) |

Server state lives in TanStack Query; forms in React Hook Form with Zod schemas that repeat the server's rules for a faster answer, while the server stays the authority. The role only shapes the navigation (UI-2).
