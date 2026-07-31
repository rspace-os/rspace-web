---
title: Mock Modules at Module Level
impact: MEDIUM
impactDescription: prevents intermittent mock timing failures
tags: setup, mock, vitest, modules
---

## Mock Modules at Module Level

Call `vi.mock()` at the top level of your test file, not inside tests. Vitest hoists mock calls, but placing them inside tests can cause timing issues.

**Incorrect (mock inside test):**

```tsx
test('fetches user data', async () => {
  vi.mock('./api', () => ({
    fetchUser: vi.fn().mockResolvedValue({ name: 'John' })
  }))

  render(<UserProfile />)
  // Mock may not be applied correctly
})
```

**Correct (mock at module level):**

```tsx
import { expect, test, vi } from 'vitest'
import { fetchUser } from './api'

vi.mock('./api')

const mockFetchUser = vi.mocked(fetchUser)

test('fetches user data', async () => {
  mockFetchUser.mockResolvedValue({ name: 'John' })

  render(<UserProfile />)
  expect(await screen.findByText('John')).toBeInTheDocument()
})

test('handles error', async () => {
  mockFetchUser.mockRejectedValue(new Error('Network error'))

  render(<UserProfile />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Network error')
})
```

Because `vi.mock()` is hoisted above the imports, a factory cannot reference
top-level variables. Use `vi.hoisted()` when the factory needs one:

```tsx
const { mockFetchUser } = vi.hoisted(() => ({ mockFetchUser: vi.fn() }))

vi.mock('./api', () => ({ fetchUser: mockFetchUser }))
```

**Reset mocks between tests:**

```tsx
beforeEach(() => {
  vi.clearAllMocks()
})
```

Reference: [Vitest - vi.mock](https://vitest.dev/api/vi.html#vi-mock)
