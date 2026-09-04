---
name: rspace-browser-tests
description: Write, run, and debug RSpace component tests in Vitest Browser Mode with real browsers, Playwright, MSW, and page objects. Use for `*.spec.tsx` tests and cross-engine failures. Do not use for jsdom tests or backend Java tests.
---

# RSpace Browser Mode tests

Use Vitest Browser Mode with `@vitest/browser-playwright`, MSW, and page objects.
Read [REFERENCE.md](REFERENCE.md) before writing specs for templates, browser
pitfalls, and Playwright-CT migration APIs. The old `playwright-ct` setup is gone.

## Files and structure

- `*.spec.tsx` runs under `vitest.browser.config.ts`, which includes
  `**/?*.spec.{ts,tsx}`. `*.test.tsx` uses jsdom under `vite.config.ts`;
  `*.e2e.*` is end-to-end Playwright. The runners do not interchange suffixes.
- Co-locate specs with components or in sibling `__tests__/` directories.
- Reuse the existing `*.story.tsx` and its providers.

| File | Responsibility |
| --- | --- |
| `*.spec.tsx` | `render(<Story/>)`, `worker.use(...)` in `beforeEach`, `cleanup()` in `afterEach`, assertions with `expect.element` / `expect.poll` |
| `pageObjects/ComponentPage.ts` | Retriable locator getters without `await`; async actions; no assertions |
| `mocks/componentMocks.ts` | MSW handlers and fixture factories; re-export shared ones |
| `*.story.tsx` | Component and providers |

Prefer role, label, and text locators. Use the reference's local `css=` cast
helper only for third-party components without accessible handles; explain why
semantic queries fail. Captured DOM nodes go stale on rerender.

## Shared infrastructure

Paths below are relative to `src/main/webapp/ui/`.

- `src/__tests__/browserSetup.ts`: shared `worker`, start/reset lifecycle,
  CDP media reset, `suppressFireAndForget404(...)`.
- `src/__tests__/mswAppShellHandlers.ts`: default whoami, navigation, analytics,
  and livechat handlers; survive `resetHandlers()`.
- `src/__tests__/mocks/inventoryMocks.ts`: `oauthTokenHandler`, `OAUTH_TOKEN`.
  `src/__tests__/mocks/galleryMocks.ts`: `galleryAppShellHandlers`.
  Opt in with `worker.use(...)`.
- `src/__tests__/pageObjects/accessibility.ts`: `expectNoAxeViolations()`,
  `emulateHighContrast()`, `emulateForcedColors()`. Emulation is Chromium-only.
- `src/__tests__/pageObjects/viewport.ts`: `isFullyInViewport`,
  `moveToastStackIntoViewport`, `clickWhenInViewport`.

The MSW worker is origin-global. Never call `worker.stop()` between files;
it breaks later interception. Keep `fileParallelism: false` and `retry: 2`.

## Run and verify

Run from the repository root:

```bash
pnpm exec playwright install chromium firefox webkit  # first run
VITEST_BROWSERS=chromium pnpm run test-browser <file>   # iterate
pnpm run test-browser <file>                          # all three engines
pnpm run test-browser                                # whole suite
pnpm run test-browser:watch
pnpm run tsc
pnpm run lint
```

Before finishing, run the changed file in all three engines and repeat it
2–3 times to check for flakes. CI's `browser-tests` job in
`.github/workflows/lint-and-test.yml` installs browsers with `--with-deps`,
sets `VITEST_BROWSERS` per matrix job, and writes per-engine JUnit results.
Some heavy Firefox suites are skipped in the config.

## Common pitfalls

- `userEvent` comes from `vitest/browser`; it has no `setup()`.
- Role queries hide 0×0 elements. DataGrid pagination needs `includeHidden: true`.
- Give MUI `SvgIcon` a `titleAccess`, then query role `img` by name.
  Bare `aria-label` leaves it `aria-hidden`.
- Give MUI `Select` inside `FormField` an accessible name through
  `SelectDisplayProps`; query by role and name as required by `AGENTS.md`.
- Modifier clicks need `userEvent.click(el, { modifiers: ["Shift"] })`;
  keyboard-hold syntax loses the modifier flag.
- Use `expect.poll` for post-action network assertions to avoid PUT/DELETE races.
