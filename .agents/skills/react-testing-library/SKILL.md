---
name: react-testing-library
description: Write, review, or refactor RSpace React Testing Library tests. Use for `*.test.tsx` files, query choices, user interactions, async behavior, accessibility, and test structure.
---

# RSpace React Testing Library tests

These rules apply to `*.test.tsx` files, which run with Vitest and jsdom. Use
the `rspace-browser-tests` skill for `*.spec.tsx` Browser Mode tests.

Read the relevant rule files in `references/` for explanations and examples.
The RSpace-specific differences from the upstream RTL guide are summarized
below and detailed in [`references/rspace-overrides.md`](references/rspace-overrides.md).

## RSpace rules

- Keep the `cleanup()` call in `src/__tests__/setup.ts`. Vitest does not enable
  global hooks here, so do not add another cleanup call to a test file. This
  replaces [`anti-manual-cleanup`](references/anti-manual-cleanup.md).
- Match i18n keys in queries. The setup runs i18next in `cimode` with
  `appendNamespaceToCIMode`, so visible names use keys such as
  `common:actions.import`, not translated English text.
- Import `render` and `within` directly from `@testing-library/react`. Put
  providers in the JSX passed to `render`; do not create a shared render module.
  Use `renderWithRealI18n` from `@/__tests__/helpers/realI18n` only when a test
  needs real English.
- Use `wrapper` with `renderHook`, or when providers must survive `rerender()`.
- Mock HTTP with MSW through `@/__tests__/mswServer`. Unhandled requests fail
  the test. Do not add `axios-mock-adapter` to new tests, but leave existing
  uses alone.
- Use `vi.mock()` only for non-network modules. Store mocked methods in a local
  before calling `vi.mocked()`, and return stable values from mocks.
- Use `findTableCell` or `getIndexOfTableCell` from
  `@/__tests__/tableQueries`, plus `expectAccessible` and `toBeAccessible` from
  `@/__tests__/accessibility` when needed. Use `silenceConsole()` for expected
  console errors and `stubAppChrome` from `@/__tests__/helpers/appChrome` when a
  page requests the app shell.
- Do not add snapshot tests. This repository has no `.snap` files.
- Browser Mode imports `userEvent` from `vitest/browser`, which has no
  `setup()` method. Its `*.spec.tsx` rules live in `rspace-browser-tests`.

## General rules

Apply the reference rules for the following areas unless an RSpace rule above
changes them:

### Queries

- [`query-prefer-role`](references/query-prefer-role.md)
- [`query-avoid-testid`](references/query-avoid-testid.md)
- [`query-use-screen`](references/query-use-screen.md)
- [`query-label-text-forms`](references/query-label-text-forms.md)
- [`query-role-name-option`](references/query-role-name-option.md)
- [`query-get-vs-query`](references/query-get-vs-query.md)
- [`query-within-scope`](references/query-within-scope.md)

### Async handling

- [`async-findby-over-waitfor`](references/async-findby-over-waitfor.md)
- [`async-await-findby`](references/async-await-findby.md)
- [`async-single-assertion-waitfor`](references/async-single-assertion-waitfor.md)
- [`async-no-side-effects-waitfor`](references/async-no-side-effects-waitfor.md)
- [`async-waitfor-disappear`](references/async-waitfor-disappear.md)

### Anti-patterns

- [`anti-unnecessary-act`](references/anti-unnecessary-act.md)
- [`anti-implementation-details`](references/anti-implementation-details.md)
- [`anti-empty-waitfor`](references/anti-empty-waitfor.md)
- [`anti-container-queries`](references/anti-container-queries.md)
- [`anti-redundant-roles`](references/anti-redundant-roles.md)

Keep `anti-container-queries`, but do not add the snapshot example from that
reference. RSpace has no snapshot files.

### User interaction

- [`user-prefer-userevent`](references/user-prefer-userevent.md)
- [`user-setup-before-render`](references/user-setup-before-render.md)
- [`user-await-interactions`](references/user-await-interactions.md)
- [`user-keyboard-for-special-keys`](references/user-keyboard-for-special-keys.md)
- [`user-clear-before-type`](references/user-clear-before-type.md)

The setup and fake-timer rules apply to jsdom tests only. Browser Mode uses the
separate `rspace-browser-tests` skill.

### Assertions

- [`assert-jest-dom-matchers`](references/assert-jest-dom-matchers.md)
- [`assert-visible-over-in-document`](references/assert-visible-over-in-document.md)
- [`assert-text-content`](references/assert-text-content.md)
- [`assert-have-value`](references/assert-have-value.md)
- [`assert-accessible-description`](references/assert-accessible-description.md)

### Component setup

- [`setup-wrapper-providers`](references/setup-wrapper-providers.md), except
  its shared-wrapper guidance, which the RSpace rule above replaces
- [`setup-mock-modules`](references/setup-mock-modules.md)
- [`setup-fake-timers`](references/setup-fake-timers.md)
- [`setup-render-hook`](references/setup-render-hook.md)

Do not use [`setup-custom-render`](references/setup-custom-render.md). RSpace
allows only `renderWithRealI18n` for tests that need real English.

### Test structure

- [`struct-arrange-act-assert`](references/struct-arrange-act-assert.md)
- [`struct-one-behavior-per-test`](references/struct-one-behavior-per-test.md)
- [`struct-descriptive-names`](references/struct-descriptive-names.md)
- [`struct-avoid-beforeeach-render`](references/struct-avoid-beforeeach-render.md)

### Debugging

- [`debug-screen-debug`](references/debug-screen-debug.md)
- [`debug-logroles`](references/debug-logroles.md)
- [`debug-testing-playground`](references/debug-testing-playground.md)

### Accessibility

- [`a11y-role-queries-verify`](references/a11y-role-queries-verify.md)
- [`a11y-verify-focus`](references/a11y-verify-focus.md)
- [`a11y-test-aria-states`](references/a11y-test-aria-states.md)
