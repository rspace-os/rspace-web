import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { playwright } from "@vitest/browser-playwright";
import type { Alias, Plugin } from "vite";
import { nodePolyfills } from "vite-plugin-node-polyfills";
import { configDefaults, defineConfig } from "vitest/config";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const resolveFromRoot = (relativePath: string) => path.resolve(__dirname, relativePath);

/*
 * Resolve the installed TinyMCE package directory so we can serve its assets
 * as static files during browser-mode tests. The Inventory rich-text editor
 * lazy-loads TinyMCE from `__TINYMCE_BASE__` at runtime; in browser tests that
 * base is "/tinymce/" (defined in the `define` block below), so this middleware
 * intercepts requests to "/tinymce/*" and streams files from the package.
 *
 * This mirrors the `rspace:tinymce-assets` plugin in vite.config.ts, which
 * does the same for the dev server and production build. We cannot reuse that
 * plugin directly because vitest.browser.config.ts is a separate config that
 * does not import from vite.config.ts.
 */
const tinymceDir = path.dirname(createRequire(import.meta.url).resolve("tinymce/package.json"));
const tinymceVersion = (
  JSON.parse(fs.readFileSync(path.join(tinymceDir, "package.json"), "utf8")) as { version: string }
).version;

const TINYMCE_MIME: Record<string, string> = {
  ".js": "text/javascript",
  ".mjs": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".json": "application/json",
  ".html": "text/html",
};

function tinymceAssetsPlugin(): Plugin {
  const prefix = "/tinymce/";
  return {
    name: "rspace:browser-test-tinymce-assets",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathname = (req.url ?? "").split("?")[0];
        if (!pathname.startsWith(prefix)) return next();
        const rel = decodeURIComponent(pathname.slice(prefix.length));
        const filePath = path.normalize(path.join(tinymceDir, rel));
        if (!filePath.startsWith(tinymceDir) || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
          return next();
        }
        res.setHeader("Content-Type", TINYMCE_MIME[path.extname(filePath)] ?? "application/octet-stream");
        res.setHeader("Cache-Control", "no-cache");
        fs.createReadStream(filePath).pipe(res);
      });
    },
  };
}

/*
 * Browser-mode test config for the component tests (`*.spec.tsx`). These are
 * the Playwright-CT successors; future Playwright e2e tests use the `*.e2e.*`
 * suffix so the two runners never collide.
 *
 * Unlike the jsdom unit-test config in vite.config.ts, this one deliberately
 * does NOT stub the MUI DataGrid, CSS, images or SVGs: the tests run in a real
 * browser via Playwright, so the real grid and real assets are exercised.
 * Network is mocked with MSW (see src/__tests__/browserSetup.ts).
 */
const browserAliases: Alias[] = [{ find: /^@\//, replacement: `${resolveFromRoot("src")}/` }];

type PlaywrightBrowser = "chromium" | "firefox" | "webkit";

// Run the full set of Playwright-supported browsers by default so the suite
// catches engine-specific regressions (e.g. Firefox-only axe violations).
// Override with VITEST_BROWSERS=chromium for a faster local inner loop.
const browsers = (process.env.VITEST_BROWSERS ?? "chromium,firefox,webkit")
  .split(",")
  .map((name) => name.trim())
  .filter(Boolean) as PlaywrightBrowser[];

// A handful of heavy suites (TinyMCE/DataGrid/gallery) reliably time out
// wholesale on CI's slow Firefox runner — raising the timeout/retries only
// turned the job into a multi-hour run without making it pass. They pass on
// Chromium/WebKit and on a local Firefox, so we skip just these files on
// Firefox AND only in CI; every other Firefox file still runs. CI runs one
// browser per job, so `VITEST_BROWSERS=firefox` identifies the Firefox leg.
const isCI = Boolean(process.env.CI);
const isFirefoxOnlyRun = browsers.length === 1 && browsers[0] === "firefox";
const firefoxCiSkippedFiles =
  isCI && isFirefoxOnlyRun
    ? [
        "**/tinyMCE/stoichiometry/__tests__/StoichiometryTable.spec.tsx",
        "**/tinyMCE/stoichiometry/__tests__/StoichiometryDialog.spec.tsx",
        "**/eln/gallery/components/CallableImagePreview.spec.tsx",
        "**/tinyMCE/pubchem/ImportDialog.spec.tsx",
        "**/Inventory/components/FieldmarkImportDialog.spec.tsx",
        "**/Inventory/Identifiers/IGSN/IgsnTable.spec.tsx",
      ]
    : [];

export default defineConfig({
  plugins: [
    react(),
    nodePolyfills({
      globals: { process: true, Buffer: true, global: true },
      protocolImports: false,
    }),
    tinymceAssetsPlugin(),
  ],
  define: {
    global: "globalThis",
    // Real TinyMCE version for cache-busting; assets are served by the
    // tinymceAssetsPlugin middleware at "/tinymce/" so TinyMCE can initialize.
    __TINYMCE_VERSION__: JSON.stringify(tinymceVersion),
    __TINYMCE_BASE__: JSON.stringify("/tinymce/"),
  },
  resolve: {
    tsconfigPaths: true,
    alias: browserAliases,
  },
  // Serve the MSW service worker from a test-only directory at the server root
  // (so worker.start({ url: "/mockServiceWorker.js" }) resolves) without
  // creating an app-level public/ dir that vite build would copy into the WAR.
  publicDir: resolveFromRoot("src/__tests__/msw"),
  // Use a dedicated optimizer/cache dir distinct from the jsdom config's default
  // (`node_modules/.vite`). Otherwise running `pnpm run test` (jsdom) and
  // `pnpm run test-browser` concurrently would have both Vite servers writing
  // the same dep-cache, corrupting it and producing "Failed to fetch
  // dynamically imported module" flakiness. A separate dir lets the two suites
  // run in parallel safely.
  cacheDir: resolveFromRoot("node_modules/.vite-browser"),
  // Pre-bundle the node-polyfill shims up front. They are otherwise discovered
  // lazily (the first time app code touches `process`/`Buffer`/`global`),
  // which makes Vite re-optimize and reload mid-run — Vitest warns this can
  // make browser tests flaky.
  optimizeDeps: {
    include: [
      "vite-plugin-node-polyfills/shims/process",
      "vite-plugin-node-polyfills/shims/buffer",
      "vite-plugin-node-polyfills/shims/global",
      // Pulled in by Inventory/Identifiers/IGSN/IgsnTable at runtime; pre-bundling
      // prevents a mid-run optimizer reload that causes duplicate React/emotion instances.
      "@mui/material/utils",
      // TinyMCE React wrapper: discovered lazily the first time a component
      // mounts a TinyMCE editor, causing a mid-run optimizer reload that
      // makes the Vitest runner warn and can cause flakiness.
      "@tinymce/tinymce-react",
    ],
  },
  test: {
    include: ["**/?*.spec.{ts,tsx}"],
    // Exclude the heavy suites that time out on CI Firefox (see above); empty on
    // every other run so they execute normally. `configDefaults.exclude` keeps
    // node_modules/dist/etc. excluded since setting `exclude` overrides it.
    exclude: [...configDefaults.exclude, ...firefoxCiSkippedFiles],
    setupFiles: ["./src/__tests__/browserSetup.ts"],
    testTimeout: 20000,
    // Real-browser component tests carry inherent timing flakiness, especially
    // on Firefox/WebKit under full-suite load. The Playwright-CT config this
    // replaces used `retries: 2`; match that so an occasional first-attempt miss
    // is retried rather than failing the run. A test that fails every attempt is
    // a real failure.
    retry: 2,
    // Run spec files SERIALLY within a browser instance. All files share one
    // origin-level MSW service worker (browserSetup starts it once and never
    // stops it); if two files ran concurrently their `worker.use()` /
    // `resetHandlers()` calls would race on that shared worker, intermittently
    // dropping each other's request handlers. Serial files keep the worker
    // owned by exactly one file at a time. (The per-browser CI matrix already
    // gives cross-engine parallelism at the job level.)
    fileParallelism: false,
    // In CI, additionally emit a JUnit report so the per-browser matrix job can
    // publish results (mirrors the jsdom `vitest-tests` job). Each CI job sets
    // VITEST_BROWSERS to a single engine, so `browsers` has one entry and the
    // filename is unique per matrix leg.
    reporters: process.env.CI ? ["default", "junit"] : ["default"],
    outputFile: process.env.CI ? { junit: `browser-junit-${browsers.join("-")}.xml` } : undefined,
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      screenshotFailures: false,
      instances: browsers.map((browser) => ({ browser })),
    },
  },
});
