---
name: react-testing-library
description: Write, review, or refactor RSpace React Testing Library tests. Use for `*.test.tsx` files, query choices, user interactions, async behavior, accessibility, and test structure.
---

# RSpace React Testing Library tests

Use for Vitest/jsdom `*.test.tsx`. For Browser Mode `*.spec.tsx`, use
`rspace-browser-tests`; its `userEvent` comes from `vitest/browser` and has no
`setup()`.

Read [RSpace overrides](references/rspace-overrides.md) first. They take
precedence over the upstream examples in `references/`.

## Rules

- Prefer `screen` queries by role and accessible name, then labels/text.
  Scope with `within`. Use `getBy` for presence, `queryBy` for absence, and
  awaited `findBy` for asynchronous appearance. Avoid test IDs and DOM selectors.
- Create `userEvent.setup()` before rendering; await interactions.
  Clear before typing replacements; use `keyboard()` for special keys.
- Prefer `findBy` over `waitFor` for appearance. Keep actions outside `waitFor`
  and one assertion inside it. Never use empty callbacks or unnecessary `act`.
- Use semantic jest-dom matchers for visibility, values, text, and accessible
  descriptions. Test focus and ARIA states; role queries alone do not establish
  accessibility. Do not add redundant roles or snapshot tests.
- Test one behavior per test with descriptive names and arrange/act/assert
  structure. Render within tests; avoid testing implementation details.

Read matching reference files for examples:

| Topic | Files in `references/` |
| --- | --- |
| Queries | `query-*.md` |
| Async behavior | `async-*.md` |
| Interactions | `user-*.md` |
| Assertions | `assert-*.md` |
| Providers, mocks, hooks, timers | `setup-*.md` |
| Structure | `struct-*.md` |
| Accessibility | `a11y-*.md` |
| Debugging | `debug-*.md` for `screen.debug`, `logRoles`, and Testing Playground |
| Anti-patterns | `anti-*.md` |

The overrides replace `anti-manual-cleanup`, `setup-custom-render`, and shared
wrapper guidance in `setup-wrapper-providers`. Ignore the snapshot example in
`anti-container-queries`. Setup and fake-timer rules apply only to jsdom.
