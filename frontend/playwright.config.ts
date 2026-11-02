import { defineConfig, devices } from '@playwright/test'

/**
 * The end-to-end test of 16.4 (S20, ADR-038): the real SPA in Chromium against the real backend,
 * started as the test application (PostgreSQL in Testcontainers and the fake models of 19.1, so no
 * real model is ever called), each on a port of its own.
 */
const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'

export default defineConfig({
  testDir: './e2e',
  // Starting the backend compiles it and starts a database; the test itself then takes seconds.
  timeout: 120_000,
  expect: { timeout: 30_000 },
  workers: 1,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${mvnw} -q spring-boot:test-run -Dspring-boot.run.profiles=test,e2e`,
      cwd: '../backend',
      url: 'http://localhost:18080/actuator/health',
      timeout: 300_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --port 5174 --strictPort',
      url: 'http://localhost:5174',
      env: { API_PROXY_TARGET: 'http://localhost:18080' },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
