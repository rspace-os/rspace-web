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

The shared types come from Vitest 5's browser matchers, where `toHaveTextContent()`
takes a string and, in Browser Mode, means the whole text (jest-dom in jsdom still
matches a string partially). `src/__tests__/setup.ts` registers
`toMatchTextContent()` for jsdom with the same partial/RegExp meaning.

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
