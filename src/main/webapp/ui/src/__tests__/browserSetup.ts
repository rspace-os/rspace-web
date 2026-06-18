import { setupWorker } from "msw/browser";
import { afterEach, beforeAll } from "vitest";
import { locators } from "vitest/browser";
import { appShellHandlers } from "./mswAppShellHandlers";

/*
 * Adds a `getByCSS` locator to the Vitest browser `page`/locator selectors.
 * The Playwright-CT tests relied on raw CSS sub-locators (e.g. the MUI
 * DataGrid's `[role="gridcell"][data-field="..."]`), which Vitest's locator
 * API does not expose out of the box. The Playwright provider understands the
 * `css=` selector engine, so we surface it as a first-class locator.
 */
locators.extend({
  getByCSS(css: string) {
    return `css=${css}`;
  },
});

declare module "vitest/browser" {
  interface LocatorSelectors {
    getByCSS(css: string): import("vitest/browser").Locator;
  }
}

/*
 * A single MSW worker shared by every browser-mode test. Tests register their
 * request handlers per-suite via `worker.use(...)`; handlers are reset after
 * each test so suites stay isolated (the MSW equivalent of Playwright's
 * per-test `router.route`).
 */
export const worker = setupWorker(...appShellHandlers());

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

afterEach(() => {
  worker.resetHandlers();
  // The browser origin — and therefore localStorage/sessionStorage — is shared
  // across every spec file. Clear it after each test so state written by one
  // test cannot leak into a later test or file (the jsdom setup in setup.ts
  // does the same). Leaks like this become order-dependent flakiness once files
  // run in any nondeterministic order.
  localStorage.clear();
  sessionStorage.clear();
});

/*
 * Opt-in suppressor for benign "fire-and-forget" 404s.
 *
 * Some components fire requests they never await or error-handle (a folder
 * listing, a thumbnail). When such a request is still in flight as a test ends,
 * the `resetHandlers()` above removes its MSW handler before it resolves, so it
 * falls through to the real (non-existent) server and 404s after teardown.
 * Nothing awaited it, so it surfaces as an `unhandledrejection`, which Vitest
 * treats as a run failure even though every assertion passed. This only bites in
 * CI, where slower runners let more requests outlive their test.
 *
 * A suite that knowingly triggers such requests opts in by calling this in a
 * `beforeEach` and invoking the returned cleanup in its `afterEach`. It swallows
 * ONLY an AxiosError 404 whose request URL matches one of `urlMatchers`; every
 * other unhandled rejection (and any 404 a test genuinely cares about, which is
 * caught rather than unhandled) still fails the run. Scoping by URL keeps the
 * suppression narrow and explicit rather than global.
 */
export function suppressFireAndForget404(urlMatchers: ReadonlyArray<string | RegExp>): () => void {
  const matchesUrl = (url: unknown): boolean =>
    typeof url === "string" && urlMatchers.some((m) => (typeof m === "string" ? url.includes(m) : m.test(url)));
  const handler = (event: PromiseRejectionEvent): void => {
    const reason = event.reason as
      | {
          name?: unknown;
          message?: unknown;
          response?: { status?: unknown };
          config?: { url?: unknown };
        }
      | null
      | undefined;
    if (reason?.name !== "AxiosError") return;
    const is404 =
      reason.response?.status === 404 ||
      (typeof reason.message === "string" && reason.message.includes("status code 404"));
    if (is404 && matchesUrl(reason.config?.url)) event.preventDefault();
  };
  window.addEventListener("unhandledrejection", handler);
  return () => window.removeEventListener("unhandledrejection", handler);
}
