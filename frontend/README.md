# Frontend

React, TypeScript and Vite single-page application. See the repository README for how to run the whole system.

Node.js 24 (see `.nvmrc`).

| Command                | What it does                                 |
| ---------------------- | -------------------------------------------- |
| `npm ci`               | Install the exact versions in the lockfile   |
| `npm run dev`          | Dev server; proxies `/api` to the backend    |
| `npm run lint`         | ESLint                                       |
| `npm run typecheck`    | TypeScript project check                     |
| `npm run build`        | Type check and production build into `dist/` |
| `npm run format:check` | Prettier check (`npm run format` rewrites)   |
