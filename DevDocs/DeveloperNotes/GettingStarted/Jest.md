# Jest

We use [Jest](https://jestjs.io/) to perform unit testing on the front-end
JavaScript codebase, primarily for Inventory. Before commiting to main, it
is best to ensure that all of the tests continue to pass.

## Running

To run on unix machines, perform the following command from the
/src/main/webapp/ui directory: `npm run test`. On windows machines, run `npm
run testw` from the same directory.

A regex can then be appended to the command to run just the tests whose file
name match the regex. E.g. `npm run test ContainerModel` will run all the
ContainerModel tests.

### Other useful arguments

Appending the argument `--verbose` outputs a detailed listing of each test. To
run this, you have to add `--` first to ensure the arg gets passed to jest and
not to npm. E.g. `npm run test -- --verbose ContainerModel`

The argument `-o` only runs the tests that jest thinks could have been impacted
by the changes that are being tracked by git. This drastically reduces the time
it takes to run the tests before committing.

The argument `--color` will always output with colours, even if the output is
not a tty. For example, when piping into less use `--color` and `-R`
```
npm run test -- --color | less -R
```

## Writing

New tests should be written to a `__tests__` directory adjacent to the source
code file in a script with extension ".test.js". Included below is a template
to start off writing new react tests.

```
/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";

beforeEach(() => {
  jest.clearAllMocks();
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

Be sure to also run eslint and flow over any new or modified tests.

### Using fireEvent

Instead of doing `screen.getByRole("button").click();` it can be better to do
`fireEvent.click(screen.getByRole("button"));` for the simple reason that the
latter does not need to be wrapped in a call to `act`. However, there are times
where only the former, wrapper in `act`, will work.

```
fireEvent.click(screen.getByRole("button"));
// instead of
act(() => {
  screen.getByRole("button").click();
});
```

Further things to watch out for is that the method of `fireEvent` that should
be used is at times unintuitive. For most uses `fireEvent.click` is correct,
but not for `select` menus, where `fireEvent.mouseDown` is required to trigger
the opening of the menu. Similarly, whilst `fireEvent.change` is the correct
call to make for edits to text fields and the like (see code below), for
numerical fields (spinbuttons) `fireEvent.input` is instead required.

```
// for buttons, and most other interactive elements
fireEvent.click(screen.getByRole("button"));

// for choice fields
fireEvent.mouseDown(screen.getByRole("Choose"));

// for text fields
fireEvent.change(screen.getByRole("textbox"), { target: { value: "new value" }});

// for numberical fields
fireEvent.input(screen.getByRole("spinbutton", { target: { value: 4 }});
```

### Debugging issues with `act`

`act` is used to ensure that react re-renders after some user interaction has
been simulated. Most APIs provided by testing-library/react, such as fireEvent
as mentioned above, call act automatically and so don't need to be wrapped.
There are times when it is necessary, and there will be a console error
reported by the test when it appears that one is necessary but not provided;
usually because of some asynchronous action. To resolve this, the simplest
solution is often to use a call with `waitFor` to have the test wait on the
result of the asynchronous action, although this assumes that the action will
make some change to the UI that can be awaited. It is usually the case that
such console errors don't make a material difference the test, though it is
possible that assertions could be checking the UI before it has had a chance to
re-render so are best resolved where possible.

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

We also maintain our own [Flow type definitions for this library] as the
library only supports TypeScript and there were no sufficient third-party ones.
Because the library was built with functional programming principles in mind,
the type definitions are actually pretty straight forward if you're comfortable
with type variables. The flow types are not comprenhensive, however, and
writing new property tests may require exetending the definitions there. The
[API-reference] is of particular help in getting this right.

[Fast-check]: https://fast-check.dev/
[Arbitraries]: https://fast-check.dev/docs/core-blocks/arbitraries/
[API-reference]: https://fast-check.dev/api-reference/index.html
[Flow type definitions for this library]: ../../../src/main/webapp/ui/flow-typed/fast-check.js

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
git bisect run bash -c 'cd src/main/webapp/ui; npm run test <failing-test>'
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

