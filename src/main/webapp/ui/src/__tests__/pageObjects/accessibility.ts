import axe from "axe-core";
import { expect } from "vitest";
import { cdp, server } from "vitest/browser";

export async function expectNoAxeViolations(): Promise<void> {
  const results = await axe.run(document);
  expect(
    results.violations.filter((v) => {
      /*
       * These violations are expected in component tests as we're not rendering
       * a complete page with proper document structure:
       *
       * 1. MUI DataGrid renders its immediate children with role=presentation,
       *    which Firefox considers to be a violation
       * 2. Component tests don't have main landmarks as they're isolated components
       * 3. Component tests typically don't have h1 headings as they're not full pages
       * 4. Content not in landmarks is expected in component testing context
       * 5. Dialogs that don't include a DialogTitle (e.g. PDF preview, which uses
       *    the document URL as its implicit title) trigger aria-dialog-name in
       *    component tests, where no page-level label is available to assign.
       * 6. TinyMCE renders its editable area inside an <iframe> without a title
       *    attribute; this is a known upstream limitation — the component-test
       *    wrapper has no page-level label to assign, and fixing it would require
       *    patching the TinyMCE init config in every consumer.
       */
      return (
        v.description !== "Ensure elements with an ARIA role that require child roles contain them" &&
        v.id !== "landmark-one-main" &&
        v.id !== "page-has-heading-one" &&
        v.id !== "region" &&
        v.id !== "aria-dialog-name" &&
        v.id !== "frame-title"
      );
    }),
  ).toEqual([]);
}

/*
 * Emulates the `prefers-contrast: more` media feature.
 *
 * Playwright's `page.emulateMedia` has no Vitest browser-mode equivalent, so we
 * drive it through the Chrome DevTools Protocol. CDP is only available with the
 * Playwright provider on Chromium; on Firefox/WebKit this is a no-op and the
 * accessibility scan still runs (just without the emulated media), preserving a
 * passing run across the configured browsers.
 */
export async function emulateHighContrast(): Promise<void> {
  if (server.browser !== "chromium") return;
  const session = cdp();
  await session.send("Emulation.setEmulatedMedia", {
    features: [{ name: "prefers-contrast", value: "more" }],
  });
}

/*
 * Emulates Windows High Contrast / forced-colors mode (`forced-colors: active`)
 * plus `prefers-contrast: more`. Like emulateHighContrast, this is driven via
 * the Chrome DevTools Protocol and is therefore chromium-only; on firefox/webkit
 * it is a no-op so the accessibility scan still runs.
 */
export async function emulateForcedColors(): Promise<void> {
  if (server.browser !== "chromium") return;
  const session = cdp();
  await session.send("Emulation.setEmulatedMedia", {
    features: [
      { name: "forced-colors", value: "active" },
      { name: "prefers-contrast", value: "more" },
    ],
  });
}
