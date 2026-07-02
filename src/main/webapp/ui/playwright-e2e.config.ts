import { resolve } from "node:path";
import { defineConfig, devices, type ReporterDescription } from "@playwright/test";
import { storageStatePath } from "./src/__tests__/e2e/authState";
import type { E2EOptions } from "./src/__tests__/e2e/fixtures";
import { INTEGRATION_MODE } from "./src/__tests__/e2e/integrationMode";
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

// When set, run only that browser project (skips api and other browsers).
// CI installs one browser and sets this; local runs omit it to get all projects.
const E2E_BROWSER = process.env.E2E_BROWSER;

// Mock server — only started when E2E_INTEGRATION_MODE=mock (the default).
// The Java backend's integration base URLs (pubchem.base.url, etc.) are
// overridden at JVM startup to http://localhost: MOCK_PORT, so Spring
// RestTemplate calls land on this local server instead of the real APIs.
const MOCK_PORT = process.env.E2E_MOCK_PORT ?? "9099";
const MOCK_PROBE_URL = `http://localhost:${MOCK_PORT}/e2e-health`;
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
  // In mock mode: start the local HTTP server that all integration tests point
  // the Java backend at. In real mode: no server — backend hits live APIs.
  webServer:
    INTEGRATION_MODE === "mock"
      ? {
          command: "node src/__tests__/e2e/mockServer.mjs",
          url: MOCK_PROBE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 15_000,
        }
      : undefined,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 4 : undefined,
  reporter: getReporterConfig(),
  // Raise timeouts for the remote server (pangolin8086 can be slow).
  // Default 30s is too short for page.goto + login round-trips on a cold server.
  timeout: 60_000,
  // Playwright default expect.timeout is 5s — not enough for server round-trips.
  expect: { timeout: 15_000 },
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
    // Auth setup: authenticates each browser project's seed user (plus
    // sysadmin) once and saves storageState under playwright/.auth/. Browser
    // projects below depend on it and start every test already authenticated
    // — see auth.setup.ts for why (avoids racing Hibernate's optimistic lock
    // on concurrent logins as the same seed user).
    {
      name: "setup",
      testMatch: "**/auth.setup.ts",
      use: { headless: HEADLESS },
    },
    // API tests: Node HTTP only, no browser. Each test gets its own
    // APIRequestContext via the `apiContext` fixture. user2b is dedicated to
    // API tests to avoid workspace state collisions with browser projects.
    // Uses the apiKey header directly, so it doesn't need `setup` — user2b is
    // chosen (over an unused-but-uninitialized seed user) because its
    // workspace root folder already exists; an account that has never
    // completed a UI login has no root folder yet (lazily created on first
    // login) and 500s on first write, e.g. POST /api/v1/documents.
    {
      name: "api",
      testMatch: "**/*.api.spec.ts",
      use: { appUser: USERS.user2b },
    },
    // Each browser uses a distinct PI+USER seed account to avoid shared-state
    // collisions when browser shards run in parallel against the same backend.
    {
      name: "chromium",
      testMatch: "**/*.e2e.ts",
      dependencies: ["setup"],
      use: {
        ...devices["Desktop Chrome"],
        headless: HEADLESS,
        appUser: USERS.user1a,
        storageState: storageStatePath(USERS.user1a.username),
      },
    },
    {
      name: "firefox",
      testMatch: "**/*.e2e.ts",
      dependencies: ["setup"],
      use: {
        ...devices["Desktop Firefox"],
        headless: HEADLESS,
        appUser: USERS.user3c,
        storageState: storageStatePath(USERS.user3c.username),
      },
    },
    {
      name: "webkit",
      testMatch: "**/*.e2e.ts",
      dependencies: ["setup"],
      // WebKit bundled in Playwright uses an older TLS stack that rejects some
      // server cipher suites with "internal error". ignoreHTTPSErrors bypasses it.
      use: {
        ...devices["Desktop Safari"],
        headless: HEADLESS,
        appUser: USERS.user4d,
        storageState: storageStatePath(USERS.user4d.username),
        ignoreHTTPSErrors: true,
      },
    },
    // `setup` has no tests of its own to filter by E2E_BROWSER, so keep it
    // whenever a browser project survives the filter below (they depend on
    // it); only E2E_BROWSER=api drops it.
  ].filter(({ name }) => !E2E_BROWSER || name === E2E_BROWSER || (name === "setup" && E2E_BROWSER !== "api")),
});
