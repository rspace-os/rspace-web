import { defineConfig, devices } from "@playwright/experimental-ct-react";
import getPort from "get-port";
import path from "path";
import fs from "fs";

/*
 * The Inventory rich-text editor lazy-loads a self-hosted TinyMCE 8 build at
 * runtime (see src/components/Inputs/StyledTinyMceEditor.tsx). Component tests
 * that render it therefore need (a) the `__TINYMCE_VERSION__` and
 * `__TINYMCE_BASE__` build-time globals defined and (b) the TinyMCE assets
 * served by the component-test Vite dev server.
 *
 * The assets are served via Vite's `publicDir`, pointed straight at the
 * installed TinyMCE package, so its files are available at the server root
 * ("/tinymce.min.js", "/models/...", "/skins/...", etc.) and `__TINYMCE_BASE__`
 * is "/". A custom serving plugin via `ctViteConfig.plugins` is deliberately
 * NOT used: Playwright only registers its own React framework plugin when no
 * user plugins are supplied (see @playwright/experimental-ct-core viteUtils),
 * so adding any plugin here would disable component mounting. There is no app
 * `public/` directory, so overriding `publicDir` is safe.
 */
const tinymceDir = path.dirname(require.resolve("tinymce/package.json"));
const tinymceVersion = (
  JSON.parse(fs.readFileSync(path.join(tinymceDir, "package.json"), "utf8")) as {
    version: string;
  }
).version;

/**
 * See https://playwright.dev/docs/test-configuration.
 */
async function getPlaywrightConfig() {
  return defineConfig({
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
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: [["html", { open: "never" }]],
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
      /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
      trace: "on-first-retry",

      /* Port to use for Playwright component endpoint. */
      ctPort: await getPort(),

      ctViteConfig: {
        // The Inventory editor reads these build-time globals to locate its
        // lazily-loaded TinyMCE assets. In the component-test build the assets
        // are served from the server root, so the base is "/".
        define: {
          __TINYMCE_VERSION__: JSON.stringify(tinymceVersion),
          __TINYMCE_BASE__: JSON.stringify("/"),
        },
        // Serve the installed TinyMCE package's files at "/" (so the editor's
        // /tinymce.min.js, /models/..., /skins/... requests resolve).
        publicDir: tinymceDir,
        resolve: {
          alias: {
            "@": path.resolve(__dirname, "src"),
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
}
export default getPlaywrightConfig();

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
