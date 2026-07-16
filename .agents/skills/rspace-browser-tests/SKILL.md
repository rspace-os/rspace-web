---
name: rspace-browser-tests
description: Write, run, and debug RSpace frontend component tests in Vitest Browser Mode (real chromium/firefox/webkit via the Playwright provider + MSW + a Page Object Model). Use when adding or fixing a `*.spec.tsx` browser test, migrating a component to the browser-test pattern, debugging a cross-engine failure or flake, or wiring MSW handlers for a component under test. Do NOT use for jsdom unit tests (`*.test.tsx`, run with `pnpm run test`) or for backend Java tests.
---

# RSpace Browser-Mode Component Tests

The frontend (`src/main/webapp/ui`) runs its component tests in **Vitest Browser
Mode**: real browsers (chromium, firefox, webkit) driven through the
`@vitest/browser-playwright` provider, with network mocked by **MSW** and the UI
exercised through a **Page Object Model**. This replaced the old Playwright
Component Testing stack; there is no `playwright-ct` anymore. Future end-to-end
Playwright tests use the `*.e2e.*` suffix so the two never collide.

`REFERENCE.md` holds the full artifact templates, the gotcha catalogue, and the
Playwright-CT to Vitest API translation table. Read it before writing a spec.

## File conventions

- Browser specs are `*.spec.tsx`. The runner is `vitest.browser.config.ts`
  (`include: ["**/?*.spec.{ts,tsx}"]`). jsdom unit tests are `*.test.tsx` and
  run under the separate `vite.config.ts`. Keep the suffixes straight: a
  `*.spec.tsx` will NOT run under `pnpm run test`, and a `*.test.tsx` will NOT
  run under `pnpm run test-browser`.
- Co-locate with the component, or under a sibling `__tests__/`. Each spec has
  up to four artifacts (see "The pattern" below).
- Every component already has a `*.story.tsx`; reuse it, do not re-wrap providers.

## Running

```bash
pnpm run test-browser                              # all 3 engines (from repo root)
VITEST_BROWSERS=chromium pnpm run test-browser     # fast inner loop, one engine
pnpm run test-browser src/components/SkipToContentMenu.spec.tsx      # single file
pnpm run test-browser:watch                        # watch mode
```

- Default is chromium,firefox,webkit so the suite catches engine-specific
  regressions. Use `VITEST_BROWSERS=chromium` while iterating.
- First run needs the Playwright browser binaries: `pnpm exec playwright install
  chromium firefox webkit` (CI does `playwright install --with-deps <browser>`).
- CI runs **one engine per matrix job** via `VITEST_BROWSERS`, and emits a
  per-engine JUnit file. The `browser-tests` job in
  `.github/workflows/lint-and-test.yml` is the wiring.
- `retry: 2` and `fileParallelism: false` are set deliberately (see gotchas).

## The pattern (four artifacts)

| Artifact | Holds | Rule |
| --- | --- | --- |
| `*.spec.tsx` | the test | `render(<Story/>)`, register per-suite MSW via `worker.use(...)` in `beforeEach`, `cleanup()` in `afterEach`; assertions live here (`expect.element`, `expect.poll`) |
| `pageObjects/ComponentPage.ts` | locator getters (no `await`, retriable) + async action methods | **no assertions** |
| `mocks/componentMocks.ts` | MSW handler + fixture factories | re-export shared ones, do not duplicate |
| `*.story.tsx` | the component wrapped in providers | reuse the existing one |

Locators are semantic first (`getByRole`/`getByLabelText`/`getByText`). CSS is a
last resort, used only to work around third-party libraries that expose no
accessible handle, via a local `css=` cast helper (templates in `REFERENCE.md`).

## Shared infrastructure (reuse, do not re-create)

- `src/__tests__/browserSetup.ts` — the shared MSW `worker`, its start/reset
  lifecycle, the CDP media reset, and `suppressFireAndForget404(...)`.
- `src/__tests__/mswAppShellHandlers.ts` — default app-shell handlers (whoami,
  nav data, analytics, livechat) registered as worker defaults; they survive
  `resetHandlers()`.
- `src/__tests__/mocks/inventoryMocks.ts` (`oauthTokenHandler`, `OAUTH_TOKEN`)
  and `src/__tests__/mocks/galleryMocks.ts` (`galleryAppShellHandlers`) —
  opt-in per suite via `worker.use(...)`.
- `src/__tests__/pageObjects/accessibility.ts` — `expectNoAxeViolations()`,
  `emulateHighContrast()`, `emulateForcedColors()` (the last two are
  chromium-only no-ops elsewhere).
- `src/__tests__/pageObjects/viewport.ts` — `isFullyInViewport`,
  `moveToastStackIntoViewport`, `clickWhenInViewport`.

## Top gotchas (full list in REFERENCE.md)

- **MSW worker is origin-global and never stopped between files** — that is why
  `fileParallelism: false`. Do not add a `worker.stop()`; it deactivates
  interception for later files and 404s them.
- **CSS locators only as a third-party workaround.** Vitest has no public CSS
  locator; use the `.locator("css=...")` cast helper and comment WHY semantic
  failed (DataGrid cell column identity, PhotoSwipe, react-pdf, TinyMCE iframe).
- **`getByRole` excludes 0x0 elements as hidden** — the MUI DataGrid pagination
  combobox needs `{ includeHidden: true }`.
- **MUI `SvgIcon` a11y name**: use `titleAccess="..."`, not bare `aria-label`
  (which makes MUI set `aria-hidden`). Query it with `getByRole("img", {name})`.
- **Modifier clicks**: `userEvent.click(el, { modifiers: ["Shift"] })`, never
  the keyboard-hold form (vitest #7007 drops the modifier flag).
- **Convert post-action network assertions to `expect.poll`** to avoid PUT/DELETE
  races; a few heavy suites are Firefox-CI-skipped (listed in the config).

## Workflow for a new / migrated spec

1. Read `REFERENCE.md` for the artifact templates and the API translation table.
2. Reuse the `*.story.tsx`; write the page object (locators + actions, no asserts).
3. Mock network in `mocks/` with MSW; register via `worker.use(...)` in `beforeEach`.
4. Iterate with `VITEST_BROWSERS=chromium`, then run all three before done.
5. Run `pnpm run tsc` and `pnpm run lint`; run the file 2-3x to catch flakes.
