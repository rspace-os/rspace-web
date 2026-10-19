import { defineConfig, devices, type ReporterDescription } from "@playwright/test";
import { storageStatePath } from "./src/__tests__/e2e/authState";
import { env } from "./src/__tests__/e2e/env";
import type { E2EOptions } from "./src/__tests__/e2e/fixtures/ui";
import { selectBrowserProjects } from "./src/__tests__/e2e/projects";
import { tags } from "./src/__tests__/e2e/tags";
import { USERS } from "./src/__tests__/e2e/users";
import { MOBILE_DEVICE } from "./src/__tests__/e2e/viewports";

const E2E_BROWSER = env.browser;
const browserProjects = selectBrowserProjects(E2E_BROWSER, { cloud: env.cloud });
const SETUP_BROWSER = browserProjects[0]?.browserName ?? "chromium";
const desktopDevices = {
  chromium: devices["Desktop Chrome"],
  firefox: devices["Desktop Firefox"],
  webkit: devices["Desktop Safari"],
};

const MOCK_PORT = env.mockPort;
const MOCK_PROBE_URL = `http://localhost:${MOCK_PORT}/e2e-health`;
const HEADLESS = env.headless;

const CLOUD_SPECS = "**/specs/cloud/**/*.e2e.ts";

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
    // CI does not need video artifacts, so avoid recording them.
    video: env.ci ? "off" : PW_LOG === "trace" ? "on" : "retain-on-failure",
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

    ...browserProjects.map(({ name, browserName, appUser }) => ({
      name,
      testMatch: env.cloud ? CLOUD_SPECS : "**/*.e2e.ts",
      testIgnore: env.cloud ? undefined : CLOUD_SPECS,
      dependencies: ["setup"],
      grep: name === "mobile" ? new RegExp(tags.MOBILE) : undefined,
      use: {
        ...(name === "mobile" ? MOBILE_DEVICE : desktopDevices[browserName]),
        browserName,
        headless: HEADLESS,
        appUser,
        storageState: storageStatePath(appUser.username),
        ignoreHTTPSErrors: browserName === "webkit" || name === "mobile",
      },
    })),
  ].filter(({ name }) => {
    if (name === "setup") return E2E_BROWSER !== "api";
    if (name === "api") return (!E2E_BROWSER && !env.cloud) || E2E_BROWSER === "api";
    return true;
  }),
});
