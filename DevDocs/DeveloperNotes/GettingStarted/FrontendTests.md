# Frontend unit tests

We use [Vitest](https://vitest.dev/) for frontend unit tests. Before committing
to main, ensure that the relevant tests pass.

## Running

To run the tests, perform the following command from the repo root:
`pnpm run test`. The `cross-env` wrapper means the same command works on
Windows, macOS, and Linux.

A file-name filter can be appended to run only matching tests. For example,
`pnpm run test ContainerModel` runs the ContainerModel tests.

### Other useful arguments

Appending `--verbose` outputs a detailed listing of each test. pnpm passes
arguments directly to the script, so do not add a standalone `--`; unlike npm,
pnpm forwards it as a literal argument instead of stripping it, and Vitest
treats it as the end of its options, silently discarding a following test
filter. For example: `pnpm run test --verbose ContainerModel`.

The argument `-o` only runs the tests that Vitest thinks could have been impacted
by the changes that are being tracked by git. This drastically reduces the time
it takes to run the tests before committing.

The argument `--color` will always output with colours, even if the output is
not a tty. For example, when piping into less use `--color` and `-R`
```
pnpm run test --color | less -R
```

## Writing

New tests should be written to a `__tests__` directory adjacent to the source
code file in a script with extension ".test.js". Included below is a template
to start off writing new react tests.

```ts
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

describe("<filename>", () => {

  describe("test suite", () => {

    test("individual test", () => {

      expect(true).toBe(true);

    });

  });

});
```

Be sure to also run Biome (`pnpm run lint`) and type checking over any new or modified tests.

### User interactions

Use `userEvent` for every user interaction so tests exercise the same event
sequence as a browser user. Create one instance per test and await each call.

```ts
import userEvent from "@testing-library/user-event";
import { screen } from "@testing-library/react";

const user = userEvent.setup();
await user.click(screen.getByRole("button"));
```

### Debugging issues with `act`

`act` is used to ensure that react re-renders after some user interaction has
been simulated. `userEvent` calls `act` automatically, so interactions normally
do not need to be wrapped manually.

An `act` warning usually points to asynchronous work that the test has not
awaited. Await the `userEvent` call and then use `findBy*` queries or `waitFor`
to observe the resulting UI state before making assertions.

### Using fast-check

[Fast-check] is a library for doing property and model based testing, where
random values are generated and are used as inputs to the test. This stands
in contrast to regular unit testing that is example-based. Property-based
testing has the advantage that it asserts a stronger guarantee that the code is
correct, but has the disadvantage that the test code can be more complex and
the tests can take longer to run (especially those that render react code).

For examples of such tests, search the code base for `import fc from "fast-check"`

Here are some useful links for working with fast-check tests:
* [Arbitraries]: documentation on all the built-in functions that generate
  random values from a specified set e.g. an arbitrary string, or an arbitrary
  number less than 100.
* [API-reference]: comprehensive documentation of the entire API that the
  library exposes. There are lots of useful arguments to the various functions
  that are documented here.

[Fast-check]: https://fast-check.dev/
[Arbitraries]: https://fast-check.dev/docs/core-blocks/arbitraries/
[API-reference]: https://fast-check.dev/api-reference/index.html

## Debugging with git bisect

When a test is failing it can be useful to identify the commit whose changes
caused the test to begin failing. This can efficiently be done using git
bisect, which performs a binary search over a chain of commits; useful in all
sorts of circumstances -- it is definitely worth taking the time to read the
manual for git bisect (`man git-bisect`). Specifically here, git bisect has the
functionality to have a script tell it which commits come before or after the
bad change based on the exit code of the passed command; see the manual's
section on "Bisect run".

First, identify a regex that covers the failing test(s); best to be specific as
possible to minimise the time spent running the tests. Let's call it
<failing-test>.

Second, identify some commit in the past where the test was known to pass;
doesn't need to be too specific as the each additional commit in the chain only
adds time logarithmically. Let's call that <good-commit>.

Finally, run the following commands from the root of the repo.
```
git bisect start
git bisect bad
git bisect good <good-commit>
git bisect run pnpm run test <failing-test>
```

As an aside, if this process identifies a squashed commit from a PR, GitHub
keeps references to the HEAD of all branches that were used in a PR, which can
be browsed with the command `git ls-remote`. To further identify the commit
within a PR that began to cause the tests to fail (or any git bisect
investigation), run the following command to fetch the HEAD of squashed branch:
```
git fetch origin refs/pull/<PR-number>/head:<local-name>
```
e.g.
```
git fetch origin refs/pull/500/head:origin/refs/pull/500/head
git checkout origin/refs/pull/500/head
```
will leave HEAD pointing to the HEAD of the branch as it was before squashing
and merging. You can now perform the same bisect steps as above, with the known
good commit being the merge base of HEAD and the branch into which the PR was
merged (this can either be found manually or using `git merge-base`).
