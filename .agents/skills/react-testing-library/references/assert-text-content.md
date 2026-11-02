---
title: Use toHaveTextContent() for Text Assertions
impact: MEDIUM
impactDescription: prevents whitespace-related false failures
tags: assert, toHaveTextContent, text, matchers
---

## Use toHaveTextContent() for Text Assertions

Use `toHaveTextContent()` instead of accessing `textContent` directly. It handles whitespace normalization and provides clear errors.

**Incorrect (direct property access):**

```tsx
render(<Badge>  Premium User  </Badge>)

const badge = screen.getByRole('status')
expect(badge.textContent).toBe('Premium User')
// Fails due to whitespace differences
```

**Correct (jest-dom matcher):**

```tsx
render(<Badge>  Premium User  </Badge>)

const badge = screen.getByRole('status')
expect(badge).toHaveTextContent('Premium User')
// Normalizes whitespace automatically
```

**Use `toMatchTextContent()` for partial and regex matches:**

In Browser Mode, Vitest 5's `toHaveTextContent()` takes a string and matches the
whole text. jest-dom's `toHaveTextContent()` in jsdom still matches part of the
text. `src/__tests__/setup.ts` adds `toMatchTextContent()` to jsdom, so partial
and RegExp checks use the same matcher in both environments.

```tsx
// Partial match
expect(badge).toMatchTextContent('Premium')

// Regex match
expect(badge).toMatchTextContent(/premium/i)
```

**For asserting empty text:**

```tsx
expect(emptyElement).toHaveTextContent('')
// or
expect(emptyElement).toBeEmptyDOMElement()
```

Reference: [jest-dom - toHaveTextContent](https://github.com/testing-library/jest-dom#tohavetextcontent)
