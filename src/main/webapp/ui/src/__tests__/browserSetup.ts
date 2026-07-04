import { setupWorker } from "msw/browser";
import { afterEach, beforeAll } from "vitest";
import { cdp, server } from "vitest/browser";
import { galleryAppShellHandlers } from "./mocks/galleryMocks";
import { oauthTokenHandler } from "./mocks/oauthTokenMocks";
import { appShellHandlers } from "./mswAppShellHandlers";

/*
 * A single MSW worker shared by every browser-mode test. Tests register their
 * request handlers per-suite via `worker.use(...)`; handlers are reset after
 * each test so suites stay isolated (the MSW equivalent of Playwright's
 * per-test `router.route`).
 */
export const worker = setupWorker(...appShellHandlers(), oauthTokenHandler(), ...galleryAppShellHandlers());

/*
 * Vitest browser mode runs each test file in its own isolated module graph, so
 * this `worker` and its `beforeAll` hook are recreated per file. The service
 * worker itself, however, is registered once at the browser origin and persists
 * across files. We deliberately DO NOT stop the worker between files: stopping
 * the shared, origin-global worker in one file's teardown deactivates request
 * interception for subsequently-running files, opening a window where their
 * requests fall through to the real server and 404 (observed as cross-file
 * flakiness in the gallery suites). The worker is torn down for free when the
 * browser context closes at the end of the run, so an explicit stop is
 * unnecessary as well as harmful.
 *
 * Because the worker is already active, every file after the first calls
 * `worker.start()` redundantly; MSW logs that as a "no effect" warning. There
 * is no JS state shared across the per-file contexts to coordinate this and the
 * call is harmless, so we silence just that one message to keep the test output
 * clean without masking other MSW diagnostics.
 */
const REDUNDANT_MSW_CALL = /\[MSW\] Found a redundant "worker\.start\(\)" call/;
const originalWarn = console.warn.bind(console);
console.warn = (...args: ReadonlyArray<unknown>) => {
  if (typeof args[0] === "string" && REDUNDANT_MSW_CALL.test(args[0])) return;
  originalWarn(...args);
};

beforeAll(async () => {
  await worker.start({
    quiet: true,
    onUnhandledRequest: "bypass",
    serviceWorker: { url: "/mockServiceWorker.js" },
  });
});

afterEach(async () => {
  // Reset any CDP-emulated media features (prefers-contrast, forced-colors)
  // so they don't bleed into the next file. Files run serially in the same
  // browser instance (fileParallelism: false), so an unreset CDP override
  // leaks into subsequent files' theme computations (e.g. createAccentedTheme
  // reads window.matchMedia("(prefers-contrast: more)") at render time).
  // CDP is Chromium-only; this is a no-op on Firefox/WebKit.
  if (server.browser === "chromium") {
    await cdp().send("Emulation.setEmulatedMedia", { features: [] });
  }
  worker.resetHandlers();
  // The browser origin — and therefore localStorage/sessionStorage — is shared
  // across every spec file. Clear it after each test so state written by one
  // test cannot leak into a later test or file (the jsdom setup in setup.ts
  // does the same). Leaks like this become order-dependent flakiness once files
  // run in any nondeterministic order.
  localStorage.clear();
  sessionStorage.clear();
});
