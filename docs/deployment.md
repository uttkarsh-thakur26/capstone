# Deployment

The local part is started in Phase 0. The demo and production parts are written in Phase 11 (DEP-5).

## Local

The steps to start the system from a clean clone are in the [README quickstart](../README.md#quickstart), which is the one place they are maintained (DEP-2).

What the stack is made of:

| Service | Image | Notes |
|---|---|---|
| `db` | `pgvector/pgvector:0.8.7-pg18-trixie`, pinned by digest (ADR-015) | Named volume `db-data` at `/var/lib/postgresql`. Port 5432 on `127.0.0.1` only, for development mode |
| `backend` | Built from `backend/Dockerfile`: Temurin 25 JDK to build, Temurin 25 JRE to run | Runs as user `app` (uid 10001), profile `prod` (ADR-013). Health check on `/actuator/health/readiness`. Port not published |
| `frontend` | Built from `frontend/Dockerfile`: Node.js 24.21 to build, `nginxinc/nginx-unprivileged:1.30.5-alpine` to serve | Forwards `/api` to `backend`. Port 3000 on `127.0.0.1` |

Configuration comes from `.env`, which is git-ignored. `.env.example` lists every variable the current phases read, with placeholders. Compose refuses to start if `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` or `APP_DISPLAY_NAME` is missing. The backend refuses to start while the database has no admin and `APP_BOOTSTRAP_ADMIN_EMAIL` or a valid `APP_BOOTSTRAP_ADMIN_PASSWORD` is missing (AUTH-3); once the first admin exists, the two variables are no longer read.

Start order is enforced by health checks: `backend` waits for a healthy `db`, and `frontend` waits for a healthy `backend`.

## Demo and production

Not written yet (Phase 11): HTTPS termination, secret handling, Swagger UI off, strict origins, and backups of the database and the file volume.
