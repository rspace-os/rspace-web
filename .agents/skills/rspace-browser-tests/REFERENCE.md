# RSpace Browser-Mode Tests: Reference

Templates, the full gotcha catalogue, and the Playwright-CT to Vitest Browser
Mode API translation table. The reference implementation is the stoichiometry
suite under `src/main/webapp/ui/src/tinyMCE/stoichiometry/__tests__/`.

## Artifact templates

### Spec (`Component.spec.tsx`)

```tsx
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";
import { SomeStory } from "./Component.story";
import { ComponentPage } from "./pageObjects/ComponentPage";

const pageObj = new ComponentPage();

beforeEach(() => {
  worker.use(
    oauthTokenHandler(),
    http.get("/api/v1/some/endpoint", () => HttpResponse.json({ ... })),
  );
});

afterEach(() => {
  cleanup();
});

describe("Component", () => {
  test("does the thing", async () => {
    render(<SomeStory />);
    await pageObj.waitForLoad();
    await pageObj.doAction();
    await expect.element(pageObj.result).toBeVisible();
  });
});
```

- `render`/`cleanup` come from `@testing-library/react` directly here (browser
  specs do not use the `@/__tests__/customQueries` wrapper, which is jsdom-only).
- Read a POSTed body in a handler with `await request.json()`.
- App-shell endpoints (whoami, nav data, analytics, livechat) are answered by
  the worker defaults already; do not re-mock them unless asserting failure.

### Page object (`pageObjects/ComponentPage.ts`)

```ts
import { type Locator, page, userEvent } from "vitest/browser";

export class ComponentPage {
  readonly table: Locator = page.getByRole("grid");

  get submit(): Locator {
    return page.getByRole("button", { name: /submit/i });
  }

  async submitForm(): Promise<void> {
    await this.submit.click();
  }
}
```

- Getters return retriable `Locator`s (no `await`, no captured DOM nodes).
- Action methods are `async` and `await` the interaction.
- No assertions; those belong in the spec.

### CSS workaround helper (only for third-party libraries with no a11y handle)

Vitest exposes no public CSS locator. `locator()` is `protected` on the type but
present at runtime, so call the provider's `css=` engine via a cast. Always
comment WHY a semantic locator could not be used.

```ts
// Under <body>:
function css(selector: string): Locator {
  return (page.elementLocator(document.body) as unknown as { locator(s: string): Locator })
    .locator(`css=${selector}`);
}

// Scoped within a root locator (e.g. a DataGrid row):
function cssWithin(root: Locator, selector: string): Locator {
  return (root as unknown as { locator(s: string): Locator }).locator(`css=${selector}`);
}
```

Do NOT use `elementLocator(querySelector(...))`: it pins a captured node that
goes stale when the grid re-renders, causing timeouts. The `css=` cast stays
retriable.

Known legitimate uses: MUI DataGrid cell-by-column (`[data-field="..."]` — column
identity is not in the a11y tree), PhotoSwipe image/caption, react-pdf
document/page divs, the TinyMCE contenteditable iframe body.

## Gotcha catalogue

- **MSW worker lifecycle.** One origin-global service worker, started once in
  `browserSetup.ts` and never stopped. Files run serially (`fileParallelism:
  false`) so `worker.use()`/`resetHandlers()` never race. Adding `worker.stop()`
  in a teardown deactivates interception for later files and 404s them
  (cross-file flakiness). Files after the first call `worker.start()` redundantly;
  that warning is silenced in `browserSetup.ts`.
- **`suppressFireAndForget404([...])`.** Components that fire un-awaited requests
  (folder listings, thumbnails) can 404 after teardown and surface as an
  `unhandledrejection` that fails the run even though every assertion passed.
  Opt in from a suite's `beforeEach`, call the returned cleanup in `afterEach`;
  it swallows only an AxiosError 404 whose URL matches one of the matchers.
- **`getByRole` and 0x0 elements.** The role engine treats a 0x0 element as
  hidden. The MUI X DataGrid pagination select renders at 0x0 in the footer, so
  query it with `getByRole("combobox", { name: /rows per page/i, includeHidden:
  true })`.
- **MUI `SvgIcon` accessible name.** A bare `aria-label` makes MUI mark the svg
  `aria-hidden`, so it has no a11y name. Use `titleAccess="..."` (renders
  `<svg role="img"><title>...</title></svg>`) and query `getByRole("img", {
  name })`. This is a real component fix, not just a test change.
- **MUI `Select` inside `FormField`.** Its `aria-labelledby` points at its own id,
  so role/label queries miss it. Add `SelectDisplayProps={{ "data-testid":
  "MySelect" }}` and use `getByTestId`.
- **MUI DataGrid ARIA.** Data rows contain `gridcell`s, the header row contains
  `columnheader`s; select data rows with
  `getByRole("row").filter({ has: page.getByRole("gridcell") })`. A cell's
  accessible name is its value, not its column; address a column by `data-field`
  via `cssWithin`. The "Update Inventory Stock" dialog is a plain `<table>`
  (`role="cell"`), so locate metric cells by header-index position.
- **Modifier clicks.** Use `userEvent.click(el, { modifiers: ["Shift"] })` /
  `["ControlOrMeta"]`. The keyboard-hold form
  (`keyboard("{Shift>}{ArrowRight}{/Shift}")`) is fine for keyboard nav but the
  modifier-CLICK keyboard-hold form drops the flag (vitest #7007).
- **Post-action network assertions.** Convert to `expect.poll(...)` to avoid
  races where the assertion runs before the PUT/DELETE round-trips.
- **CDP media emulation is chromium-only.** `emulateHighContrast()` /
  `emulateForcedColors()` drive `Emulation.setEmulatedMedia` via CDP; they are
  no-ops on firefox/webkit (the scan still runs). `browserSetup.ts` resets
  emulated media after each test so it does not bleed across files.
- **Storage isolation.** `browserSetup.ts` clears `localStorage`/`sessionStorage`
  after each test; the origin is shared across files, so leaks become
  order-dependent flakes.
- **Accessibility scans.** `expectNoAxeViolations()` filters violations that are
  inherent to isolated component rendering (no main landmark, no h1, DataGrid
  presentation children, dialog-name for title-less dialogs, TinyMCE frame
  title). Match the originals before adding new exclusions.
- **CSV / download capture.** No Playwright download interception exists.
  Override `URL.createObjectURL`/`revokeObjectURL` and suppress the anchor
  `click`, then read `blob.text()` (see `StoichiometryTablePage.exportToCsv`).
  Wait for all rows to render first or a slow engine exports a partial CSV.
- **TinyMCE.** Assets are served at `/tinymce/` by the `tinymceAssetsPlugin`
  middleware in `vitest.browser.config.ts`. The iframe `title` is set only on
  Firefox and the body `aria-label` only on non-Firefox, so reach the editable
  body via `frameLocator` + a `css=` cast on `.tox-edit-area__iframe` and
  `body[contenteditable="true"]`. `pressSequentially` does not exist on Vitest
  locators; use `userEvent.type`.
- **Firefox CI skips.** A handful of heavy TinyMCE/DataGrid/gallery suites time
  out on CI's slow Firefox runner; they are skipped only on the Firefox CI leg
  (see `firefoxCiSkippedFiles` in the config). pdf.js never resolves its worker
  in headless Firefox, so PDF tests `skipIf(firefox)`.
- **`optimizeDeps`.** `@mui/material/utils` and `@tinymce/tinymce-react` are
  pre-bundled to stop a mid-run optimizer reload that duplicates React/emotion
  instances. The browser config uses its own `cacheDir`
  (`node_modules/.vite-browser`) so it can run alongside the jsdom suite.

## API translation (Playwright-CT to Vitest Browser Mode)

| Playwright-CT | Vitest Browser Mode |
| --- | --- |
| `mount(<Story/>)` | `render(<Story/>)` + `cleanup()` |
| `router.route()` / `page.route()` | MSW `http`/`HttpResponse` via `worker.use()`; `postData()` to `await request.json()` |
| `AxeBuilder().analyze()` | `axe.run(document)` / `axe.run(el)` (use `expectNoAxeViolations()`) |
| `page.emulateMedia({ forcedColors })` | `emulateForcedColors()` / `emulateHighContrast()` (chromium-only) |
| `page.boundingBox()` | `locator.element().getBoundingClientRect()` in `expect.poll` |
| `page.waitForEvent('download')` | override `URL.createObjectURL` + suppress anchor click, read `blob.text()` |
| `page.waitForFunction` | `vi.waitFor(() => { if (!cond) throw })` |
| `page.waitForTimeout` | `expect.poll` / `vi.waitFor` (no fixed sleeps) |
| `page.evaluate(fn)` | inline DOM access; `locator.element()` |
| `page.addInitScript` (matchMedia stub) | patch `window.matchMedia` before `render`, restore in `afterEach` |
| `Shift+Arrow` keyboard | `userEvent.keyboard("{Shift>}{ArrowRight}{/Shift}")` |
| modifier click | `userEvent.click(el, { modifiers: ["Shift"] })` (NOT keyboard-hold) |
| `page.frameLocator(iframe)` | `page.frameLocator(css(".iframe"))` + `css('body[contenteditable="true"]')` |
| new-tab assertion | assert anchor `target`/`href` and that clicking does not toggle selection |
| `context.grantPermissions(['clipboard-*'])` | stub `navigator.clipboard.writeText`, assert captured text (chromium) |
| `toHaveScreenshot()` | none present; not used in this repo |
