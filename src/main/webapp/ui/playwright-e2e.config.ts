import { defineConfig, devices, type ReporterDescription } from "@playwright/test";
import { storageStatePath } from "./src/__tests__/e2e/authState";
import { env } from "./src/__tests__/e2e/env";
import type { E2EOptions } from "./src/__tests__/e2e/fixtures/ui";
import { tags } from "./src/__tests__/e2e/tags";
import { USERS } from "./src/__tests__/e2e/users";
import { MOBILE_DEVICE } from "./src/__tests__/e2e/viewports";

const E2E_BROWSER = env.browser;
const SETUP_BROWSER: "chromium" | "firefox" | "webkit" =
  E2E_BROWSER === "firefox" || E2E_BROWSER === "webkit" ? E2E_BROWSER : "chromium";

const MOCK_PORT = env.mockPort;
const MOCK_PROBE_URL = `http://localhost:${MOCK_PORT}/e2e-health`;
const HEADLESS = env.headless;

const PW_LOG = env.playwrightLog;
if (PW_LOG === "trace") {
  env.enablePlaywrightApiDebug();
}

function getReporterConfig(): ReporterDescription[] {
  if (env.ci) {
    return [["list"], ["junit", { outputFile: "e2e-junit.xml" }], ["html", { open: "never" }]];
  }
  if (PW_LOG !== "off") {
    return [["list"], ["html", { open: "on-failure" }], ["line"]];
  }
  return [["list"], ["html", { open: "on-failure" }]];
}

export default defineConfig<E2EOptions>({
  testDir: "./src",

  webServer:
    env.integrationMode === "mock"
      ? {
          command: `node src/__tests__/e2e/mockServer.ts ${MOCK_PORT}`,
          url: MOCK_PROBE_URL,
          reuseExistingServer: !env.ci,
          timeout: 15_000,
        }
      : undefined,
  forbidOnly: env.ci,
  retries: env.ci ? 2 : 0,
  // Keep a single worker: tests share per-project seed users and global config,
  // so they must not run concurrently within one instance. `fullyParallel` only
  // changes CI --shard granularity to per-test (evenly balanced shards) instead
  // of per-file; execution stays serial. `.serial` blocks remain grouped.
  workers: 1,
  fullyParallel: true,
  reporter: getReporterConfig(),

  timeout: 60_000,

  expect: { timeout: 15_000 },
  use: {
    baseURL: env.baseURL,

    trace: PW_LOG === "trace" ? "on" : "on-first-retry",
    screenshot: PW_LOG === "off" ? "only-on-failure" : "on",
    video: PW_LOG === "trace" ? "on" : "retain-on-failure",
  },
  projects: [
    {
      name: "setup",
      testMatch: "**/auth.setup.ts",
      use: {
        browserName: SETUP_BROWSER,
        headless: HEADLESS,
        ignoreHTTPSErrors: SETUP_BROWSER === "webkit",
      },
    },

    // user2b is initialized; unused seed users lack a workspace root until their first UI login.
    {
      name: "api",
      testMatch: "**/*.api.spec.ts",
      use: { appUser: USERS.user2b },
    },

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

      use: {
        ...devices["Desktop Safari"],
        headless: HEADLESS,
        appUser: USERS.user4d,
        storageState: storageStatePath(USERS.user4d.username),
        ignoreHTTPSErrors: true,
      },
    },

    {
      name: "mobile",
      testMatch: "**/*.e2e.ts",
      dependencies: ["setup"],
      grep: new RegExp(tags.MOBILE),
      use: {
        ...MOBILE_DEVICE,
        headless: HEADLESS,
        appUser: USERS.user7g,
        storageState: storageStatePath(USERS.user7g.username),
        ignoreHTTPSErrors: true,
      },
    },
    // Browser projects require setup; API-only runs do not.
  ].filter(({ name }) => !E2E_BROWSER || name === E2E_BROWSER || (name === "setup" && E2E_BROWSER !== "api")),
});
