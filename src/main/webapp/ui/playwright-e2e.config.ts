import { resolve } from "node:path";
import { defineConfig, devices, type ReporterDescription } from "@playwright/test";
import type { E2EOptions } from "./src/__tests__/e2e/fixtures";
import { USERS } from "./src/__tests__/e2e/users";

// Load .env before anything reads process.env. process.loadEnvFile is built
// into Node 20.6+ — no dotenv package needed. Missing file is silently ignored
// so CI can inject vars directly without the file.
try {
  process.loadEnvFile(resolve(__dirname, ".env"));
} catch {
  // .env absent — CI injects vars directly
}

const BASE_URL = process.env.RSPACE_BASE_URL ?? "http://localhost:8080";
const HEADLESS = (process.env.HEADLESS ?? "true") !== "false";

// Log level: "trace" | "info" | "off" (default "off")
// Set PW_LOG=trace or PW_LOG=info to enable. "trace" also sets DEBUG=pw:api.
const PW_LOG = (process.env.PW_LOG ?? "off") as "trace" | "info" | "off";
if (PW_LOG === "trace" && !process.env.DEBUG) {
  process.env.DEBUG = "pw:api";
}

function getReporterConfig(): ReporterDescription[] {
  if (process.env.CI) {
    return [["list"], ["junit", { outputFile: "e2e-junit.xml" }], ["html", { open: "never" }]];
  }
  if (PW_LOG !== "off") {
    return [["list"], ["html", { open: "on-failure" }], ["line"]];
  }
  return [["list"], ["html", { open: "on-failure" }]];
}

export default defineConfig<E2EOptions>({
  // Discovers *.e2e.ts (UI browser specs) and *.api.spec.ts (Node HTTP specs)
  // anywhere under src/ — covers both specs/ and future colocated module tests.
  testDir: "./src",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 4 : undefined,
  reporter: getReporterConfig(),
  use: {
    baseURL: BASE_URL,
    // PW_LOG=trace -> capture every action; PW_LOG=info -> on first retry; off -> on first retry
    trace: PW_LOG === "trace" ? "on" : "on-first-retry",
    screenshot: PW_LOG === "off" ? "only-on-failure" : "on",
    video: PW_LOG === "trace" ? "on" : "retain-on-failure",
    // RSpace uses `data-test-id` (not the Playwright default `data-testid`).
    // This lets page objects call `page.getByTestId('create-btn')` cleanly.
    testIdAttribute: "data-test-id",
  },
  projects: [
    {
      // API tests: Node HTTP only, no browser. Each test gets its own
      // APIRequestContext via the `apiContext` fixture.
      name: "api",
      testMatch: "**/*.api.spec.ts",
    },
    {
      // Each browser uses a distinct seed user to avoid shared-state collisions
      // when browser shards run in parallel against the same backend.
      name: "chromium",
      testMatch: "**/*.e2e.ts",
      use: { ...devices["Desktop Chrome"], headless: HEADLESS, appUser: USERS.user1a },
    },
    {
      name: "firefox",
      testMatch: "**/*.e2e.ts",
      use: { ...devices["Desktop Firefox"], headless: HEADLESS, appUser: USERS.user2b },
    },
    {
      name: "webkit",
      testMatch: "**/*.e2e.ts",
      // WebKit bundled in Playwright uses an older TLS stack that rejects some
      // server cipher suites with "internal error". ignoreHTTPSErrors bypasses it.
      use: { ...devices["Desktop Safari"], headless: HEADLESS, appUser: USERS.user3c, ignoreHTTPSErrors: true },
    },
  ],
});
