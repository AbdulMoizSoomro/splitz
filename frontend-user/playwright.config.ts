import { defineConfig, devices } from "@playwright/test";

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: "./src/test/e2e",
  /* Run tests in files in parallel */
  fullyParallel: true,
  /**
   * Per-test budget. These specs register two or three users and wait on a RabbitMQ-mediated
   * friendship round trip before they can assert anything, which does not fit Playwright's 30s
   * default. Specs should not carry their own setTimeout; raise it here so there is one source of
   * truth.
   */
  timeout: 180_000,
  expect: { timeout: 15_000 },
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Was pinned to 1 on CI, making the suite's whole runtime wall clock. Left undefined so Playwright
     applies its cores/2 heuristic: runners vary, and the suite already shares one Postgres, one
     RabbitMQ, two JVMs and a Vite server with the browsers. E2E_WORKERS overrides. */
  workers: process.env.E2E_WORKERS ? Number(process.env.E2E_WORKERS) : undefined,
  reporter: [["list"], ["html", { open: "never" }]],
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: "on-first-retry",
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
