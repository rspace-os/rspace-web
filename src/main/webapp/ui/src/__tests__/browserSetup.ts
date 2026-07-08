import { setupWorker } from "msw/browser";
import { afterEach, beforeAll } from "vitest";
import { cdp, server } from "vitest/browser";
import i18n from "@/modules/common/i18n";
import { galleryAppShellHandlers } from "./mocks/galleryMocks";
import { oauthTokenHandler } from "./mocks/oauthTokenMocks";
import { appShellHandlers } from "./mswAppShellHandlers";

/*
 * Unlike the jsdom unit-test config (setup.ts), which deliberately runs in
 * "cimode" so assertions target stable translation keys, browser-mode tests
 * assert against real rendered copy (visible text, accessible names, axe
 * checks) and never wrap components in an `I18nRoot`/`I18nextProvider`. They
 * rely on the shared i18n singleton, which lazily loads each namespace's JSON
 * on first use. Without this, a component's first render can race that
 * async load and paint raw translation keys (e.g.
 * "accessibilityTips.skipToContent.header") instead of real text. Preloading
 * every namespace here, before any test file's module graph finishes
 * evaluating, closes that race.
 */
await i18n.loadNamespaces([
  "about",
  "admin",
  "apps",
  "common",
  "gallery",
  "groups",
  "inventory",
  "public",
  "system",
  "workspace",
]);

/*
 * A single MSW worker shared by every browser-mode test. Tests register their
 * request handlers per-suite via `worker.use(...)`; handlers are reset after
 * each test so suites stay isolated (the MSW equivalent of Playwright's
 * per-test `router.route`).
 */
export const worker = setupWorker(...appShellHandlers(), oauthTokenHandler(), ...galleryAppShellHandlers());

type AxiosLikeRejection = {
  isAxiosError?: boolean;
  response?: {
    status?: number;
  };
  config?: {
    url?: string;
  };
};

function isAxios404(reason: unknown): reason is AxiosLikeRejection {
  return (
    typeof reason === "object" &&
    reason !== null &&
    (reason as AxiosLikeRejection).isAxiosError === true &&
    (reason as AxiosLikeRejection).response?.status === 404
  );
}

/**
 * Some components intentionally fire requests whose promises are not returned
 * to the test. If the component has already unmounted, those late 404s surface
 * as global unhandled rejections and fail Vitest even though the user-visible
 * behaviour under test has completed. Use this only for known fire-and-forget
 * URLs; all other unhandled rejections still fail the run.
 */
export function suppressFireAndForget404(matchers: ReadonlyArray<RegExp | string>): () => void {
  const listener = (event: PromiseRejectionEvent) => {
    const reason = event.reason;
    const url = isAxios404(reason) ? (reason.config?.url ?? "") : "";
    const matches = matchers.some((matcher) =>
      typeof matcher === "string" ? url.includes(matcher) : matcher.test(url),
    );
    if (matches) event.preventDefault();
  };

  window.addEventListener("unhandledrejection", listener);
  return () => {
    window.removeEventListener("unhandledrejection", listener);
  };
}

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
