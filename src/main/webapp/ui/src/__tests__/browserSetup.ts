import { afterAll, afterEach, beforeAll } from "vitest";
import { cdp, server } from "vitest/browser";
import i18n from "@/modules/common/i18n";
import "@/modules/common/styles/index.css";
import { worker } from "./browserMocks";

// Loaded by setupFiles only. Specs import browserMocks to avoid registering these hooks twice.

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

// Stop this iframe's client before Vitest disposes it.
afterAll(() => worker.stop());
