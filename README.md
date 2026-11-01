# AI-powered document and organizational knowledge platform

Working description. The product name is not decided yet (D1 in `docs/PROJECT_SPEC.md`); the running application shows whatever `APP_DISPLAY_NAME` says.

Users upload PDFs, the system indexes them, and people search and ask questions in natural language. Answers are built only from documents the asking user is allowed to read, they cite the pages they come from, and the system says so when the documents do not contain the answer. Final-year capstone project (CSET400C).

**Status:** Phases 0 to 3 are built: accounts and roles, document upload and sharing, and ingestion, which reads each PDF page by page, splits it into chunks and indexes them with an embedding model. Search and questions come in later phases. See [`docs/PROGRESS.md`](docs/PROGRESS.md).

## Quickstart

Needs Docker Desktop (or Docker Engine) with Docker Compose v2 or later, and [Ollama](https://ollama.com) running on the same machine with the embedding model pulled once:

```bash
ollama pull qwen3-embedding:0.6b
```

Ollama runs outside Compose so it can use the GPU; the backend container reaches it at `http://host.docker.internal:11434`. Without Ollama the stack still starts, and uploaded documents wait as "Waiting for processing" until the model answers.

```bash
git clone <repository-url>
cd <repository-folder>
cp .env.example .env
```

Open `.env` and set these to values of your own:

- `POSTGRES_PASSWORD`.
- `APP_SECURITY_JWT_SECRET`: the key that signs sign-in tokens, base64 of 32 random bytes. Generate it with `openssl rand -base64 32` (Git Bash, macOS, Linux), or in Windows PowerShell with `$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`.
- `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` (10 to 128 characters): the first admin account, created on the first start because no admin exists yet. There is no built-in account; with the placeholder password the backend refuses to start.
- `OPENAI_API_KEY`: the key of the OpenAI account that answers questions (`gpt-6-luna`, billed per token). Without it the backend refuses to start; set `CHAT_PROVIDER=none` instead to run everything except answering questions. The passages an answer is built from are sent to OpenAI, so use public documents only.

Then:

```bash
docker compose up --build
```

Open <http://localhost:3000> and sign in with `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`, or register: self-registration always creates a student account, and the admin can then change its role under Users. Stop with `Ctrl+C`; `docker compose down` removes the containers, and `docker compose down -v` also deletes the database volume.

The stack runs three containers: `db` (PostgreSQL 18 with pgvector), `backend` (Spring Boot, `prod` profile) and `frontend` (the built interface behind nginx, which also forwards `/api` to the backend). Only ports 3000 and 5432 are published, and only on `127.0.0.1`.

## Development mode

The database runs in Docker; the backend and frontend run on your machine with hot reload.

Needs, in addition: JDK 25 (Eclipse Temurin 25, with `JAVA_HOME` set) and Node.js 24 (see `frontend/.nvmrc`). No Maven installation is needed; the Maven Wrapper downloads it.

```bash
docker compose up -d db
```

Backend, in one terminal. The `dev` profile reads the repository's `.env`, so it uses the same database credentials, signing key and first admin; `.env` needs the same variables as for the quickstart. It accepts the refresh cookie only from the Vite dev server's origin, `http://localhost:5173`, and sends that cookie without `Secure`, because development runs on plain HTTP:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`. The backend listens on <http://localhost:8080>; in the `dev` profile the API documentation is at <http://localhost:8080/swagger-ui/index.html>.

From an IDE, use `backend` as the working directory. If the IDE builds the project itself rather than through Maven, run `./mvnw generate-resources` in `backend` once after cloning and after every full rebuild: it writes the build information that `/api/v1/meta` reports, and without it the application does not start. Tests started from an IDE need `-Duser.timezone=UTC` in their run configuration; Maven passes it by itself (ADR-016).

Frontend, in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. The dev server forwards `/api` to the backend on port 8080.

## Checks

These are the commands CI runs. Integration tests start PostgreSQL in a container, so Docker must be running.

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run format:check
```

CI also builds both images and starts the stack from `.env.example`, with a generated signing key and admin password and no chat model (`CHAT_PROVIDER=none`), with `docker compose up --build -d --wait`, then calls `/api/v1/meta` through nginx.

Formatting: `./mvnw spotless:apply` in `backend`, `npm run format` in `frontend`.

When an endpoint changes, run `./mvnw verify` in `backend` (it writes `backend/target/openapi.json`) and then `npm run gen:api` in `frontend` to regenerate the TypeScript types. CI fails if they differ.

## Repository layout

```
.github/workflows/  CI
backend/            Spring Boot application (Java 25, Maven Wrapper)
frontend/           React, TypeScript, Vite
docs/               specification, progress, decisions, versions, architecture, testing, deployment
compose.yaml        the whole system
.env.example        environment variables, with placeholders
```

## Documentation

- [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md): what the system does and how it is built
- [`docs/PROGRESS.md`](docs/PROGRESS.md): where the work stands
- [`docs/DECISIONS.md`](docs/DECISIONS.md): decision records
- [`docs/VERSIONS.md`](docs/VERSIONS.md): resolved versions
- [`docs/architecture.md`](docs/architecture.md), [`docs/data-model.md`](docs/data-model.md), [`docs/security.md`](docs/security.md), [`docs/api.md`](docs/api.md)
- [`docs/testing.md`](docs/testing.md), [`docs/deployment.md`](docs/deployment.md)
- [`docs/phase-reports/`](docs/phase-reports/): one report per finished phase
