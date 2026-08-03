---
title: RSpace Overrides (read this file first)
impact: CRITICAL
impactDescription: four upstream rules are not correct for this repository
tags: rspace, overrides, local-conventions
---

## RSpace Overrides

This skill comes from an external repository. It describes a general RTL setup.
Some of its rules are not correct here. If a rule in this file disagrees with an
upstream rule, obey this file.

These two files give the correct data:

- `CLAUDE.md` in the root directory
- `src/main/webapp/ui/src/__tests__/setup.ts`

### 1. Keep the cleanup call

Do not obey [`anti-manual-cleanup`](anti-manual-cleanup.md).

The vitest configuration in `src/main/webapp/ui/vite.config.ts` does not set
`globals: true`. Therefore `afterEach` is not a global function, and RTL does not
start its automatic cleanup.

The `cleanup()` call in `src/__tests__/setup.ts` is necessary. If you remove it,
each test keeps its components in the DOM. A query can then find an element from
an earlier test.

Do not add a `cleanup()` call to a test file. The setup file does this one time
for all tests.

### 2. Use i18n keys in queries

The upstream examples use English text in queries. English text matches no
element here.

`src/__tests__/setup.ts` starts i18next in `cimode` mode with
`appendNamespaceToCIMode`. Therefore the screen shows the namespace and the key.

Correct:

```tsx
await user.click(screen.getByRole("button", { name: "common:actions.import" }));
expect(screen.getByText("common:tags.addTag")).toBeVisible();
```

Wrong. This query matches no element in `cimode` mode:

```tsx
await user.click(screen.getByRole("button", { name: /import/i }));
```

You can use a regular expression for a part of a key, for example
`name: /common:actions.delete/`.

If a test must use real English text, use `renderWithRealI18n` from
`@/__tests__/helpers/realI18n`. This function is the only approved custom render
function.

### 3. Put the providers in the JSX

Do not obey [`setup-custom-render`](setup-custom-render.md). Do not obey the
shared-wrapper part of [`setup-wrapper-providers`](setup-wrapper-providers.md).

`CLAUDE.md` gives this rule: import `render` and `within` from
`@testing-library/react`. Do not make a `test-utils` module that exports these
functions again. Do not replace `render` with a different function.

Put the providers in the JSX that you give to `render`:

```tsx
import { render, screen } from "@testing-library/react";

render(
  <ThemeProvider theme={materialTheme}>
    <ScopeField getDMPs={getDMPs} />
  </ThemeProvider>,
);
```

You can use the `wrapper` option for `renderHook`. You can also use it if the
providers must stay after a `rerender()` call.

### 4. Mock the network

Do not use `vi.mock()` for an HTTP request. Mock each HTTP request with MSW.

`src/__tests__/mswServer.ts` starts the server with
`onUnhandledRequest: "error"`. Therefore a request without a handler makes the
test fail. The test does not stop and wait.

```tsx
import { http, HttpResponse } from "msw";
import { server } from "@/__tests__/mswServer";

server.use(http.get("/api/v1/stoichiometry", () => HttpResponse.json(mockResponse)));
```

Approximately 50 test files use `axios-mock-adapter`. Do not change these files.
Do not use `axios-mock-adapter` in a new test.

Use `vi.mock()` only for modules that are not the network, for example:

- browser APIs
- legacy global functions
- large components from an external package

Obey these two rules when you use `vi.mock()`:

- Put `obj.method` in a local variable before you call `vi.mocked()`. If you do
  not do this, the unbound-method lint rule gives an error.
- Give each mock a stable return value. A shared mock that makes a new function
  for each render can cause a loop of renders.

### 5. Container queries: keep the rule

Keep [`anti-container-queries`](anti-container-queries.md). Continue to reject
`container.querySelector` in a new test.

One example in that rule is not correct here. It shows
`expect(asFragment()).toMatchSnapshot()`. This repository has no `.snap` files.
Do not add a snapshot test.

Use one of these in place of a container query:

- a role query
- `within()`
- `findTableCell`

### Local helper functions

The upstream rules do not include these helper functions:

- `findTableCell` and `getIndexOfTableCell` from `@/__tests__/tableQueries`
- `expectAccessible` from `@/__tests__/accessibility`, and the `toBeAccessible`
  matcher
- `silenceConsole()` for expected console errors
- `stubAppChrome` from `@/__tests__/helpers/appChrome` for pages that request the
  app shell

`CLAUDE.md` makes accessibility assertions necessary. The upstream skill gives
them a LOW priority. Obey `CLAUDE.md`.

### Where these rules apply

These rules and the upstream rules apply to `*.test.tsx` files. These files use
vitest with jsdom.

Browser Mode `*.spec.tsx` files import `userEvent` from `vitest/browser`. This
`userEvent` has no `setup()` function. Therefore
[`user-setup-before-render`](user-setup-before-render.md) and
[`setup-fake-timers`](setup-fake-timers.md) do not apply to these files. Use the
`rspace-browser-tests` skill for these files.
