import { defineConfig, devices } from "@playwright/experimental-ct-react";
import path from "path";

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: "./src",
  testMatch: "**/*.spec.tsx",
  tsconfig: "./tsconfig.json",
  snapshotDir: "./__snapshots__",
  /* Maximum time one test can run for. */
  timeout: 10 * 1000,
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: "html",
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: "on-first-retry",

    /* Port to use for Playwright component endpoint. */
    ctPort: 3100,

    ctViteConfig: {
      resolve: {
        alias: {
          Styles: "src/util/styles.ts",
          "@": path.resolve(__dirname, "src"),
          "@mui/utils/deepmerge/": path.resolve(
            __dirname,
            "node_modules/@mui/utils/deepmerge/index.js"
          ),
        },
        extensions: [".ts", ".tsx", ".js"],
      },
    },
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "firefox",
      use: { ...devices["Desktop Firefox"] },
    },
    {
      name: "webkit",
      use: { ...devices["Desktop Safari"] },
    },
  ],
});

/*
 * ====  USEFUL COMMANDS  =====================================================
 *
 *  To run all the tests:
 *    npm run test-ct
 *
 *  To open the Playwright Test UI, (for dev tools in the live browser) run:
 *    npm run test-ct --ui
 *
 *  After switching git branches, you may need to clear the Playwright cache:
 *    rm -r playwright/.cache
 *
 * ============================================================================
 */
