---
title: RSpace Overrides (read this file first)
impact: CRITICAL
impactDescription: four upstream rules are not correct for this repository
tags: rspace, overrides, local-conventions
---

# RSpace overrides

These rules override upstream RTL examples for jsdom `*.test.tsx` tests.
See root `AGENTS.md` and `src/main/webapp/ui/src/__tests__/setup.ts`.

- Keep shared `cleanup()` in `src/__tests__/setup.ts`. Vitest globals are off,
  so RTL automatic cleanup does not run. Do not add cleanup to individual tests.
- Query i18n keys such as `common:actions.import`. Setup enables `cimode` and
  `appendNamespaceToCIMode`. Use `renderWithRealI18n` from
  `@/__tests__/helpers/realI18n` only when real English is needed.
- Import `render` and `within` directly from `@testing-library/react`; put
  providers in rendered JSX. Do not create a shared render module. Use `wrapper`
  for `renderHook` or providers that must survive `rerender()`.
- Mock HTTP with `server` from `@/__tests__/mswServer`. Unhandled requests fail
  tests. Leave existing `axios-mock-adapter` uses alone; do not add new ones.
- Reserve `vi.mock()` for non-network modules. Assign methods to locals before
  `vi.mocked()` to avoid unbound-method lint errors. Return stable mock values
  to avoid render loops.
- Reject container queries and snapshot tests. Use role queries, `within`, or
  table helpers. The upstream `asFragment().toMatchSnapshot()` example does not apply.
- Follow `AGENTS.md` accessibility requirements regardless of upstream priority.

Reuse local helpers:

| Need | Helper |
| --- | --- |
| Tables | `findTableCell`, `getIndexOfTableCell` from `@/__tests__/tableQueries` |
| Accessibility | `expectAccessible`, `toBeAccessible` from `@/__tests__/accessibility` |
| Expected console errors | `silenceConsole()` |
| Pages requesting app chrome | `stubAppChrome` from `@/__tests__/helpers/appChrome` |

For Browser Mode `*.spec.tsx`, follow `rspace-browser-tests` instead.
