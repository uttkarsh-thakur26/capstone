import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Development mode (DEP-3): the backend runs on the host and the SPA calls it on its
    // own origin, as it does behind the web server in Compose.
    // The Playwright test points it at its own backend (API_PROXY_TARGET, 16.4).
    proxy: { '/api': process.env.API_PROXY_TARGET ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // The end-to-end test in e2e/ runs in Playwright, not here.
    include: ['src/**/*.test.{ts,tsx}'],
    // The slowest tests take about 2.6 s alone and passed 5 s with a container build running
    // beside them; 15 s keeps a busy machine from failing them (ADR-039).
    testTimeout: 15_000,
  },
})
